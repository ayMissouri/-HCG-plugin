package dev.amissouri.hcg.npcs;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.amissouri.hcg.AsyncSaver;
import dev.amissouri.hcg.HcgScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class NpcManager {

    static final class Npc {
        final NpcData data;
        Object entity;
        int entityId = -1;
        final Set<UUID> viewers = new HashSet<>();
        final Set<UUID> turned = new HashSet<>();
        final Map<UUID, Long> lastInteraction = new HashMap<>();
        UUID hologramId;

        Npc(NpcData data) {
            this.data = data;
        }
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final double HOLOGRAM_HEIGHT = 2.05;
    private static final long CLICK_DEDUPE_MILLIS = 150;

    private final JavaPlugin plugin;
    private final NpcPackets packets;
    private final NamespacedKey hologramKey;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final ConcurrentHashMap<Integer, String> entityIds = new ConcurrentHashMap<>();
    private final AsyncSaver<String> saver;
    private BukkitTask turnTask;

    private static final long SAVE_PERIOD_TICKS = 100L;

    public NpcManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.packets = NpcPackets.createOrNull(plugin.getLogger());
        this.hologramKey = new NamespacedKey(plugin, "npc-hologram");
        this.saver = new AsyncSaver<>(new HcgScheduler(plugin), SAVE_PERIOD_TICKS,
                this::snapshot, this::writeYaml);
    }

    public boolean isAvailable() {
        return packets != null;
    }

    public void load() {
        if (packets == null) {
            return;
        }
        File file = npcsFile();
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = config.getConfigurationSection("npcs");
            if (section != null) {
                for (String name : section.getKeys(false)) {
                    NpcData data = NpcData.load(name, section.getConfigurationSection(name));
                    npcs.put(name.toLowerCase(Locale.ROOT), new Npc(data));
                }
            }
        }
        for (Npc npc : npcs.values()) {
            spawnRuntime(npc);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            handleJoin(player);
        }
        turnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTurning, 20, 3);
        saver.start();
    }

    public void shutdown() {
        if (packets == null) {
            return;
        }
        saver.flushNow();
        if (turnTask != null) {
            turnTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            packets.uninject(player);
        }
        for (Npc npc : npcs.values()) {
            for (Player viewer : onlineViewers(npc)) {
                despawnFor(viewer, npc);
            }
            npc.viewers.clear();
            removeHologram(npc);
            removeTeam(npc);
        }
        npcs.clear();
        entityIds.clear();
    }

    public void save() {
        saver.markDirty();
    }

    private String snapshot() {
        YamlConfiguration config = new YamlConfiguration();
        for (Npc npc : npcs.values()) {
            npc.data.save(config.createSection("npcs." + npc.data.name()));
        }
        return config.saveToString();
    }

    private void writeYaml(String yaml) {
        try {
            Path path = npcsFile().toPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save npcs.yml: " + e.getMessage());
        }
    }

    private File npcsFile() {
        return new File(plugin.getDataFolder(), "npcs.yml");
    }

    public Npc get(String name) {
        return npcs.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Npc> all() {
        return npcs.values();
    }

    public List<String> names() {
        return npcs.values().stream().map(npc -> npc.data.name()).toList();
    }

    public Npc create(String name, Location location) {
        Npc npc = new Npc(new NpcData(name, UUID.randomUUID(), location));
        npcs.put(name.toLowerCase(Locale.ROOT), npc);
        spawnRuntime(npc);
        showToWorld(npc);
        save();
        return npc;
    }

    public void delete(Npc npc) {
        for (Player viewer : onlineViewers(npc)) {
            despawnFor(viewer, npc);
        }
        npc.viewers.clear();
        removeHologram(npc);
        removeTeam(npc);
        if (npc.entityId != -1) {
            entityIds.remove(npc.entityId);
        }
        npcs.remove(npc.data.name().toLowerCase(Locale.ROOT));
        save();
    }

    private void spawnRuntime(Npc npc) {
        Location location = npc.data.location();
        if (location == null) {
            return;
        }
        try {
            npc.entity = packets.createEntity(location.getWorld(), npc.data.uuid(),
                    npc.data.profileName(), npc.data.skinValue(), npc.data.skinSignature(), location);
            npc.entityId = packets.entityId(npc.entity);
            entityIds.put(npc.entityId, npc.data.name().toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not create NPC '" + npc.data.name() + "': " + e);
            return;
        }
        setupTeam(npc);
        spawnHologram(npc);
    }

    public void handleWorldLoad(World world) {
        for (Npc npc : npcs.values()) {
            if (npc.entity == null && npc.data.worldName().equals(world.getName())) {
                spawnRuntime(npc);
            }
        }
    }

    private void showTo(Player player, Npc npc) {
        Location location = npc.data.location();
        if (npc.entity == null || location == null
                || !player.getWorld().getName().equals(npc.data.worldName())) {
            return;
        }
        try {
            packets.sendSpawn(player, npc.entity, npc.data.uuid(), packets.gameProfile(npc.entity),
                    npc.data.isShowInTab(), location, npc.data.isGlowing());
            if (!npc.data.equipment().isEmpty()) {
                packets.sendEquipment(player, npc.entityId, npc.data.equipment());
            }
            npc.viewers.add(player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not show NPC '" + npc.data.name() + "': " + e);
        }
    }

    private void despawnFor(Player player, Npc npc) {
        try {
            packets.sendDespawn(player, npc.entityId, npc.data.uuid());
        } catch (Exception ignored) {
        }
    }

    private void showToWorld(Npc npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equals(npc.data.worldName())) {
                showTo(player, npc);
            }
        }
    }

    public void rebuild(Npc npc) {
        List<Player> viewers = onlineViewers(npc);
        for (Player viewer : viewers) {
            despawnFor(viewer, npc);
        }
        npc.viewers.clear();
        npc.turned.clear();
        if (npc.entityId != -1) {
            entityIds.remove(npc.entityId);
            npc.entityId = -1;
        }
        npc.entity = null;
        removeHologram(npc);
        spawnRuntime(npc);
        showToWorld(npc);
    }

    private List<Player> onlineViewers(Npc npc) {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : npc.viewers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    public void handleJoin(Player player) {
        packets.inject(player, this::onPacketClick);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                showWorldNpcs(player);
            }
        }, 10);
    }

    public void handleQuit(Player player) {
        packets.uninject(player);
        for (Npc npc : npcs.values()) {
            npc.viewers.remove(player.getUniqueId());
            npc.turned.remove(player.getUniqueId());
            npc.lastInteraction.remove(player.getUniqueId());
        }
    }

    public void handleWorldRefresh(Player player) {
        for (Npc npc : npcs.values()) {
            npc.viewers.remove(player.getUniqueId());
            npc.turned.remove(player.getUniqueId());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                showWorldNpcs(player);
            }
        }, 2);
    }

    public void handleFarTeleport(Player player) {
        for (Npc npc : npcs.values()) {
            if (npc.viewers.remove(player.getUniqueId())) {
                npc.turned.remove(player.getUniqueId());
                despawnFor(player, npc);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                showWorldNpcs(player);
            }
        }, 2);
    }

    private void showWorldNpcs(Player player) {
        for (Npc npc : npcs.values()) {
            if (!npc.viewers.contains(player.getUniqueId())) {
                showTo(player, npc);
            }
        }
    }

    public void handleChunkLoad(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            String owner = entity.getPersistentDataContainer().get(hologramKey, PersistentDataType.STRING);
            if (owner == null) {
                continue;
            }
            Npc npc = get(owner);
            if (npc == null || !entity.getUniqueId().equals(npc.hologramId)) {
                entity.remove();
            }
        }
        for (Npc npc : npcs.values()) {
            Location location = npc.data.location();
            if (location != null && location.getWorld().equals(chunk.getWorld())
                    && location.getBlockX() >> 4 == chunk.getX()
                    && location.getBlockZ() >> 4 == chunk.getZ()
                    && npc.data.hasHologram()
                    && (npc.hologramId == null || Bukkit.getEntity(npc.hologramId) == null)) {
                spawnHologram(npc);
            }
        }
    }

    public void applyLocation(Npc npc, Location location) {
        boolean worldChanged = !npc.data.worldName().equals(location.getWorld().getName());
        npc.data.setLocation(location);
        save();
        if (npc.entity == null || worldChanged || !packets.canTeleport()) {
            rebuild(npc);
            return;
        }
        try {
            packets.position(npc.entity, location);
            for (Player viewer : onlineViewers(npc)) {
                packets.sendTeleport(viewer, npc.entity, location);
            }
        } catch (Exception e) {
            rebuild(npc);
            return;
        }
        refreshHologram(npc);
    }

    public void applyRotation(Npc npc, float yaw, float pitch) {
        npc.data.setRotation(yaw, pitch);
        save();
        Location location = npc.data.location();
        if (npc.entity == null || location == null) {
            return;
        }
        try {
            packets.position(npc.entity, location);
            for (Player viewer : onlineViewers(npc)) {
                packets.sendRotation(viewer, npc.entity, npc.entityId, yaw, pitch);
            }
        } catch (Exception ignored) {
        }
    }

    public void applyEquipment(Npc npc) {
        save();
        if (npc.entity == null) {
            return;
        }
        try {
            for (Player viewer : onlineViewers(npc)) {
                packets.sendEquipment(viewer, npc.entityId, npc.data.equipment());
            }
        } catch (Exception ignored) {
        }
    }

    public void applyGlowing(Npc npc) {
        save();
        setupTeam(npc);
        if (npc.entity == null) {
            return;
        }
        try {
            for (Player viewer : onlineViewers(npc)) {
                packets.sendMetadata(viewer, npc.entityId, npc.data.isGlowing());
            }
        } catch (Exception ignored) {
        }
    }

    public void applyCollidable(Npc npc) {
        save();
        setupTeam(npc);
    }

    public void applyDisplayName(Npc npc) {
        save();
        refreshHologram(npc);
    }

    private void setupTeam(Npc npc) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(npc.data.teamName());
        if (team == null) {
            team = scoreboard.registerNewTeam(npc.data.teamName());
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setOption(Team.Option.COLLISION_RULE,
                npc.data.isCollidable() ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER);
        ChatColor color = ChatColor.WHITE;
        if (npc.data.isGlowing()) {
            try {
                color = ChatColor.valueOf(npc.data.glowColor().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        team.setColor(color);
        if (!team.hasEntry(npc.data.profileName())) {
            team.addEntry(npc.data.profileName());
        }
    }

    private void removeTeam(Npc npc) {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(npc.data.teamName());
        if (team != null) {
            team.unregister();
        }
    }

    private void spawnHologram(Npc npc) {
        removeHologram(npc);
        Location location = npc.data.location();
        if (!npc.data.hasHologram() || location == null) {
            return;
        }
        Location at = location.clone().add(0, HOLOGRAM_HEIGHT, 0);
        at.setYaw(0);
        at.setPitch(0);
        TextDisplay display = location.getWorld().spawn(at, TextDisplay.class, entity -> {
            entity.text(hologramText(npc.data.displayName()));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setPersistent(false);
            entity.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING,
                    npc.data.name().toLowerCase(Locale.ROOT));
        });
        npc.hologramId = display.getUniqueId();
    }

    private void removeHologram(Npc npc) {
        if (npc.hologramId != null) {
            Entity entity = Bukkit.getEntity(npc.hologramId);
            if (entity != null) {
                entity.remove();
            }
            npc.hologramId = null;
        }
    }

    private void refreshHologram(Npc npc) {
        spawnHologram(npc);
    }

    private static Component hologramText(String displayName) {
        String[] lines = displayName.split("\\|");
        Component text = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                text = text.append(Component.newline());
            }
            text = text.append(LEGACY.deserialize(lines[i]));
        }
        return text;
    }

    private void tickTurning() {
        for (Npc npc : npcs.values()) {
            if (!npc.data.isTurnToPlayer() || npc.entity == null) {
                continue;
            }
            Location location = npc.data.location();
            if (location == null) {
                continue;
            }
            double maxSquared = npc.data.turnDistance() * npc.data.turnDistance();
            for (Player viewer : onlineViewers(npc)) {
                if (!viewer.getWorld().equals(location.getWorld())) {
                    continue;
                }
                double distSquared = viewer.getLocation().distanceSquared(location);
                try {
                    if (distSquared <= maxSquared) {
                        float[] rot = lookAt(location, viewer.getEyeLocation());
                        packets.sendRotation(viewer, npc.entity, npc.entityId, rot[0], rot[1]);
                        npc.turned.add(viewer.getUniqueId());
                    } else if (npc.turned.remove(viewer.getUniqueId())) {
                        packets.sendRotation(viewer, npc.entity, npc.entityId,
                                location.getYaw(), location.getPitch());
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static float[] lookAt(Location npcLocation, Location target) {
        double dx = target.getX() - npcLocation.getX();
        double dy = target.getY() - (npcLocation.getY() + 1.62);
        double dz = target.getZ() - npcLocation.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{yaw, pitch};
    }

    private boolean onPacketClick(Player player, int entityId, NpcPackets.ClickType type) {
        String name = entityIds.get(entityId);
        if (name == null) {
            return false;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Npc npc = get(name);
            if (npc != null && player.isOnline()) {
                handleClick(player, npc, type);
            }
        });
        return true;
    }

    private void handleClick(Player player, Npc npc, NpcPackets.ClickType type) {
        long now = System.currentTimeMillis();
        long last = npc.lastInteraction.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < Math.max(npc.data.cooldownMillis(), CLICK_DEDUPE_MILLIS)) {
            return;
        }
        npc.lastInteraction.put(player.getUniqueId(), now);

        String trigger = type == NpcPackets.ClickType.LEFT ? "left_click" : "right_click";
        List<String> actions = new ArrayList<>(npc.data.actions(trigger));
        actions.addAll(npc.data.actions("any_click"));
        if (!actions.isEmpty()) {
            runActions(player, actions, 0);
        }
    }

    private void runActions(Player player, List<String> actions, int start) {
        for (int i = start; i < actions.size(); i++) {
            if (!player.isOnline()) {
                return;
            }
            String action = actions.get(i);
            int space = action.indexOf(' ');
            String type = (space == -1 ? action : action.substring(0, space)).toLowerCase(Locale.ROOT);
            String args = space == -1 ? "" : action.substring(space + 1);
            String filled = args.replace("{player}", player.getName());
            switch (type) {
                case "message" -> player.sendMessage(LEGACY.deserialize(filled));
                case "player_command" -> player.performCommand(
                        filled.startsWith("/") ? filled.substring(1) : filled);
                case "console_command" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        filled.startsWith("/") ? filled.substring(1) : filled);
                case "sound" -> playSound(player, filled);
                case "wait" -> {
                    long ticks;
                    try {
                        ticks = Long.parseLong(filled.trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    int next = i + 1;
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> runActions(player, actions, next), Math.max(1, ticks));
                    return;
                }
                default -> {
                }
            }
        }
    }

    private static void playSound(Player player, String args) {
        String[] parts = args.split(" ");
        float volume = parts.length > 1 ? parseFloat(parts[1], 1.0f) : 1.0f;
        float pitch = parts.length > 2 ? parseFloat(parts[2], 1.0f) : 1.0f;
        player.playSound(player.getLocation(), parts[0], volume, pitch);
    }

    private static float parseFloat(String input, float fallback) {
        try {
            return Float.parseFloat(input);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
