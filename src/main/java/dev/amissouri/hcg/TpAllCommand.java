package dev.amissouri.hcg;

import java.util.List;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /tpall here, teleport everyone to the sender.
 * /tpall looking, teleport everyone to the block the sender is looking at.
 * /tpall <player>, teleport everyone to that player.
 */
public final class TpAllCommand implements CommandExecutor, TabCompleter {

    private static final int LOOKING_RANGE = 250;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        Location destination;
        Player excluded = sender instanceof Player player ? player : null;
        String where;

        switch (args[0].toLowerCase()) {
            case "here" -> {
                if (excluded == null) {
                    Messages.send(sender, "commands.tpall.console-cant", "option", "here");
                    return true;
                }
                destination = excluded.getLocation();
                where = Messages.raw("commands.tpall.where-here");
            }
            case "looking" -> {
                if (excluded == null) {
                    Messages.send(sender, "commands.tpall.console-cant", "option", "looking");
                    return true;
                }
                Block target = excluded.getTargetBlockExact(LOOKING_RANGE);
                if (target == null) {
                    Messages.send(sender, "commands.tpall.no-block", "range", String.valueOf(LOOKING_RANGE));
                    return true;
                }
                destination = target.getLocation().add(0.5, 1.0, 0.5);
                destination.setYaw(excluded.getLocation().getYaw());
                where = Messages.raw("commands.tpall.where-looking");
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    Messages.send(sender, "general.player-not-online", "player", args[0]);
                    return true;
                }
                destination = target.getLocation();
                excluded = target; // don't teleport the destination player onto themselves
                where = Messages.raw("commands.tpall.where-player", "player", target.getName());
            }
        }

        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(excluded) || player.equals(sender)) {
                continue;
            }
            player.teleport(destination);
            count++;
        }
        Messages.send(sender, "commands.tpall.done", "count", String.valueOf(count), "where", where);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Stream.concat(
                            Stream.of("here", "looking"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
