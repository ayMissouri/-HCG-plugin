package dev.amissouri.hcg;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /sudo <player> <message or /command>, makes the player chat the message, or run the command if it starts with a slash.
 */
public final class SudoCommand implements CommandExecutor, TabCompleter {

    private final HcgScheduler scheduler;

    public SudoCommand(HcgScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            return false;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Messages.send(sender, "general.player-not-online", "player", args[0]);
            return true;
        }
        String input = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (input.startsWith("/")) {
            scheduler.entity(target, () -> target.performCommand(input.substring(1)));
            Messages.send(sender, "commands.sudo.forced-command",
                    "player", target.getName(), "input", input);
        } else {
            scheduler.entity(target, () -> target.chat(input));
            Messages.send(sender, "commands.sudo.forced-chat",
                    "player", target.getName(), "input", input);
        }
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
        return List.of();
    }
}
