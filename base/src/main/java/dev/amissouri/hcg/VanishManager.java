package dev.amissouri.hcg;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class VanishManager {

    private final HCGPlugin plugin;
    private final HcgScheduler scheduler;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishManager(HCGPlugin plugin, HcgScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        if (vanished.remove(player.getUniqueId())) {
            show(player);
            return false;
        }
        vanished.add(player.getUniqueId());
        hide(player);
        return true;
    }

    void handleJoin(Player joiner) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != joiner && vanished.contains(online.getUniqueId())) {
                joiner.hidePlayer(plugin, online);
            }
        }
        if (isVanished(joiner)) {
            hide(joiner);
        }
    }

    private void hide(Player target) {
        Players.forEachOnline(scheduler, viewer -> {
            if (viewer != target) {
                viewer.hidePlayer(plugin, target);
            }
        });
    }

    private void show(Player target) {
        Players.forEachOnline(scheduler, viewer -> {
            if (viewer != target) {
                viewer.showPlayer(plugin, target);
            }
        });
    }
}
