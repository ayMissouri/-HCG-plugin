package dev.amissouri.hcg.firstto;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class FirstToListener implements Listener {

    private final FirstToManager manager;

    public FirstToListener(FirstToManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            manager.handleCraft(player, event.getRecipe().getResult().getType());
        }
    }

    @EventHandler
    public void onRollGuiClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof FirstToManager.RollHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRollGuiClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof FirstToManager.RollHolder
                && event.getPlayer() instanceof Player player) {
            manager.handleGuiClose(player, event.getInventory());
        }
    }

    @EventHandler
    public void onRollGuiDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof FirstToManager.RollHolder) {
            event.setCancelled(true);
        }
    }
}
