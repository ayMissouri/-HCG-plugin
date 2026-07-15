package dev.amissouri.hcg.lavaraise;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import dev.amissouri.hcg.AsyncSaver;
import dev.amissouri.hcg.HcgScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Remembers burnable blocks placed by players in the event world, so the rising lava can burn player builds
 */
public final class LavaBurnTracker implements Listener {

    private static final long SAVE_PERIOD_TICKS = 6000L;

    private final JavaPlugin plugin;
    private final LavaRaiseManager manager;
    private final Map<Integer, Set<Long>> byY = new HashMap<>();
    private final AsyncSaver<byte[]> saver;

    public LavaBurnTracker(JavaPlugin plugin, LavaRaiseManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        load();
        this.saver = new AsyncSaver<>(new HcgScheduler(plugin), SAVE_PERIOD_TICKS,
                this::snapshot, this::writeBytes);
        this.saver.start();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!block.getType().isBurnable() || !block.getWorld().equals(manager.eventWorld())) {
            return;
        }
        if (manager.isBlockInLava(block)) {
            // Placed straight into the lava, burns immediately.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (block.getType().isBurnable() && manager.isBlockInLava(block)) {
                    burn(block);
                }
            });
            return;
        }
        byY.computeIfAbsent(block.getY(), k -> new HashSet<>()).add(pack(block.getX(), block.getZ()));
        saver.markDirty();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Set<Long> set = byY.get(event.getBlock().getY());
        if (set != null && set.remove(pack(event.getBlock().getX(), event.getBlock().getZ()))) {
            saver.markDirty();
        }
    }

    public void burnRange(int fromY, int toY) {
        boolean removedAny = false;
        for (int y = fromY; y <= toY; y++) {
            Set<Long> set = byY.get(y);
            if (set == null || set.isEmpty()) {
                continue;
            }
            Iterator<Long> iterator = set.iterator();
            while (iterator.hasNext()) {
                long key = iterator.next();
                int x = (int) (key >> 32);
                int z = (int) key;
                if (!manager.inRegionXZ(x, z)) {
                    continue; // outside the event, keep tracking it
                }
                if (!manager.eventWorld().isChunkLoaded(x >> 4, z >> 4)) {
                    continue; // not loaded, survives this event, stays tracked
                }
                Block block = manager.eventWorld().getBlockAt(x, y, z);
                if (block.getType().isBurnable()) {
                    burn(block);
                }
                iterator.remove();
                removedAny = true;
            }
        }
        if (removedAny) {
            saver.markDirty();
        }
    }

    private void burn(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.LAVA, center, 10, 0.3, 0.3, 0.3);
        block.getWorld().playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.0f);
        block.setType(Material.AIR, false);
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "placed-burnables.dat");
    }

    public void shutdown() {
        saver.flushNow();
    }

    private byte[] snapshot() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            int count = byY.values().stream().mapToInt(Set::size).sum();
            out.writeInt(count);
            for (Map.Entry<Integer, Set<Long>> entry : byY.entrySet()) {
                for (long key : entry.getValue()) {
                    out.writeInt((int) (key >> 32));
                    out.writeInt(entry.getKey());
                    out.writeInt((int) key);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private void writeBytes(byte[] data) {
        try {
            Path path = dataFile().toPath();
            Files.createDirectories(path.getParent());
            Files.write(path, data);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save placed-burnables.dat: " + e.getMessage());
        }
    }

    private void load() {
        File file = dataFile();
        if (!file.exists()) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                byY.computeIfAbsent(y, k -> new HashSet<>()).add(pack(x, z));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load placed-burnables.dat: " + e.getMessage());
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
