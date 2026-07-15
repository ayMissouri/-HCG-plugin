package dev.amissouri.hcg;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class Players {

    private Players() {
    }

    public static void forEachOnline(HcgScheduler scheduler, Consumer<Player> body) {
        forEach(scheduler, Bukkit.getOnlinePlayers(), body);
    }

    public static void forEach(HcgScheduler scheduler, Collection<? extends Player> players,
            Consumer<Player> body) {
        for (Player player : List.copyOf(players)) {
            scheduler.entity(player, () -> body.accept(player));
        }
    }

    public static void forEach(HcgScheduler scheduler, Collection<? extends Player> players,
            Consumer<Player> body, Runnable whenAllDone) {
        List<? extends Player> snapshot = List.copyOf(players);
        if (snapshot.isEmpty()) {
            scheduler.global(whenAllDone);
            return;
        }
        AtomicInteger pending = new AtomicInteger(snapshot.size());
        Runnable settle = () -> {
            if (pending.decrementAndGet() == 0) {
                scheduler.global(whenAllDone);
            }
        };
        for (Player player : snapshot) {
            ScheduledTask task = scheduler.entityOrDrop(player, () -> {
                try {
                    body.accept(player);
                } finally {
                    settle.run();
                }
            }, settle);
            if (task == null) {
                settle.run();
            }
        }
    }
}
