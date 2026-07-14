package dev.amissouri.hcg;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

/** Tracks which players are currently frozen. */
public final class FreezeManager {

    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public boolean isFrozen(Player player) {
        return frozen.contains(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        if (frozen.remove(player.getUniqueId())) {
            return false;
        }
        frozen.add(player.getUniqueId());
        return true;
    }

    public void freeze(Player player) {
        frozen.add(player.getUniqueId());
    }

    public void unfreeze(Player player) {
        frozen.remove(player.getUniqueId());
    }

    public void clear() {
        frozen.clear();
    }
}
