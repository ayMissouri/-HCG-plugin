package dev.amissouri.hcg.healthdecay;
import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.HcgText;
import dev.amissouri.hcg.Messages;
import dev.amissouri.hcg.Players;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * everyone's max health slowly ticks down to a floor, and a confirmed player-vs-player kill restores everyone to full.
 */
public final class DecayManager {

    private final JavaPlugin plugin;
    private final HcgScheduler scheduler;

    private volatile ScheduledTask decayTask;
    private volatile double currentMaxHp;
    private volatile boolean bottomedOutAnnounced;

    public DecayManager(JavaPlugin plugin, HcgScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
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
        scheduler.global(() -> {
            stopTask();
            plugin.getConfig().set("enabled", true);
            plugin.saveConfig();
            scheduleTask();
            applyToAll();
        });
    }

    /** Stops decay and restores everyone to full health. */
    public void stop() {
        scheduler.global(() -> {
            stopTask();
            plugin.getConfig().set("enabled", false);
            plugin.saveConfig();
            resetHealth();
        });
    }

    public void shutdown() {
        stopTask();
        double target = maximumHp();
        currentMaxHp = target;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                apply(player, target);
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not restore max health for " + player.getName()
                        + " on shutdown: " + t);
            }
        }
    }

    private void scheduleTask() {
        long interval = intervalTicks();
        decayTask = scheduler.globalTimer(this::tick, interval, interval);
    }

    public void restartTimer() {
        scheduler.global(this::restartTimerNow);
    }

    private void restartTimerNow() {
        if (decayTask != null) {
            stopTask();
            scheduleTask();
        }
    }

    private void stopTask() {
        HcgScheduler.cancel(decayTask);
        decayTask = null;
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
            Messages.broadcastOps("healthdecay.bottomed-out", "hearts", HcgText.formatHearts(min));
        }
    }

    public void onPlayerKill(Player killer, Player victim) {
        if (!isRunning()) {
            return;
        }
        String killerName = killer.getName();
        String victimName = victim.getName();
        scheduler.global(() -> {
            if (!isRunning()) {
                return;
            }
            resetHealth();
            restartTimerNow();
            Messages.broadcastOps("healthdecay.kill-broadcast",
                    "killer", killerName, "victim", victimName);
            Players.forEachOnline(scheduler,
                    player -> player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f));
        });
    }

    /** Restores everyone's max health and heals them to full. */
    public void resetHealth() {
        double target = maximumHp();
        currentMaxHp = target;
        bottomedOutAnnounced = false;
        Players.forEachOnline(scheduler, player -> {
            apply(player, target);
            player.setHealth(target);
        });
    }

    public void applyToAll() {
        double target = currentMaxHp;
        Players.forEachOnline(scheduler, player -> apply(player, target));
    }

    /** Sets the player's max health to the current global value. */
    public void apply(Player player) {
        apply(player, currentMaxHp);
    }

    private void apply(Player player, double maxHp) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        attribute.setBaseValue(maxHp);
        if (player.getHealth() > maxHp) {
            player.setHealth(maxHp);
        }
    }
}
