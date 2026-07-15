package dev.amissouri.hcg.healthdecay;
import dev.amissouri.hcg.HcgScheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Any player who damaged the victim within the configured combat window is credited when the victim dies.
 */
public final class KillListener implements Listener {

    private record Tag(UUID attacker, long timestamp) {}

    private final JavaPlugin plugin;
    private final HcgScheduler scheduler;
    private final DecayManager decayManager;
    private final Map<UUID, Tag> lastAttacker = new ConcurrentHashMap<>();

    public KillListener(JavaPlugin plugin, HcgScheduler scheduler, DecayManager decayManager) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.decayManager = decayManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
            lastAttacker.put(victim.getUniqueId(), new Tag(attacker.getUniqueId(), System.currentTimeMillis()));
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Tag tag = lastAttacker.remove(victim.getUniqueId());

        Player killer = victim.getKiller();
        if (killer == null && tag != null) {
            long windowMillis = plugin.getConfig().getLong("kill.credit-window-seconds", 45) * 1000L;
            if (System.currentTimeMillis() - tag.timestamp() <= windowMillis) {
                killer = plugin.getServer().getPlayer(tag.attacker());
            }
        }

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            decayManager.onPlayerKill(killer, victim);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (decayManager.isRunning()) {
            decayManager.apply(event.getPlayer());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (decayManager.isRunning()) {
            Player player = event.getPlayer();
            scheduler.entity(player, () -> decayManager.apply(player));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastAttacker.remove(event.getPlayer().getUniqueId());
    }
}
