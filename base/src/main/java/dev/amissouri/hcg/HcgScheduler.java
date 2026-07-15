package dev.amissouri.hcg;

import java.util.concurrent.TimeUnit;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class HcgScheduler {

    private final Plugin plugin;

    public HcgScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    private static long atLeastOneTick(long ticks) {
        return Math.max(1L, ticks);
    }

    public void global(Runnable body) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, body);
    }

    public ScheduledTask globalDelayed(Runnable body, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler()
                .runDelayed(plugin, task -> body.run(), atLeastOneTick(delayTicks));
    }

    public ScheduledTask globalTimer(Runnable body, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, task -> body.run(),
                        atLeastOneTick(delayTicks), atLeastOneTick(periodTicks));
    }

    public void region(Location location, Runnable body) {
        Bukkit.getRegionScheduler().execute(plugin, location, body);
    }

    public void region(World world, int chunkX, int chunkZ, Runnable body) {
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, body);
    }

    public ScheduledTask regionTimer(Location location, Runnable body, long delayTicks, long periodTicks) {
        return Bukkit.getRegionScheduler()
                .runAtFixedRate(plugin, location, task -> body.run(),
                        atLeastOneTick(delayTicks), atLeastOneTick(periodTicks));
    }

    public ScheduledTask entity(Entity entity, Runnable body) {
        return entity.getScheduler().run(plugin, task -> body.run(), null);
    }

    public ScheduledTask entityOrDrop(Entity entity, Runnable body, Runnable retired) {
        return entity.getScheduler().run(plugin, task -> body.run(), retired);
    }

    public ScheduledTask entityDelayed(Entity entity, Runnable body, long delayTicks) {
        return entity.getScheduler()
                .runDelayed(plugin, task -> body.run(), null, atLeastOneTick(delayTicks));
    }

    public ScheduledTask entityTimer(Entity entity, Runnable body, long delayTicks, long periodTicks) {
        return entity.getScheduler()
                .runAtFixedRate(plugin, task -> body.run(), null,
                        atLeastOneTick(delayTicks), atLeastOneTick(periodTicks));
    }

    public ScheduledTask async(Runnable body) {
        return Bukkit.getAsyncScheduler().runNow(plugin, task -> body.run());
    }

    public ScheduledTask asyncTimer(Runnable body, long delayTicks, long periodTicks) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> body.run(),
                atLeastOneTick(delayTicks) * 50L, atLeastOneTick(periodTicks) * 50L,
                TimeUnit.MILLISECONDS);
    }

    public static void cancel(ScheduledTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
