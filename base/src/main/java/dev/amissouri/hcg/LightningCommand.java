package dev.amissouri.hcg;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /lightning, strikes lightning where the player is looking. */
public final class LightningCommand implements CommandExecutor {

    private static final int RANGE = 250;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return true;
        }
        Block target = player.getTargetBlockExact(RANGE);
        if (target == null) {
            Messages.send(sender, "commands.lightning.no-block", "range", String.valueOf(RANGE));
            return true;
        }
        target.getWorld().strikeLightning(target.getLocation().add(0.5, 1.0, 0.5));
        return true;
    }
}
