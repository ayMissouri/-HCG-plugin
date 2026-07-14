package dev.amissouri.hcg;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** /spawner <mob>, changes the mob type of the spawner you're looking at. */
public final class SpawnerCommand implements CommandExecutor, TabCompleter {

    private static final int RANGE = 10;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return true;
        }
        if (args.length == 0) {
            return false;
        }
        EntityType type = parseMob(args[0]);
        if (type == null) {
            Messages.send(sender, "commands.spawner.unknown-mob", "input", args[0]);
            return true;
        }
        Block block = player.getTargetBlockExact(RANGE);
        if (block == null || block.getType() != Material.SPAWNER) {
            Messages.send(sender, "commands.spawner.look-at", "range", String.valueOf(RANGE));
            return true;
        }
        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        spawner.setSpawnedType(type);
        spawner.update();
        Messages.send(sender, "commands.spawner.set", "mob", type.name().toLowerCase());
        return true;
    }

    static EntityType parseMob(String input) {
        try {
            EntityType type = EntityType.valueOf(input.toUpperCase());
            return type.isAlive() && type.isSpawnable() && type != EntityType.PLAYER ? type : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static List<String> mobCompletions(String prefix) {
        return Arrays.stream(EntityType.values())
                .filter(t -> t.isAlive() && t.isSpawnable() && t != EntityType.PLAYER)
                .map(t -> t.name().toLowerCase())
                .filter(name -> name.startsWith(prefix.toLowerCase()))
                .sorted()
                .toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return mobCompletions(args[0]);
        }
        return List.of();
    }
}
