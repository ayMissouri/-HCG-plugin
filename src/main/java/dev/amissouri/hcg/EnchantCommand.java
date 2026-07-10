package dev.amissouri.hcg;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * /enchant <enchantment> <level>, enchants the held item. Level 0 removes the enchantment.
 */
public final class EnchantCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_LEVEL = 255;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return true;
        }
        if (args.length < 2) {
            return false;
        }

        Enchantment enchantment;
        try {
            enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(args[0].toLowerCase()));
        } catch (IllegalArgumentException e) {
            enchantment = null;
        }
        if (enchantment == null) {
            Messages.send(sender, "commands.enchant.unknown", "input", args[0]);
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            Messages.send(sender, "general.not-a-number", "input", args[1]);
            return true;
        }
        if (level < 0 || level > MAX_LEVEL) {
            Messages.send(sender, "commands.enchant.level-range", "max", String.valueOf(MAX_LEVEL));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            Messages.send(sender, "commands.enchant.hold-item");
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        String name = enchantment.getKey().getKey();
        if (level == 0) {
            if (!meta.hasEnchant(enchantment)) {
                Messages.send(sender, "commands.enchant.not-present", "enchant", name);
                return true;
            }
            meta.removeEnchant(enchantment);
            item.setItemMeta(meta);
            Messages.send(sender, "commands.enchant.removed", "enchant", name);
        } else {
            meta.addEnchant(enchantment, level, true);
            item.setItemMeta(meta);
            Messages.send(sender, "commands.enchant.enchanted",
                    "enchant", name, "level", String.valueOf(level));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Registry.ENCHANTMENT.stream()
                    .map(e -> e.getKey().getKey())
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }
        if (args.length == 2) {
            return List.of("1", "5", "10", "50", "100", "255").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }
}
