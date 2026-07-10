package dev.amissouri.hcg;

import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class VolcanoManager {

    public static final String DEBRIS_METADATA = "hcg-volcano-debris";

    private static final List<Material> DEBRIS = List.of(
            Material.MAGMA_BLOCK, Material.NETHERRACK, Material.BLACKSTONE,
            Material.BASALT, Material.COBBLESTONE, Material.COBBLED_DEEPSLATE);

    private final HCGPlugin plugin;
    private final Random random = new Random();
    private BukkitTask task;
    private BukkitTask scheduleTask;
    private long ticksLeft;
    private long prevDayTime = -1;
    private boolean shakeFlip;

    public VolcanoManager(HCGPlugin plugin) {
        this.plugin = plugin;
        if (isScheduled()) {
            startScheduleTask();
        }
    }

    public Location center() {
        String worldName = plugin.getConfig().getString("volcano.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                plugin.getConfig().getInt("volcano.x") + 0.5,
                plugin.getConfig().getInt("volcano.y"),
                plugin.getConfig().getInt("volcano.z") + 0.5);
    }

    public void setCenter(Location location) {
        plugin.getConfig().set("volcano.world", location.getWorld().getName());
        plugin.getConfig().set("volcano.x", location.getBlockX());
        plugin.getConfig().set("volcano.y", location.getBlockY());
        plugin.getConfig().set("volcano.z", location.getBlockZ());
        plugin.saveConfig();
    }

    public int durationSeconds() {
        return plugin.getConfig().getInt("volcano.duration-seconds", 20);
    }

    public void setDurationSeconds(int seconds) {
        plugin.getConfig().set("volcano.duration-seconds", seconds);
        plugin.saveConfig();
    }

    public boolean isShakeEnabled() {
        return plugin.getConfig().getBoolean("volcano.shake-enabled", true);
    }

    public void setShakeEnabled(boolean enabled) {
        plugin.getConfig().set("volcano.shake-enabled", enabled);
        plugin.saveConfig();
    }

    public int shakeRadius() {
        return plugin.getConfig().getInt("volcano.shake-radius", 150);
    }

    public void setShakeRadius(int blocks) {
        plugin.getConfig().set("volcano.shake-radius", blocks);
        plugin.saveConfig();
    }

    public boolean isErupting() {
        return task != null;
    }

    public boolean isScheduled() {
        return plugin.getConfig().getBoolean("volcano.schedule-enabled", false);
    }

    public int eruptTime() {
        return plugin.getConfig().getInt("volcano.erupt-time", 13000);
    }

    public void setSchedule(int ticks) {
        plugin.getConfig().set("volcano.schedule-enabled", true);
        plugin.getConfig().set("volcano.erupt-time", ticks);
        plugin.saveConfig();
        prevDayTime = -1;
        startScheduleTask();
    }

    public void disableSchedule() {
        plugin.getConfig().set("volcano.schedule-enabled", false);
        plugin.saveConfig();
        if (scheduleTask != null) {
            scheduleTask.cancel();
            scheduleTask = null;
        }
    }

    private void startScheduleTask() {
        if (scheduleTask == null) {
            scheduleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scheduleTick, 5L, 5L);
        }
    }

    private void scheduleTick() {
        Location center = center();
        if (center == null) {
            return; // no crater set yet — nothing to erupt
        }
        long dayTime = center.getWorld().getTime();
        if (prevDayTime >= 0 && dayTime != prevDayTime && !isErupting()
                && LavaRaiseManager.crossed(eruptTime(), prevDayTime, dayTime)) {
            erupt(durationSeconds());
        }
        prevDayTime = dayTime;
    }

    public boolean erupt(int seconds) {
        if (isErupting() || center() == null) {
            return false;
        }
        ticksLeft = seconds * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        return true;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (scheduleTask != null) {
            scheduleTask.cancel();
            scheduleTask = null;
        }
    }

    private void tick() {
        Location center = center();
        if (center == null || --ticksLeft <= 0) {
            stop();
            return;
        }
        World world = center.getWorld();

        world.spawnParticle(Particle.FLAME, center, 25, 1.5, 1.0, 1.5, 0.08, null, true);
        world.spawnParticle(Particle.LAVA, center, 12, 2.0, 1.0, 2.0, 0.0, null, true);
        world.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(0, 2, 0),
                20, 1.5, 3.0, 1.5, 0.02, null, true);
        if (ticksLeft % 8 == 0) {
            world.spawnParticle(Particle.EXPLOSION, center, 1, 1.0, 1.0, 1.0, 0.0, null, true);
        }

        if (ticksLeft % 12 == 0) {
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 6.0f,
                    0.5f + random.nextFloat() * 0.4f);
        }
        if (ticksLeft % 5 == 0) {
            world.playSound(center, Sound.BLOCK_LAVA_POP, 3.0f, 0.6f + random.nextFloat() * 0.6f);
        }

        if (ticksLeft % 2 == 0) {
            int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                Location spawn = center.clone().add(random.nextDouble() * 2 - 1, 0.5,
                        random.nextDouble() * 2 - 1);
                Material material = DEBRIS.get(random.nextInt(DEBRIS.size()));
                FallingBlock debris = world.spawnFallingBlock(spawn, material.createBlockData());
                debris.setVelocity(new Vector(
                        (random.nextDouble() * 2 - 1) * 0.7,
                        1.0 + random.nextDouble() * 0.9,
                        (random.nextDouble() * 2 - 1) * 0.7));
                debris.setDropItem(false);
                debris.setHurtEntities(true);
                debris.setMetadata(DEBRIS_METADATA, new FixedMetadataValue(plugin, true));
            }
        }

        if (isShakeEnabled() && ticksLeft % 3 == 0) {
            shakeFlip = !shakeFlip;
            float sign = shakeFlip ? 1f : -1f;
            double radius = shakeRadius();
            double radiusSquared = radius * radius;
            for (Player player : world.getPlayers()) {
                double distSquared = player.getLocation().distanceSquared(center);
                if (distSquared > radiusSquared) {
                    continue;
                }
                float falloff = (float) (1.0 - Math.sqrt(distSquared) / radius);
                float yaw = player.getLocation().getYaw()
                        + sign * (0.4f + random.nextFloat() * 1.2f) * falloff;
                float pitch = Math.clamp(player.getLocation().getPitch()
                        + sign * (0.3f + random.nextFloat() * 0.8f) * falloff, -90f, 90f);
                player.setRotation(yaw, pitch);
            }
        }
    }
}
