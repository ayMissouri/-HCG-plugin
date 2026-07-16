package dev.amissouri.hcg.tweaks;

import java.util.ArrayList;
import java.util.List;

import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class RecoveryCompassListener implements Listener {

    private final RecoveryCompassTweak tweak;
    private final HcgScheduler scheduler;
    private final NamespacedKey worldKey;
    private final NamespacedKey xKey;
    private final NamespacedKey yKey;
    private final NamespacedKey zKey;

    public RecoveryCompassListener(org.bukkit.plugin.Plugin plugin, RecoveryCompassTweak tweak,
            HcgScheduler scheduler) {
        this.tweak = tweak;
        this.scheduler = scheduler;
        this.worldKey = new NamespacedKey(plugin, "recovery_world");
        this.xKey = new NamespacedKey(plugin, "recovery_x");
        this.yKey = new NamespacedKey(plugin, "recovery_y");
        this.zKey = new NamespacedKey(plugin, "recovery_z");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!tweak.isEnabled()) {
            return;
        }
        Player player = event.getEntity();
        if (!player.hasPermission("hcg.recoverycompass.use")) {
            return;
        }
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(worldKey, PersistentDataType.STRING, world.getName());
        pdc.set(xKey, PersistentDataType.INTEGER, loc.getBlockX());
        pdc.set(yKey, PersistentDataType.INTEGER, loc.getBlockY());
        pdc.set(zKey, PersistentDataType.INTEGER, loc.getBlockZ());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String worldName = pdc.get(worldKey, PersistentDataType.STRING);
        Integer x = pdc.get(xKey, PersistentDataType.INTEGER);
        Integer y = pdc.get(yKey, PersistentDataType.INTEGER);
        Integer z = pdc.get(zKey, PersistentDataType.INTEGER);
        pdc.remove(worldKey);
        pdc.remove(xKey);
        pdc.remove(yKey);
        pdc.remove(zKey);
        if (worldName == null || x == null || y == null || z == null) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        ItemStack compass = buildCompass(new Location(world, x, y, z), x, y, z, worldName);
        scheduler.entityDelayed(player, () -> give(player, compass), 1L);
    }

    private ItemStack buildCompass(Location death, int x, int y, int z, String worldName) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.setLodestone(death);
        meta.setLodestoneTracked(false);
        meta.displayName(line(Messages.msg("tweaks.recoverycompass.item-name")));
        List<Component> lore = new ArrayList<>();
        lore.add(line(Messages.msg("tweaks.recoverycompass.item-lore")));
        if (tweak.coordinates()) {
            lore.add(line(Messages.msg("tweaks.recoverycompass.item-coords",
                    "x", String.valueOf(x), "y", String.valueOf(y), "z", String.valueOf(z),
                    "world", worldName)));
        }
        meta.lore(lore);
        if (tweak.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        compass.setItemMeta(meta);
        return compass;
    }

    private void give(Player player, ItemStack compass) {
        if (!player.isOnline()) {
            return;
        }
        for (ItemStack leftover : player.getInventory().addItem(compass).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
        Messages.send(player, "tweaks.recoverycompass.given");
    }

    private static Component line(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
