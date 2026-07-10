package dev.amissouri.hcg;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.papermc.paper.math.Position;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class LavaRaiseManager {

    public enum Phase { IDLE, ARMED, RISING, HOLDING, DRAINING }

    private record Job(UUID player, int chunkX, int chunkZ, int fromY, int toY, boolean render) {}

    private final HCGPlugin plugin;
    private final BlockData lavaData = Material.LAVA.createBlockData();
    private BukkitTask task;
    private Phase phase = Phase.IDLE;

    private World world;
    private int minX;
    private int maxX;
    private int minZ;
    private int maxZ;
    private int minY;
    private int topY;

    private long tickCounter;
    private long phaseStartTick;
    private double ticksPerLayer;
    private int currentLevel;
    private int drainStartLevel;
    private boolean fastDrain;
    private boolean pendingDrain;
    private int burnedUpTo;
    private long prevDayTime = -1;
    private LavaBurnTracker burnTracker;

    private final Map<UUID, Map<Long, Integer>> rendered = new HashMap<>();
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();

    private boolean purging;
    private int purgeTopY;
    private int purgeX;
    private int purgeZ;
    private int purgeY;
    private int purgeRemoved;

    public LavaRaiseManager(HCGPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getConfig().getBoolean("lava-raise.enabled", false)) {
            enable();
        }
    }

    public boolean isEnabled() {
        return phase != Phase.IDLE;
    }

    public Phase phase() {
        return phase;
    }

    public boolean isActive() {
        return phase == Phase.RISING || phase == Phase.HOLDING || phase == Phase.DRAINING;
    }

    public boolean isPurging() {
        return purging;
    }

    public int startTime() {
        return plugin.getConfig().getInt("lava-raise.start-time", 0);
    }

    public int endTime() {
        return plugin.getConfig().getInt("lava-raise.end-time", 12000);
    }

    public int riseDurationSeconds() {
        return plugin.getConfig().getInt("lava-raise.rise-duration-seconds", 300);
    }

    public int maxY() {
        return plugin.getConfig().getInt("lava-raise.max-y", 62);
    }

    public boolean replaceWater() {
        return plugin.getConfig().getBoolean("lava-raise.replace-water", false);
    }

    public boolean burnPlacedBlocks() {
        return plugin.getConfig().getBoolean("lava-raise.burn-placed-blocks", true);
    }

    public boolean damageMobs() {
        return plugin.getConfig().getBoolean("lava-raise.damage-mobs", false);
    }

    public LavaBurnTracker burnTracker() {
        if (burnTracker == null) {
            burnTracker = new LavaBurnTracker(plugin, this);
        }
        return burnTracker;
    }

    public boolean isBlockInLava(Block block) {
        return isActive() && currentLevel >= minY
                && block.getWorld().equals(world)
                && block.getY() <= currentLevel
                && inRegionXZ(block.getX(), block.getZ());
    }

    public boolean inRegionXZ(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private int positionsPerTick() {
        return Math.max(2000, plugin.getConfig().getInt("lava-raise.blocks-per-tick", 40000));
    }

    private int maxRegionSize() {
        return Math.max(16, plugin.getConfig().getInt("lava-raise.max-region", 512));
    }

    private int renderRadius() {
        // Default covers everything the client can see; the config can lower it.
        int configured = Math.max(2, plugin.getConfig().getInt("lava-raise.render-radius", 32));
        return Math.min(configured, world != null ? world.getViewDistance() : Bukkit.getViewDistance());
    }

    public void set(String key, Object value) {
        plugin.getConfig().set("lava-raise." + key, value);
        plugin.saveConfig();
    }

    public World eventWorld() {
        return Bukkit.getWorlds().get(0);
    }

    public void enable() {
        set("enabled", true);
        if (phase == Phase.IDLE) {
            phase = Phase.ARMED;
            prevDayTime = -1;
            ensureTask();
        }
    }

    public void disable() {
        set("enabled", false);
        if (phase == Phase.RISING || phase == Phase.HOLDING) {
            startDrain(true);
        } else if (phase == Phase.ARMED) {
            phase = Phase.IDLE;
            stopTaskIfIdle();
        }
    }

    public void cancelEvent() {
        if (phase == Phase.RISING || phase == Phase.HOLDING) {
            startDrain(true);
        }
    }

    public boolean startPurge(int upToY) {
        if (purging || isActive()) {
            return false;
        }
        world = eventWorld();
        snapshotRegion();
        purging = true;
        purgeTopY = upToY;
        purgeX = minX;
        purgeZ = minZ;
        purgeY = world.getMinHeight();
        purgeRemoved = 0;
        ensureTask();
        return true;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (world != null) {
            rendered.values().stream()
                    .flatMap(m -> m.keySet().stream())
                    .distinct()
                    .forEach(key -> world.refreshChunk((int) (key >> 32), (int) key.longValue()));
        }
        rendered.clear();
        jobs.clear();
        phase = Phase.IDLE;
        purging = false;
        if (burnTracker != null) {
            burnTracker.save();
        }
    }

    private void ensureTask() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void stopTaskIfIdle() {
        if (phase == Phase.IDLE && !purging && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        tickCounter++;

        if (phase != Phase.IDLE) {
            World w = phase == Phase.ARMED ? eventWorld() : world;
            long dayTime = w.getTime();
            if (prevDayTime >= 0 && dayTime != prevDayTime) {
                if (phase == Phase.ARMED && crossed(startTime(), prevDayTime, dayTime)) {
                    startRise();
                } else if (phase == Phase.RISING && crossed(endTime(), prevDayTime, dayTime)) {
                    pendingDrain = true;
                } else if (phase == Phase.HOLDING && crossed(endTime(), prevDayTime, dayTime)) {
                    startDrain(false);
                }
            }
            prevDayTime = dayTime;
        }

        if (isActive()) {
            updateLevel();
            if (burnPlacedBlocks() && currentLevel > burnedUpTo
                    && (phase == Phase.RISING || phase == Phase.HOLDING)) {
                burnTracker().burnRange(Math.max(minY, burnedUpTo + 1), currentLevel);
                burnedUpTo = currentLevel;
            }
            if (tickCounter % 10 == 0) {
                syncPlayers();
            }
            processJobs();
            if (tickCounter % 10 == 5) {
                burnPlayers();
            }
            if (phase == Phase.DRAINING && currentLevel < minY
                    && jobs.isEmpty() && rendered.values().stream().allMatch(Map::isEmpty)) {
                finishDrain();
            }
        }

        if (purging) {
            processPurge();
        }
    }

    static boolean crossed(int target, long prev, long now) {
        if (now >= prev) {
            return target > prev && target <= now;
        }
        return target > prev || target <= now;
    }

    public int currentLavaY() {
        return Math.max(minY, currentLevel);
    }

    private void updateLevel() {
        switch (phase) {
            case RISING -> {
                int level = minY + (int) ((tickCounter - phaseStartTick) / ticksPerLayer);
                if (level >= topY) {
                    level = topY;
                    currentLevel = level;
                    if (pendingDrain) {
                        startDrain(false);
                    } else {
                        phase = Phase.HOLDING;
                        Messages.broadcastOps("lavaraise.reached-broadcast", "y", String.valueOf(topY));
                    }
                    return;
                }
                currentLevel = level;
            }
            case HOLDING -> currentLevel = topY;
            case DRAINING -> {
                if (fastDrain) {
                    currentLevel = minY - 1;
                } else {
                    int level = drainStartLevel - (int) ((tickCounter - phaseStartTick) / ticksPerLayer);
                    currentLevel = Math.max(minY - 1, level);
                }
            }
            default -> { }
        }
    }

    private void snapshotRegion() {
        WorldBorder border = world.getWorldBorder();
        int half = (int) Math.min(border.getSize(), maxRegionSize()) / 2;
        Location center = border.getCenter();
        minX = center.getBlockX() - half;
        maxX = center.getBlockX() + half;
        minZ = center.getBlockZ() - half;
        maxZ = center.getBlockZ() + half;
        minY = world.getMinHeight() + 1;
        topY = Math.clamp(maxY(), minY + 1, world.getMaxHeight() - 1);
    }

    private void startRise() {
        world = eventWorld();
        snapshotRegion();
        ticksPerLayer = Math.max(0.05, riseDurationSeconds() * 20.0 / (topY - minY + 1));
        currentLevel = minY;
        burnedUpTo = minY - 1;
        phaseStartTick = tickCounter;
        rendered.clear();
        jobs.clear();
        pendingDrain = false;
        fastDrain = false;
        phase = Phase.RISING;
        Messages.broadcastOps("lavaraise.rising-broadcast", "y", String.valueOf(topY));
    }

    private void startDrain(boolean fast) {
        drainStartLevel = currentLevel;
        ticksPerLayer = Math.max(0.05,
                riseDurationSeconds() * 20.0 / Math.max(1, drainStartLevel - minY + 1));
        phaseStartTick = tickCounter;
        fastDrain = fast;
        pendingDrain = false;
        phase = Phase.DRAINING;
        Messages.broadcastOps("lavaraise.receding-broadcast");
    }

    private void finishDrain() {
        rendered.clear();
        jobs.clear();
        boolean stayEnabled = plugin.getConfig().getBoolean("lava-raise.enabled", false);
        phase = stayEnabled ? Phase.ARMED : Phase.IDLE;
        Messages.broadcastOps(stayEnabled ? "lavaraise.gone-repeat-broadcast" : "lavaraise.gone-broadcast");
        stopTaskIfIdle();
    }

    private void syncPlayers() {
        int radius = renderRadius();
        int forgetBeyond = world.getViewDistance() + 2;
        int chunkMinX = minX >> 4;
        int chunkMaxX = maxX >> 4;
        int chunkMinZ = minZ >> 4;
        int chunkMaxZ = maxZ >> 4;

        rendered.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);

        for (Player player : world.getPlayers()) {
            Map<Long, Integer> map = rendered.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            int pcx = player.getLocation().getBlockX() >> 4;
            int pcz = player.getLocation().getBlockZ() >> 4;

            map.keySet().removeIf(key -> {
                int cx = (int) (key >> 32);
                int cz = (int) key.longValue();
                return Math.abs(cx - pcx) > forgetBeyond || Math.abs(cz - pcz) > forgetBeyond;
            });

            if (currentLevel >= minY) {
                for (int cx = Math.max(chunkMinX, pcx - radius); cx <= Math.min(chunkMaxX, pcx + radius); cx++) {
                    for (int cz = Math.max(chunkMinZ, pcz - radius); cz <= Math.min(chunkMaxZ, pcz + radius); cz++) {
                        map.putIfAbsent(((long) cx << 32) | (cz & 0xFFFFFFFFL), minY - 1);
                    }
                }
            }

            var iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                long key = entry.getKey();
                int cx = (int) (key >> 32);
                int cz = (int) key;
                int renderedTo = entry.getValue();
                if (renderedTo < currentLevel) {
                    jobs.add(new Job(player.getUniqueId(), cx, cz, renderedTo + 1, currentLevel, true));
                    entry.setValue(currentLevel);
                } else if (renderedTo > currentLevel) {
                    jobs.add(new Job(player.getUniqueId(), cx, cz,
                            Math.max(minY, currentLevel + 1), renderedTo, false));
                    if (currentLevel < minY) {
                        iterator.remove();
                    } else {
                        entry.setValue(currentLevel);
                    }
                }
            }
        }
    }

    private void processJobs() {
        int layerBudget = Math.max(8, positionsPerTick() / 256) * (fastDrain ? 4 : 1);
        while (layerBudget > 0 && !jobs.isEmpty()) {
            Job job = jobs.poll();
            Player player = Bukkit.getPlayer(job.player());
            if (player == null || !world.isChunkLoaded(job.chunkX(), job.chunkZ())) {
                continue;
            }
            if (job.render()) {
                int y = job.fromY();
                while (y <= job.toY() && layerBudget > 0) {
                    sendLayer(player, job.chunkX(), job.chunkZ(), y, true);
                    y++;
                    layerBudget--;
                }
                if (y <= job.toY()) {
                    jobs.addFirst(new Job(job.player(), job.chunkX(), job.chunkZ(), y, job.toY(), true));
                    return;
                }
            } else {
                int y = job.toY();
                while (y >= job.fromY() && layerBudget > 0) {
                    sendLayer(player, job.chunkX(), job.chunkZ(), y, false);
                    y--;
                    layerBudget--;
                }
                if (y >= job.fromY()) {
                    jobs.addFirst(new Job(job.player(), job.chunkX(), job.chunkZ(), job.fromY(), y, false));
                    return;
                }
            }
        }
    }

    private void sendLayer(Player player, int chunkX, int chunkZ, int y, boolean render) {
        Map<Position, BlockData> changes = new HashMap<>();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        boolean water = replaceWater();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                if (x < minX || x > maxX || z < minZ || z > maxZ) {
                    continue;
                }
                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (type.isAir() || (water && type == Material.WATER)) {
                    changes.put(Position.block(x, y, z), render ? lavaData : block.getBlockData());
                }
            }
        }
        if (!changes.isEmpty()) {
            player.sendMultiBlockChange(changes);
        }
    }

    private void burnPlayers() {
        if (currentLevel < minY) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                continue;
            }
            if (inLava(player.getLocation()) && !player.isInvulnerable()) {
                player.setFireTicks(Math.max(player.getFireTicks(), 100));
                player.damage(4.0);
            }
        }
        if (damageMobs()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Player || entity instanceof ArmorStand || entity.isInvulnerable()) {
                    continue;
                }
                if (inLava(entity.getLocation())) {
                    entity.setFireTicks(Math.max(entity.getFireTicks(), 100));
                    entity.damage(4.0);
                }
            }
        }
    }

    private boolean inLava(Location loc) {
        if (loc.getBlockY() > currentLevel
                || loc.getBlockX() < minX || loc.getBlockX() > maxX
                || loc.getBlockZ() < minZ || loc.getBlockZ() > maxZ) {
            return false;
        }
        return replaceWater() || loc.getBlock().getType() != Material.WATER;
    }

    private void processPurge() {
        int budget = positionsPerTick() * 2;
        while (budget-- > 0) {
            if (world.isChunkLoaded(purgeX >> 4, purgeZ >> 4)) {
                Block block = world.getBlockAt(purgeX, purgeY, purgeZ);
                if (block.getType() == Material.LAVA) {
                    block.setType(Material.AIR, false);
                    purgeRemoved++;
                }
            }
            purgeZ++;
            if (purgeZ > maxZ) {
                purgeZ = minZ;
                purgeX++;
                if (purgeX > maxX) {
                    purgeX = minX;
                    purgeY++;
                    if (purgeY > purgeTopY) {
                        purging = false;
                        Messages.broadcastOps("lavaraise.purge-complete", "count", String.valueOf(purgeRemoved));
                        stopTaskIfIdle();
                        return;
                    }
                }
            }
        }
    }

    public static int parseTime(String input) {
        switch (input.toLowerCase()) {
            case "day", "morning" -> { return 1000; }
            case "noon" -> { return 6000; }
            case "sunset", "dusk" -> { return 12000; }
            case "night" -> { return 13000; }
            case "midnight" -> { return 18000; }
            case "sunrise", "dawn" -> { return 23000; }
            default -> { }
        }
        if (input.matches("\\d{1,2}:\\d{2}")) {
            String[] parts = input.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            if (hours > 23 || minutes > 59) {
                return -1;
            }
            return (((hours - 6 + 24) % 24) * 1000) + (minutes * 1000 / 60);
        }
        try {
            int ticks = Integer.parseInt(input);
            return ticks >= 0 && ticks < 24000 ? ticks : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String formatTime(int ticks) {
        int hours = (ticks / 1000 + 6) % 24;
        int minutes = (ticks % 1000) * 60 / 1000;
        return ticks + String.format(" (%02d:%02d)", hours, minutes);
    }
}
