package dev.amissouri.hcg;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /anvil, virtual anvil, /craft, virtual crafting table,
 * /enderchest (/ec) [player], your (or another player's) ender chest.
 */
public final class OpenMenuCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "anvil" -> player.openAnvil(null, true);
            case "craft" -> player.openWorkbench(null, true);
            case "enderchest" -> {
                Player target = player;
                if (args.length >= 1) {
                    target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        Messages.send(sender, "general.player-not-online", "player", args[0]);
                        return true;
                    }
                    if (!target.equals(player) && HcgPlatform.isFolia()) {
                        Messages.send(sender, "general.folia-no-shared-inventory");
                        return true;
                    }
                }
                player.openInventory(target.getEnderChest());
                if (!target.equals(player)) {
                    Messages.send(player, "commands.enderchest.viewing", "player", target.getName());
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && command.getName().equalsIgnoreCase("enderchest")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
