package dev.amissouri.hcg;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /flyspeed <1-10>, sets the fly speed. Vanilla default is 1. */
public final class FlySpeedCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return true;
        }
        if (args.length == 0) {
            return false;
        }
        double speed;
        try {
            speed = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            Messages.send(sender, "general.not-a-number", "input", args[0]);
            return true;
        }
        if (speed < 1 || speed > 10) {
            Messages.send(sender, "commands.flyspeed.out-of-range");
            return true;
        }
        player.setFlySpeed((float) (speed / 10.0));
        Messages.send(player, "commands.flyspeed.set", "speed", args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10").stream()
                    .filter(s -> s.startsWith(args[0]))
                    .toList();
        }
        return List.of();
    }
}
