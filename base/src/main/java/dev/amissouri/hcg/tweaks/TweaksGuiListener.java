package dev.amissouri.hcg.tweaks;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class TweaksGuiListener implements Listener {

    private final TweaksGui gui;

    public TweaksGuiListener(TweaksGui gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TweaksGui.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != holder.getInventory()
                || !(event.getWhoClicked() instanceof Player player)
                || !player.hasPermission("hcg.tweaks")) {
            return;
        }
        gui.handleClick(holder, player, event.getSlot(), !event.isRightClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TweaksGui.Holder) {
            event.setCancelled(true);
        }
    }
}
