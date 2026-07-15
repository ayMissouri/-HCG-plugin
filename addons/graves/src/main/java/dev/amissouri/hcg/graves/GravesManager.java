package dev.amissouri.hcg.graves;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.amissouri.hcg.AsyncSaver;
import dev.amissouri.hcg.HcgScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GravesManager {

    public record Grave(String world, int x, int y, int z,
                        UUID owner, String ownerName, List<ItemStack> items, int xp) {

        String key() {
            return world + ";" + x + ";" + y + ";" + z;
        }
    }

    private static final BlockFace[] ROTATIONS = {
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST,
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST};

    private static final long SAVE_PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Grave> graves = new ConcurrentHashMap<>();
    private final AsyncSaver<String> saver;
    private volatile boolean enabled;

    public GravesManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "graves.yml");
        this.enabled = plugin.getConfig().getBoolean("graves.enabled", false);
        this.saver = new AsyncSaver<>(new HcgScheduler(plugin), SAVE_PERIOD_TICKS,
                this::snapshot, this::writeYaml);
    }

    void start() {
        saver.start();
    }

    void shutdown() {
        saver.flushNow();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("graves.enabled", enabled);
        plugin.saveConfig();
    }

    public int count() {
        return graves.size();
    }

    public Grave graveAt(Block block) {
        return block == null ? null : graves.get(keyOf(block));
    }

    public Block placeGrave(Player victim, List<ItemStack> items, int xp) {
        Block block = findSpot(victim.getLocation());
        if (block == null) {
            return null;
        }
        block.setType(Material.PLAYER_HEAD);
        if (block.getBlockData() instanceof Rotatable rotatable) {
            int index = Math.floorMod(Math.round((victim.getLocation().getYaw() + 180.0f) / 22.5f), 16);
            rotatable.setRotation(ROTATIONS[index]);
            block.setBlockData(rotatable);
        }
        if (block.getState() instanceof Skull skull) {
            skull.setOwningPlayer(victim);
            skull.update();
        }
        Grave grave = new Grave(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                victim.getUniqueId(), victim.getName(),
                items.stream().map(ItemStack::clone).toList(), xp);
        graves.put(grave.key(), grave);
        saver.markDirty();
        return block;
    }

    public Grave collect(Player owner, Block block) {
        Grave grave = graves.remove(keyOf(block));
        if (grave == null) {
            return null;
        }
        block.setType(Material.AIR);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack item : grave.items()) {
            for (ItemStack overflow : owner.getInventory().addItem(item.clone()).values()) {
                block.getWorld().dropItemNaturally(center, overflow);
            }
        }
        if (grave.xp() > 0) {
            owner.giveExp(grave.xp());
        }
        saver.markDirty();
        return grave;
    }

    public Grave forceRemove(Block block) {
        Grave grave = graves.remove(keyOf(block));
        if (grave == null) {
            return null;
        }
        block.setType(Material.AIR);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack item : grave.items()) {
            block.getWorld().dropItemNaturally(center, item.clone());
        }
        if (grave.xp() > 0) {
            block.getWorld().spawn(center, ExperienceOrb.class, orb -> orb.setExperience(grave.xp()));
        }
        saver.markDirty();
        return grave;
    }

    private Block findSpot(Location death) {
        World world = death.getWorld();
        int x = death.getBlockX();
        int z = death.getBlockZ();
        int y = Math.clamp(death.getBlockY(), world.getMinHeight(), world.getMaxHeight() - 1);
        for (int dy = y; dy < world.getMaxHeight(); dy++) {
            Block block = world.getBlockAt(x, dy, z);
            if (canReplace(block)) {
                return block;
            }
        }
        for (int dy = y - 1; dy >= world.getMinHeight(); dy--) {
            Block block = world.getBlockAt(x, dy, z);
            if (canReplace(block)) {
                return block;
            }
        }
        return null;
    }

    private boolean canReplace(Block block) {
        if (graves.containsKey(keyOf(block))) {
            return false;
        }
        Material type = block.getType();
        return type.isAir() || block.isLiquid() || Tag.REPLACEABLE.isTagged(type);
    }

    private static String keyOf(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    public static int totalXp(Player player) {
        int level = player.getLevel();
        int atLevel;
        if (level <= 16) {
            atLevel = level * level + 6 * level;
        } else if (level <= 31) {
            atLevel = (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            atLevel = (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        return atLevel + Math.round(player.getExp() * player.getExpToLevel());
    }

    void load() {
        graves.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("graves");
        if (section == null) {
            return;
        }
        for (String index : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(index);
            if (entry == null) {
                continue;
            }
            try {
                List<ItemStack> items = new ArrayList<>();
                for (Object item : entry.getList("items", List.of())) {
                    if (item instanceof ItemStack stack) {
                        items.add(stack);
                    }
                }
                Grave grave = new Grave(
                        entry.getString("world"),
                        entry.getInt("x"), entry.getInt("y"), entry.getInt("z"),
                        UUID.fromString(entry.getString("owner", "")),
                        entry.getString("owner-name", "unknown"),
                        List.copyOf(items),
                        entry.getInt("xp"));
                graves.put(grave.key(), grave);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping invalid grave entry #" + index + " in graves.yml");
            }
        }
    }

    private String snapshot() {
        YamlConfiguration config = new YamlConfiguration();
        int index = 0;
        for (Grave grave : graves.values()) {
            ConfigurationSection entry = config.createSection("graves." + index++);
            entry.set("world", grave.world());
            entry.set("x", grave.x());
            entry.set("y", grave.y());
            entry.set("z", grave.z());
            entry.set("owner", grave.owner().toString());
            entry.set("owner-name", grave.ownerName());
            entry.set("xp", grave.xp());
            entry.set("items", grave.items());
        }
        return config.saveToString();
    }

    private void writeYaml(String yaml) {
        try {
            Files.createDirectories(file.toPath().getParent());
            Files.writeString(file.toPath(), yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save graves.yml: " + e.getMessage());
        }
    }
}
