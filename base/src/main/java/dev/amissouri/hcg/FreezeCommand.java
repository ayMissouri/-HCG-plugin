package dev.amissouri.hcg;

import java.util.List;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /freeze <player>, toggle freeze on one player.
 * /freeze all, freeze everyone except the sender; if all of them are already frozen, unfreeze everyone instead.
 */
public final class FreezeCommand implements CommandExecutor, TabCompleter {

    private final FreezeManager freezeManager;

    public FreezeCommand(FreezeManager freezeManager) {
        this.freezeManager = freezeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        if (args[0].equalsIgnoreCase("all")) {
            List<Player> targets = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(sender))
                    .map(p -> (Player) p)
                    .toList();
            if (targets.isEmpty()) {
                Messages.send(sender, "commands.freeze.none-online");
                return true;
            }
            boolean allFrozen = targets.stream().allMatch(freezeManager::isFrozen);
            for (Player target : targets) {
                if (allFrozen) {
                    freezeManager.unfreeze(target);
                } else {
                    freezeManager.freeze(target);
                }
                notifyTarget(target, !allFrozen);
            }
            Messages.send(sender, allFrozen ? "commands.freeze.unfroze-all" : "commands.freeze.froze-all",
                    "count", String.valueOf(targets.size()));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Messages.send(sender, "general.player-not-online", "player", args[0]);
            return true;
        }
        boolean nowFrozen = freezeManager.toggle(target);
        notifyTarget(target, nowFrozen);
        Messages.send(sender, nowFrozen ? "commands.freeze.froze" : "commands.freeze.unfroze",
                "player", target.getName());
        return true;
    }

    private void notifyTarget(Player target, boolean frozen) {
        Messages.send(target, frozen ? "commands.freeze.target-frozen" : "commands.freeze.target-unfrozen");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Stream.concat(
                            Stream.of("all"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
