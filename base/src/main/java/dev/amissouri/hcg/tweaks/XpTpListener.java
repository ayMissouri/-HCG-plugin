package dev.amissouri.hcg.tweaks;

import java.util.concurrent.ThreadLocalRandom;

import dev.amissouri.hcg.HcgScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public final class XpTpListener implements Listener {

    private final XpTpTweak tweak;
    private final HcgScheduler scheduler;

    public XpTpListener(XpTpTweak tweak, HcgScheduler scheduler) {
        this.tweak = tweak;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!tweak.isEnabled()) {
            return;
        }
        if (event.getEntity() instanceof Player && !tweak.includePlayers()) {
            return;
        }
        int xp = event.getDroppedExp();
        if (xp <= 0) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.isDead() || !killer.hasPermission("hcg.xptp.use")) {
            return;
        }
        event.setDroppedExp(0);
        deliver(killer, xp);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!tweak.isEnabled() || !tweak.blocks()) {
            return;
        }
        int xp = event.getExpToDrop();
        if (xp <= 0) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isDead() || !player.hasPermission("hcg.xptp.use")) {
            return;
        }
        event.setExpToDrop(0);
        deliver(player, xp);
    }

    // A ranged kill can leave the receiver in another region on Folia.
    private void deliver(Player player, int xp) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            give(player, xp);
        } else {
            scheduler.entity(player, () -> give(player, xp));
        }
    }

    private void give(Player player, int xp) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        player.giveExp(xp, tweak.mending());
        if (tweak.sound()) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f,
                    0.9f + ThreadLocalRandom.current().nextFloat() * 0.4f);
        }
    }
}
