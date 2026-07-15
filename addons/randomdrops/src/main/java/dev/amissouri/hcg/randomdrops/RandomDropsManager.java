package dev.amissouri.hcg.randomdrops;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RandomDropsManager {

    public enum Mode { DYNAMIC, STATIC }

    private static final Set<Material> EXCLUDED = EnumSet.of(
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.BARRIER,
            Material.LIGHT,
            Material.DEBUG_STICK,
            Material.KNOWLEDGE_BOOK,
            Material.SPAWNER,
            Material.TRIAL_SPAWNER,
            Material.VAULT,
            Material.BEDROCK,
            Material.END_PORTAL_FRAME,
            Material.REINFORCED_DEEPSLATE,
            Material.PETRIFIED_OAK_SLAB,
            Material.FARMLAND,
            Material.FROGSPAWN);

    private final JavaPlugin plugin;
    private final List<Material> pool;
    private final List<Enchantment> enchantments;

    private volatile Map<Material, Material> staticDrops = Map.of();
    private volatile Map<EntityType, Material> staticMobDrops = Map.of();
    private volatile boolean enabled;
    private volatile Mode mode;
    private volatile boolean enchanted;
    private volatile boolean mobs;

    public RandomDropsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pool = Arrays.stream(Material.values())
                .filter(m -> !m.isLegacy() && m.isItem() && !m.isAir())
                .filter(m -> !EXCLUDED.contains(m))
                .filter(m -> !m.name().endsWith("_SPAWN_EGG"))
                .toList();
        this.enchantments = Registry.ENCHANTMENT.stream().toList();
        this.enabled = plugin.getConfig().getBoolean("random-drops.enabled", false);
        this.mode = "static".equalsIgnoreCase(plugin.getConfig().getString("random-drops.mode", "dynamic"))
                ? Mode.STATIC : Mode.DYNAMIC;
        this.enchanted = plugin.getConfig().getBoolean("random-drops.enchanted", false);
        this.mobs = plugin.getConfig().getBoolean("random-drops.mobs", false);

        if (!plugin.getConfig().contains("random-drops.static-seed")) {
            plugin.getConfig().set("random-drops.static-seed", ThreadLocalRandom.current().nextLong());
            plugin.saveConfig();
        }
        rebuildStaticDrops();
    }

    private void rebuildStaticDrops() {
        long seed = plugin.getConfig().getLong("random-drops.static-seed");
        Random random = new Random(seed);
        Map<Material, Material> drops = new HashMap<>();
        for (Material material : Material.values()) {
            if (!material.isLegacy() && material.isBlock() && !material.isAir()) {
                drops.put(material, pool.get(random.nextInt(pool.size())));
            }
        }
        Random mobRandom = new Random(seed ^ 0x9E3779B97F4A7C15L);
        Map<EntityType, Material> mobDrops = new HashMap<>();
        for (EntityType type : EntityType.values()) {
            if (type.isAlive() && type != EntityType.PLAYER) {
                mobDrops.put(type, pool.get(mobRandom.nextInt(pool.size())));
            }
        }
        staticDrops = Map.copyOf(drops);
        staticMobDrops = Map.copyOf(mobDrops);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Mode mode() {
        return mode;
    }

    public int poolSize() {
        return pool.size();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("random-drops.enabled", enabled);
        plugin.saveConfig();
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        plugin.getConfig().set("random-drops.mode", mode.name().toLowerCase());
        plugin.saveConfig();
    }

    public void reroll() {
        plugin.getConfig().set("random-drops.static-seed", ThreadLocalRandom.current().nextLong());
        plugin.saveConfig();
        rebuildStaticDrops();
    }

    public boolean isEnchanted() {
        return enchanted;
    }

    public void setEnchanted(boolean enchanted) {
        this.enchanted = enchanted;
        plugin.getConfig().set("random-drops.enchanted", enchanted);
        plugin.saveConfig();
    }

    public boolean isMobsEnabled() {
        return mobs;
    }

    public void setMobsEnabled(boolean mobs) {
        this.mobs = mobs;
        plugin.getConfig().set("random-drops.mobs", mobs);
        plugin.saveConfig();
    }

    public ItemStack createDrop(Material broken) {
        if (mode == Mode.STATIC) {
            Material fixed = staticDrops.get(broken);
            if (fixed != null) {
                return buildStack(fixed);
            }
        }
        return buildStack(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
    }

    public ItemStack createMobDrop(EntityType type) {
        if (mode == Mode.STATIC) {
            Material fixed = staticMobDrops.get(type);
            if (fixed != null) {
                return buildStack(fixed);
            }
        }
        return buildStack(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
    }

    private ItemStack buildStack(Material material) {
        ItemStack stack = new ItemStack(material);
        if (!enchanted) {
            return stack;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            Enchantment enchantment = enchantments.get(random.nextInt(enchantments.size()));
            int level = 1 + random.nextInt(enchantment.getMaxLevel());
            meta.addEnchant(enchantment, level, true);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
