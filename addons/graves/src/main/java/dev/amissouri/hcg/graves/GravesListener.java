package dev.amissouri.hcg.graves;
import dev.amissouri.hcg.Messages;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Stores death drops in a grave, lets only the owner collect it, and protects grave blocks from being destroyed or moved.
 */
public final class GravesListener implements Listener {

    private final GravesManager manager;

    public GravesListener(GravesManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!manager.isEnabled() || event.getKeepInventory()) {
            return;
        }
        Player victim = event.getEntity();
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        drops.removeIf(item -> item == null || item.getType().isAir());
        int xp = GravesManager.totalXp(victim);
        if (drops.isEmpty() && xp <= 0) {
            return;
        }
        Block block = manager.placeGrave(victim, drops, xp);
        if (block == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Messages.send(victim, "graves.created",
                "x", String.valueOf(block.getX()),
                "y", String.valueOf(block.getY()),
                "z", String.valueOf(block.getZ()),
                "world", block.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        GravesManager.Grave grave = manager.graveAt(event.getClickedBlock());
        if (grave == null) {
            return;
        }
        event.setCancelled(true);
        openGrave(event.getPlayer(), event.getClickedBlock(), grave);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        GravesManager.Grave grave = manager.graveAt(event.getBlock());
        if (grave == null) {
            return;
        }
        event.setCancelled(true);
        openGrave(event.getPlayer(), event.getBlock(), grave);
    }

    private void openGrave(Player player, Block block, GravesManager.Grave grave) {
        if (!grave.owner().equals(player.getUniqueId())) {
            Messages.send(player, "graves.not-yours", "owner", grave.ownerName());
            return;
        }
        GravesManager.Grave collected = manager.collect(player, block);
        if (collected != null) {
            int items = collected.items().stream().mapToInt(ItemStack::getAmount).sum();
            Messages.send(player, "graves.collected",
                    "items", String.valueOf(items),
                    "xp", String.valueOf(collected.xp()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> manager.graveAt(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> manager.graveAt(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> manager.graveAt(block) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> manager.graveAt(block) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (manager.graveAt(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }
}
