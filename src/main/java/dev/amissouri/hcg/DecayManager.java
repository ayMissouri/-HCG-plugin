package dev.amissouri.hcg;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * everyone's max health slowly ticks down to a floor, and a confirmed player-vs-player kill restores everyone to full.
 */
public final class DecayManager {

    private final HCGPlugin plugin;

    private BukkitTask decayTask;
    private double currentMaxHp;
    private boolean bottomedOutAnnounced;

    public DecayManager(HCGPlugin plugin) {
        this.plugin = plugin;
        this.currentMaxHp = maximumHp();
    }

    public boolean isRunning() {
        return decayTask != null;
    }

    public double currentMaxHp() {
        return currentMaxHp;
    }

    public double maximumHp() {
        return plugin.getConfig().getDouble("decay.maximum-hearts", 10.0) * 2.0;
    }

    public double minimumHp() {
        return plugin.getConfig().getDouble("decay.minimum-hearts", 3.0) * 2.0;
    }

    public double decayAmountHp() {
        return plugin.getConfig().getDouble("decay.amount-hearts", 0.5) * 2.0;
    }

    public long intervalTicks() {
        return Math.max(1, plugin.getConfig().getLong("decay.interval-seconds", 60)) * 20L;
    }

    public void start() {
        stopTask();
        plugin.getConfig().set("enabled", true);
        plugin.saveConfig();
        scheduleTask();
        applyToAll();
    }

    /** Stops decay and restores everyone to full health. */
    public void stop() {
        stopTask();
        plugin.getConfig().set("enabled", false);
        plugin.saveConfig();
        resetHealth();
    }

    /** undo max-health changes without saving config. */
    public void shutdown() {
        stopTask();
        currentMaxHp = maximumHp();
        applyToAll();
    }

    private void scheduleTask() {
        long interval = intervalTicks();
        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void restartTimer() {
        if (decayTask != null) {
            stopTask();
            scheduleTask();
        }
    }

    private void stopTask() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
    }

    // core

    private void tick() {
        double min = minimumHp();
        if (currentMaxHp <= min) {
            return;
        }
        currentMaxHp = Math.max(min, currentMaxHp - decayAmountHp());
        applyToAll();

        if (currentMaxHp <= min && !bottomedOutAnnounced) {
            bottomedOutAnnounced = true;
            Messages.broadcastOps("healthdecay.bottomed-out", "hearts", formatHearts(min));
        }
    }

    /** restore everyone and start over. */
    public void onPlayerKill(Player killer, Player victim) {
        if (!isRunning()) {
            return;
        }
        resetHealth();
        restartTimer();
        Messages.broadcastOps("healthdecay.kill-broadcast",
                "killer", killer.getName(), "victim", victim.getName());
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    /** Restores everyone's max health and heals them to full. */
    public void resetHealth() {
        currentMaxHp = maximumHp();
        bottomedOutAnnounced = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
            player.setHealth(currentMaxHp);
        }
    }

    public void applyToAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
    }

    /** Sets the player's max health to the current global value. */
    public void apply(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        attribute.setBaseValue(currentMaxHp);
        if (player.getHealth() > currentMaxHp) {
            player.setHealth(currentMaxHp);
        }
    }

    public static String formatHearts(double hp) {
        double hearts = hp / 2.0;
        return hearts == Math.floor(hearts) ? String.valueOf((long) hearts) : String.valueOf(hearts);
    }
}
