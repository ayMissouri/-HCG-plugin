package dev.amissouri.hcg;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class LoadedChunks implements Listener {

    private final Map<UUID, Set<Long>> byWorld = new ConcurrentHashMap<>();

    void seed(java.util.logging.Logger logger) {
        for (World world : Bukkit.getWorlds()) {
            try {
                for (Chunk chunk : world.getLoadedChunks()) {
                    keys(world).add(key(chunk.getX(), chunk.getZ()));
                }
            } catch (Throwable t) {
                logger.warning("Could not seed the loaded-chunk index for " + world.getName()
                        + " (" + t + "). Chunks already loaded will be missed by /remove until they"
                        + " unload and load again.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        byWorld.remove(event.getWorld().getUID());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(ChunkLoadEvent event) {
        keys(event.getWorld()).add(key(event.getChunk().getX(), event.getChunk().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(ChunkUnloadEvent event) {
        keys(event.getWorld()).remove(key(event.getChunk().getX(), event.getChunk().getZ()));
    }

    public long[] snapshot(World world) {
        return keys(world).stream().mapToLong(Long::longValue).toArray();
    }

    private Set<Long> keys(World world) {
        return byWorld.computeIfAbsent(world.getUID(), k -> ConcurrentHashMap.newKeySet());
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int chunkX(long key) {
        return (int) (key >> 32);
    }

    public static int chunkZ(long key) {
        return (int) key;
    }
}
