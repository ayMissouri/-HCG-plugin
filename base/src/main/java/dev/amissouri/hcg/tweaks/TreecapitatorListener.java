package dev.amissouri.hcg.tweaks;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.Messages;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;

public final class TreecapitatorListener implements Listener {

    private final Set<UUID> felling = ConcurrentHashMap.newKeySet();

    private final TreecapitatorTweak tweak;
    private final TweakEnchant enchant;
    private final HcgScheduler scheduler;

    public TreecapitatorListener(TreecapitatorTweak tweak, TweakEnchant enchant, HcgScheduler scheduler) {
        this.tweak = tweak;
        this.enchant = enchant;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (felling.contains(player.getUniqueId())) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!tweak.shouldTrigger(player, event.getBlock(), tool)) {
            return;
        }
        felling.add(player.getUniqueId());
        try {
            tweak.fell(player, event.getBlock(), tool);
        } finally {
            felling.remove(player.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock falling && tweak.handleLand(falling)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD
                || event.getCause() == EntityRemoveEvent.Cause.PLUGIN) {
            return;
        }
        tweak.handleRemoved(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        int level = tweak.rollEnchant(item, event.getExpLevelCost());
        if (level <= 0) {
            return;
        }
        Player player = event.getEnchanter();
        Material type = item.getType();
        if (!(event.getInventory() instanceof EnchantingInventory table)) {
            return;
        }
        scheduler.entityDelayed(player, () -> {
            ItemStack result = table.getItem();
            if (result == null || result.getType() != type || enchant.has(result)) {
                return;
            }
            enchant.apply(result, level);
            table.setItem(result);
            Messages.send(player, "tweaks.treecapitator.enchant-gained",
                    "name", enchant.displayName(level));
        }, 1L);
    }
}
