package dev.amissouri.hcg;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /burn <player> [seconds], sets a player on fire (default 5 seconds). */
public final class BurnCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Messages.send(sender, "general.player-not-online", "player", args[0]);
            return true;
        }
        int seconds = 5;
        if (args.length >= 2) {
            try {
                seconds = Math.clamp(Integer.parseInt(args[1]), 1, 600);
            } catch (NumberFormatException e) {
                Messages.send(sender, "general.not-a-number", "input", args[1]);
                return true;
            }
        }
        target.setFireTicks(seconds * 20);
        Messages.send(sender, "commands.burn.done",
                "player", target.getName(), "seconds", String.valueOf(seconds));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            return List.of("5", "10", "30", "60").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }
}
