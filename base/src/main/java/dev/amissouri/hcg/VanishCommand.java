package dev.amissouri.hcg;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /vanish [player], toggles invisibility to other players. */
public final class VanishCommand implements CommandExecutor, TabCompleter {

    private final VanishManager vanishManager;

    public VanishCommand(VanishManager vanishManager) {
        this.vanishManager = vanishManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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

        boolean vanish = vanishManager.toggle(target);

        Messages.send(sender, vanish ? "commands.vanish.enabled-other" : "commands.vanish.disabled-other",
                "player", target.getName());
        if (sender != target) {
            Messages.send(target, vanish ? "commands.vanish.enabled-self" : "commands.vanish.disabled-self");
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
