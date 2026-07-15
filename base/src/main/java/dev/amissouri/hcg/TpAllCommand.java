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

    private final HcgScheduler scheduler;

    public TpAllCommand(HcgScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "here" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, "commands.tpall.console-cant", "option", "here");
                    return true;
                }
                teleportAll(sender, player.getLocation(), player,
                        Messages.raw("commands.tpall.where-here"));
            }
            case "looking" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, "commands.tpall.console-cant", "option", "looking");
                    return true;
                }
                Block target = player.getTargetBlockExact(LOOKING_RANGE);
                if (target == null) {
                    Messages.send(sender, "commands.tpall.no-block", "range", String.valueOf(LOOKING_RANGE));
                    return true;
                }
                Location destination = target.getLocation().add(0.5, 1.0, 0.5);
                destination.setYaw(player.getLocation().getYaw());
                teleportAll(sender, destination, player, Messages.raw("commands.tpall.where-looking"));
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    Messages.send(sender, "general.player-not-online", "player", args[0]);
                    return true;
                }
                if (scheduler.entity(target, () -> teleportAll(sender, target.getLocation(), target,
                        Messages.raw("commands.tpall.where-player", "player", target.getName()))) == null) {
                    Messages.send(sender, "general.player-not-online", "player", args[0]);
                }
            }
        }
        return true;
    }

    private void teleportAll(CommandSender sender, Location destination, Player excluded, String where) {
        List<? extends Player> targets = Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.equals(excluded) && !player.equals(sender))
                .toList();
        Players.forEach(scheduler, targets, player -> player.teleportAsync(destination));
        Messages.send(sender, "commands.tpall.done",
                "count", String.valueOf(targets.size()), "where", where);
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
