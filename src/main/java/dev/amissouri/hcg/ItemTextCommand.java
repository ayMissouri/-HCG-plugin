package dev.amissouri.hcg;

import java.util.Arrays;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * /name <text>, renames the held item. 
 * /lore <text>, sets the held item's lore. 
 * Both support &-color codes (&a, &l, ...); lore supports multiple lines separated by "|". Use "reset" to clear.
 */
public final class ItemTextCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return true;
        }
        if (args.length == 0) {
            return false;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            Messages.send(sender, "commands.itemtext.hold-item");
            return true;
        }

        String input = String.join(" ", args);
        boolean reset = input.equalsIgnoreCase("reset");
        boolean isName = command.getName().equalsIgnoreCase("name");
        ItemMeta meta = item.getItemMeta();

        if (isName) {
            meta.displayName(reset ? null : format(input));
        } else {
            meta.lore(reset ? null
                    : Arrays.stream(input.split("\\|"))
                            .map(line -> (Component) format(line.trim()))
                            .toList());
        }
        item.setItemMeta(meta);

        String prefix = isName ? "commands.itemtext.name" : "commands.itemtext.lore";
        if (reset) {
            Messages.send(sender, prefix + "-reset");
        } else {
            sender.sendMessage(Messages.msg(prefix + "-set").append(format(input)));
        }
        return true;
    }

    private Component format(String input) {
        return LEGACY.deserialize(input)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
