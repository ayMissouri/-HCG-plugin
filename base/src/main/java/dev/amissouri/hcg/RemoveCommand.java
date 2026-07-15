package dev.amissouri.hcg;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

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

    private final HcgScheduler scheduler;
    private final LoadedChunks chunks;

    public RemoveCommand(HcgScheduler scheduler, LoadedChunks chunks) {
        this.scheduler = scheduler;
        this.chunks = chunks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        String what;
        Predicate<Entity> predicate;
        switch (args[0].toLowerCase()) {
            case "items", "grounditems" -> {
                what = Messages.raw("commands.remove.what-items");
                predicate = e -> e instanceof Item;
            }
            case "entities" -> {
                what = Messages.raw("commands.remove.what-entities");
                predicate = e -> !(e instanceof Player);
            }
            case "mobs" -> {
                String filter = args.length >= 2 ? args[1].toLowerCase() : "all";
                switch (filter) {
                    case "all" -> {
                        what = Messages.raw("commands.remove.what-mobs");
                        predicate = e -> e instanceof Mob;
                    }
                    case "hostile" -> {
                        what = Messages.raw("commands.remove.what-hostile");
                        predicate = e -> e instanceof Mob && e instanceof Enemy;
                    }
                    case "neutral", "passive" -> {
                        what = Messages.raw("commands.remove.what-neutral");
                        predicate = e -> e instanceof Mob && !(e instanceof Enemy);
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

        sweep(sender, predicate, what);
        return true;
    }

    private void sweep(CommandSender sender, Predicate<Entity> predicate, String what) {
        record Job(World world, int chunkX, int chunkZ) {
        }

        List<Job> jobs = Bukkit.getWorlds().stream()
                .flatMap(world -> Arrays.stream(chunks.snapshot(world))
                        .mapToObj(key -> new Job(world, LoadedChunks.chunkX(key), LoadedChunks.chunkZ(key))))
                .toList();
        if (jobs.isEmpty()) {
            report(sender, 0, what);
            return;
        }

        AtomicInteger removed = new AtomicInteger();
        AtomicInteger pending = new AtomicInteger(jobs.size());

        for (Job job : jobs) {
            scheduler.region(job.world(), job.chunkX(), job.chunkZ(), () -> {
                try {
                    if (job.world().isChunkLoaded(job.chunkX(), job.chunkZ())) {
                        for (Entity entity : job.world().getChunkAt(job.chunkX(), job.chunkZ()).getEntities()) {
                            if (predicate.test(entity)) {
                                entity.remove();
                                removed.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        scheduler.global(() -> report(sender, removed.get(), what));
                    }
                }
            });
        }
    }

    private void report(CommandSender sender, int removed, String what) {
        Messages.send(sender, "commands.remove.done", "count", String.valueOf(removed), "what", what);
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
