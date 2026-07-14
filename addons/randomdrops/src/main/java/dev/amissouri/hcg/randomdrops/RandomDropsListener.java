package dev.amissouri.hcg.randomdrops;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public final class RandomDropsListener implements Listener {

    private final RandomDropsManager manager;

    public RandomDropsListener(RandomDropsManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        event.setDropItems(false);
        Location center = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        event.getBlock().getWorld().dropItemNaturally(center,
                manager.createDrop(event.getBlock().getType()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!manager.isEnabled() || !manager.isMobsEnabled()) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            return;
        }
        event.getDrops().clear();
        event.getDrops().add(manager.createMobDrop(event.getEntityType()));
    }
}
