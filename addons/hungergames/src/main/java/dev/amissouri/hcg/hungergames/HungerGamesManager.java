package dev.amissouri.hcg.hungergames;
import dev.amissouri.hcg.Messages;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Hunger Games match sequence: a small starting border, an on-screen title countdown,
 * a quick expansion, then staged shrinks toward a final size (storm style).
 * Spawn points, border sizes, and all timings live in config.yml.
 */
public final class HungerGamesManager {

    private enum Phase { COUNTDOWN, EXPANDING, HOLDING, SHRINKING }

    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300));

    private final JavaPlugin plugin;
    private BukkitTask task;
    private World world;
    private Phase phase;
    private int counter;
    private int stage;
    private double previousSize;
    private double previousCenterX;
    private double previousCenterZ;

    public HungerGamesManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // --- Spawn points, stored as "world;x;y;z;yaw;pitch" strings ---

    public List<String> rawSpawns() {
        return plugin.getConfig().getStringList("hungergames.spawns");
    }

    public int spawnCount() {
        return rawSpawns().size();
    }

    /** @return the new spawn's 1-based index. */
    public int addSpawn(Location location) {
        List<String> raw = new ArrayList<>(rawSpawns());
        raw.add(String.format(Locale.ROOT, "%s;%.2f;%.2f;%.2f;%.1f;%.1f",
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
        plugin.getConfig().set("hungergames.spawns", raw);
        plugin.saveConfig();
        return raw.size();
    }

    public boolean removeSpawn(int index) {
        List<String> raw = new ArrayList<>(rawSpawns());
        if (index < 1 || index > raw.size()) {
            return false;
        }
        raw.remove(index - 1);
        plugin.getConfig().set("hungergames.spawns", raw);
        plugin.saveConfig();
        return true;
    }

    public int clearSpawns() {
        int count = spawnCount();
        plugin.getConfig().set("hungergames.spawns", List.of());
        plugin.saveConfig();
        return count;
    }

    /** Entries are null when malformed or their world isn't loaded. */
    public List<Location> spawns() {
        List<Location> spawns = new ArrayList<>();
        for (String raw : rawSpawns()) {
            spawns.add(parseSpawn(raw));
        }
        return spawns;
    }

    public static Location parseSpawn(String raw) {
        String[] parts = raw.split(";");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world,
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    parts.length >= 6 ? Float.parseFloat(parts[4]) : 0f,
                    parts.length >= 6 ? Float.parseFloat(parts[5]) : 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --- Border settings ---

    /** null until /hungergames setcenter, or while the saved world isn't loaded. */
    public Location borderCenter() {
        String worldName = plugin.getConfig().getString("hungergames.border.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                plugin.getConfig().getDouble("hungergames.border.center-x"), 0,
                plugin.getConfig().getDouble("hungergames.border.center-z"));
    }

    public void setBorderCenter(Location location) {
        plugin.getConfig().set("hungergames.border.world", location.getWorld().getName());
        plugin.getConfig().set("hungergames.border.center-x", location.getX());
        plugin.getConfig().set("hungergames.border.center-z", location.getZ());
        plugin.saveConfig();
    }

    public int setting(String key, int def) {
        return plugin.getConfig().getInt("hungergames." + key, def);
    }

    public void setSetting(String key, int value) {
        plugin.getConfig().set("hungergames." + key, value);
        plugin.saveConfig();
    }

    public int countdownSeconds() {
        return setting("countdown-seconds", 60);
    }

    public int startSize() {
        return setting("border.start-size", 100);
    }

    public int expandedSize() {
        return setting("border.expanded-size", 1000);
    }

    public int finalSize() {
        return setting("border.final-size", 30);
    }

    public int expandSeconds() {
        return setting("border.expand-seconds", 15);
    }

    public int stages() {
        return setting("stages", 5);
    }

    public int stageHoldSeconds() {
        return setting("stage-hold-seconds", 180);
    }

    public int stageShrinkSeconds() {
        return setting("stage-shrink-seconds", 60);
    }

    // --- Match sequence ---

    public boolean isRunning() {
        return task != null;
    }

    public String stateDescription() {
        if (!isRunning()) {
            return "idle";
        }
        return switch (phase) {
            case COUNTDOWN -> "counting down, " + counter + "s until expansion";
            case EXPANDING -> "border expanding, " + counter + "s left";
            case HOLDING -> "stage " + stage + "/" + stages() + ", next shrink in " + counter + "s";
            case SHRINKING -> "stage " + stage + "/" + stages() + " shrinking, " + counter + "s left";
        };
    }

    public void start() {
        start(countdownSeconds());
    }

    /** Requires a border center; check borderCenter() != null first. */
    public void start(int countdownSeconds) {
        Location center = borderCenter();
        world = center.getWorld();
        WorldBorder border = world.getWorldBorder();
        previousSize = border.getSize();
        previousCenterX = border.getCenter().getX();
        previousCenterZ = border.getCenter().getZ();
        border.setCenter(center.getX(), center.getZ());
        border.setSize(startSize());
        phase = Phase.COUNTDOWN;
        counter = countdownSeconds;
        stage = 0;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
    }

    /** Cancels the sequence and restores the border to its pre-game center and size. */
    public void stop() {
        if (task == null) {
            return;
        }
        cancelTask();
        WorldBorder border = world.getWorldBorder();
        border.setCenter(previousCenterX, previousCenterZ);
        border.setSize(previousSize);
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
    }

    private void tick() {
        switch (phase) {
            case COUNTDOWN -> {
                if (counter <= 0) {
                    beginExpansion();
                    return;
                }
                if (counter <= 10 || counter == 20 || counter % 30 == 0) {
                    showTitle("hungergames.countdown-title", "hungergames.countdown-subtitle",
                            "seconds", String.valueOf(counter));
                    playSound(Sound.BLOCK_NOTE_BLOCK_PLING, counter <= 3 ? 1.6f : 1f);
                }
                counter--;
            }
            case EXPANDING -> {
                if (--counter <= 0) {
                    stage = 1;
                    enterHold();
                }
            }
            case HOLDING -> {
                counter--;
                if (counter <= 0) {
                    beginShrink();
                } else if (counter == 60 || counter == 30 || counter == 10 || counter == 5) {
                    Messages.broadcast("hungergames.shrink-warning",
                            "size", String.valueOf(sizeFor(stage)),
                            "seconds", String.valueOf(counter));
                    playSound(Sound.BLOCK_NOTE_BLOCK_BASS, 1f);
                }
            }
            case SHRINKING -> {
                if (--counter <= 0) {
                    if (stage >= stages()) {
                        finish();
                    } else {
                        stage++;
                        enterHold();
                    }
                }
            }
        }
    }

    private void beginExpansion() {
        world.getWorldBorder().setSize(expandedSize(), expandSeconds());
        phase = Phase.EXPANDING;
        counter = expandSeconds();
        showTitle("hungergames.begin-title", "hungergames.begin-subtitle",
                "size", String.valueOf(expandedSize()));
        Messages.broadcast("hungergames.begin-broadcast",
                "size", String.valueOf(expandedSize()),
                "seconds", String.valueOf(expandSeconds()));
        playSound(Sound.ENTITY_ENDER_DRAGON_GROWL, 1f);
    }

    private void enterHold() {
        phase = Phase.HOLDING;
        counter = stageHoldSeconds();
        Messages.broadcast("hungergames.shrink-warning",
                "size", String.valueOf(sizeFor(stage)),
                "seconds", String.valueOf(counter));
    }

    private void beginShrink() {
        world.getWorldBorder().setSize(sizeFor(stage), stageShrinkSeconds());
        phase = Phase.SHRINKING;
        counter = stageShrinkSeconds();
        String[] pairs = {
                "stage", String.valueOf(stage),
                "stages", String.valueOf(stages()),
                "size", String.valueOf(sizeFor(stage)),
                "seconds", String.valueOf(stageShrinkSeconds())};
        showTitle("hungergames.shrink-title", "hungergames.shrink-subtitle", pairs);
        Messages.broadcast("hungergames.shrink-broadcast", pairs);
        playSound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f);
    }

    private void finish() {
        showTitle("hungergames.final-title", "hungergames.final-subtitle",
                "size", String.valueOf(finalSize()));
        Messages.broadcast("hungergames.final-broadcast", "size", String.valueOf(finalSize()));
        playSound(Sound.ENTITY_WITHER_SPAWN, 1.2f);
        cancelTask();
    }

    /** Stage sizes step evenly from the expanded size down to the final size. */
    private int sizeFor(int stage) {
        double expanded = expandedSize();
        double end = finalSize();
        int total = Math.max(1, stages());
        return (int) Math.round(expanded - (expanded - end) * stage / total);
    }

    private void showTitle(String titleKey, String subtitleKey, String... pairs) {
        Title title = Title.title(Messages.msg(titleKey, pairs), Messages.msg(subtitleKey, pairs), TITLE_TIMES);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }

    private void playSound(Sound sound, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, 1f, pitch);
        }
    }
}
