package dev.amissouri.hcg;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * /remove items, removes all loaded ground items.
 * /remove entities, removes all loaded non-player entities.
 * /remove mobs [hostile|neutral], removes all mobs, or only hostile/neutral ones.
 */
public final class RemoveCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        String what;
        int removed;
        switch (args[0].toLowerCase()) {
            case "items", "grounditems" -> {
                what = Messages.raw("commands.remove.what-items");
                removed = removeMatching(e -> e instanceof Item);
            }
            case "entities" -> {
                what = Messages.raw("commands.remove.what-entities");
                removed = removeMatching(e -> !(e instanceof Player));
            }
            case "mobs" -> {
                String filter = args.length >= 2 ? args[1].toLowerCase() : "all";
                switch (filter) {
                    case "all" -> {
                        what = Messages.raw("commands.remove.what-mobs");
                        removed = removeMatching(e -> e instanceof Mob);
                    }
                    case "hostile" -> {
                        what = Messages.raw("commands.remove.what-hostile");
                        removed = removeMatching(e -> e instanceof Mob && e instanceof Enemy);
                    }
                    case "neutral", "passive" -> {
                        what = Messages.raw("commands.remove.what-neutral");
                        removed = removeMatching(e -> e instanceof Mob && !(e instanceof Enemy));
                    }
                    default -> {
                        Messages.send(sender, "commands.remove.unknown-filter", "input", args[1]);
                        return true;
                    }
                }
            }
            default -> {
                return false;
            }
        }

        Messages.send(sender, "commands.remove.done", "count", String.valueOf(removed), "what", what);
        return true;
    }

    private int removeMatching(java.util.function.Predicate<Entity> predicate) {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (predicate.test(entity)) {
                    entity.remove();
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("items", "entities", "mobs").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mobs")) {
            return List.of("hostile", "neutral").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
