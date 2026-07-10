package dev.amissouri.hcg;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /gmc (creative), /gms (survival), /gmsp (spectator), /gma (adventure).
 */
public final class GamemodeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        GameMode mode = switch (command.getName()) {
            case "gmc" -> GameMode.CREATIVE;
            case "gms" -> GameMode.SURVIVAL;
            case "gmsp" -> GameMode.SPECTATOR;
            case "gma" -> GameMode.ADVENTURE;
            default -> null;
        };
        if (mode == null) {
            return false;
        }

        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Messages.send(sender, "general.player-not-online", "player", args[0]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Messages.send(sender, "general.console-needs-player");
            return true;
        }

        target.setGameMode(mode);
        String modeName = mode.name().toLowerCase();
        Messages.send(target, "commands.gamemode.set", "mode", modeName);
        if (!target.equals(sender)) {
            Messages.send(sender, "commands.gamemode.set-other", "player", target.getName(), "mode", modeName);
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
