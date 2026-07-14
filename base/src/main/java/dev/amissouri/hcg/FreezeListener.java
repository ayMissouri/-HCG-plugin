package dev.amissouri.hcg;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Blocks movement for frozen players. */
public final class FreezeListener implements Listener {

    private final FreezeManager freezeManager;

    public FreezeListener(FreezeManager freezeManager) {
        this.freezeManager = freezeManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!freezeManager.isFrozen(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location clamped = from.clone();
            clamped.setYaw(to.getYaw());
            clamped.setPitch(to.getPitch());
            event.setTo(clamped);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!freezeManager.isFrozen(event.getPlayer())) {
            return;
        }

        switch (event.getCause()) {
            case ENDER_PEARL, CHORUS_FRUIT -> {
                event.setCancelled(true);
                Messages.send(event.getPlayer(), "commands.freeze.no-teleport");
            }
            default -> { }
        }
    }
}
