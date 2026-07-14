package dev.amissouri.hcg;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** /spawnmob <mob> [amount], spawns mobs at the player's location. */
public final class SpawnMobCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_AMOUNT = 1000;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return true;
        }
        if (args.length == 0) {
            return false;
        }
        EntityType type = SpawnerCommand.parseMob(args[0]);
        if (type == null) {
            Messages.send(sender, "commands.spawner.unknown-mob", "input", args[0]);
            return true;
        }
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Math.clamp(Integer.parseInt(args[1]), 1, MAX_AMOUNT);
            } catch (NumberFormatException e) {
                Messages.send(sender, "general.not-a-number", "input", args[1]);
                return true;
            }
        }
        for (int i = 0; i < amount; i++) {
            player.getWorld().spawnEntity(player.getLocation(), type);
        }
        Messages.send(sender, "commands.spawnmob.done",
                "amount", String.valueOf(amount),
                "mob", type.name().toLowerCase() + (amount == 1 ? "" : "s"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SpawnerCommand.mobCompletions(args[0]);
        }
        if (args.length == 2) {
            return List.of("1", "5", "10", "25").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }
}
