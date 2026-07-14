package dev.amissouri.hcg;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /fly, toggles flight. */
public final class FlyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return true;
        }
        boolean enable = !player.getAllowFlight();
        player.setAllowFlight(enable);
        if (!enable && player.isFlying()) {
            player.setFlying(false);
        }
        if (!enable) {
            Messages.send(player, "commands.fly.disabled");
        }
        return true;
    }
}
