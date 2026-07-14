package dev.amissouri.hcg;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /god [player], toggles invulnerability. */
public final class GodCommand implements CommandExecutor, TabCompleter {

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

        boolean god = !target.isInvulnerable();
        target.setInvulnerable(god);
        
        Messages.send(sender, god ? "commands.god.enabled-other" : "commands.god.disabled-other",
                "player", target.getName());
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
