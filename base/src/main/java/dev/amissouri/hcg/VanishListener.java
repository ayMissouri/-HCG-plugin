package dev.amissouri.hcg;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class VanishListener implements Listener {

    private final VanishManager vanishManager;

    public VanishListener(VanishManager vanishManager) {
        this.vanishManager = vanishManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        vanishManager.handleJoin(event.getPlayer());
        if (vanishManager.isVanished(event.getPlayer())) {
            Messages.send(event.getPlayer(), "commands.vanish.still-vanished");
        }
    }
}
