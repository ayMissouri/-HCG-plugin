package dev.amissouri.hcg.tweaks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import dev.amissouri.hcg.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class VeinminerTweak implements Tweak {

    public enum Mode {
        SHIFT,
        ENCHANT,
        BOTH
    }

    public enum Durability {
        PER_BLOCK,
        SINGLE
    }

    private static final String PATH = "tweaks.veinminer.";
    private static final int[] SIZE_STEPS = {8, 16, 32, 64, 128, 256};
    private static final int[] CHANCE_STEPS = {5, 10, 25, 33, 50, 75, 100};
    private static final int[] MIN_LEVEL_STEPS = {1, 5, 10, 15, 20, 25, 30};
    private static final int[][] NEIGHBOURS = neighbourOffsets();

    private static final List<String> DEFAULT_BLOCKS = List.of(
            "COAL_ORE", "DEEPSLATE_COAL_ORE",
            "IRON_ORE", "DEEPSLATE_IRON_ORE",
            "COPPER_ORE", "DEEPSLATE_COPPER_ORE",
            "GOLD_ORE", "DEEPSLATE_GOLD_ORE",
            "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE",
            "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE",
            "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE",
            "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE",
            "NETHER_GOLD_ORE", "NETHER_QUARTZ_ORE", "ANCIENT_DEBRIS");

    private static final List<String> DEFAULT_TOOLS = List.of(
            "WOODEN_PICKAXE", "STONE_PICKAXE", "IRON_PICKAXE",
            "GOLDEN_PICKAXE", "DIAMOND_PICKAXE", "NETHERITE_PICKAXE");

    private final JavaPlugin plugin;
    private final VeinminerEnchant enchant;

    private volatile boolean enabled;
    private volatile Mode mode;
    private volatile boolean requireSneak;
    private volatile boolean requireCorrectTool;
    private volatile int maxVeinSize;
    private volatile boolean hungerEnabled;
    private volatile float hungerPerBlock;
    private volatile Durability durability;
    private volatile int enchantChance;
    private volatile int enchantMinLevel;
    private volatile Set<Material> blocks;
    private volatile Set<Material> tools;

    public VeinminerTweak(JavaPlugin plugin, VeinminerEnchant enchant) {
        this.plugin = plugin;
        this.enchant = enchant;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean(PATH + "enabled", false);
        mode = parse(config.getString(PATH + "mode"), Mode.class, Mode.SHIFT);
        requireSneak = config.getBoolean(PATH + "enchant.require-sneak", true);
        requireCorrectTool = config.getBoolean(PATH + "require-correct-tool", true);
        maxVeinSize = Math.clamp(config.getInt(PATH + "max-vein-size", 64), 1, 4096);
        hungerEnabled = config.getBoolean(PATH + "hunger.enabled", true);
        hungerPerBlock = (float) Math.clamp(
                config.getDouble(PATH + "hunger.exhaustion-per-block", 0.05), 0.0, 40.0);
        durability = parse(config.getString(PATH + "durability.mode"), Durability.class,
                Durability.PER_BLOCK);
        enchantChance = Math.clamp(config.getInt(PATH + "enchant.chance", 25), 0, 100);
        enchantMinLevel = Math.clamp(config.getInt(PATH + "enchant.min-level", 15), 1, 30);
        blocks = materials(config.contains(PATH + "blocks")
                ? config.getStringList(PATH + "blocks") : DEFAULT_BLOCKS, "block");
        tools = materials(config.contains(PATH + "enchant.tools")
                ? config.getStringList(PATH + "enchant.tools") : DEFAULT_TOOLS, "tool");
    }

    @Override
    public String id() {
        return "veinminer";
    }

    @Override
    public String displayName() {
        return Messages.raw("tweaks.veinminer.name");
    }

    @Override
    public Material icon() {
        return Material.DIAMOND_PICKAXE;
    }

    @Override
    public List<String> summary() {
        return List.of(Messages.raw("tweaks.veinminer.summary"));
    }

    @Override
    public String command() {
        return "/veinminer";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        enabled = value;
        write("enabled", value);
    }

    @Override
    public List<Setting> settings() {
        return List.of(
                new Setting.Of("Mode", Material.LEVER,
                        () -> mode.name().replace('_', ' '),
                        forward -> setMode(Setting.step(mode, Mode.values(), forward)),
                        List.of("How players turn veinmining on:",
                                "SHIFT - anyone, while sneaking",
                                "ENCHANT - only an enchanted tool",
                                "BOTH - either one")),
                new Setting.Of("Sneak With Enchant", Material.LEATHER_BOOTS,
                        () -> onOff(requireSneak),
                        forward -> setRequireSneak(!requireSneak),
                        List.of("Whether an enchanted tool must also",
                                "sneak. Off means it always veinmines.",
                                "Unused in SHIFT mode.")),
                new Setting.Of("Hunger Cost", Material.COOKED_BEEF,
                        () -> onOff(hungerEnabled),
                        forward -> setHungerEnabled(!hungerEnabled),
                        List.of("Whether the extra blocks add",
                                "exhaustion, draining hunger.")),
                new Setting.Of("Durability Cost", Material.ANVIL,
                        () -> durability.name().replace('_', ' '),
                        forward -> setDurability(Setting.step(durability, Durability.values(), forward)),
                        List.of("PER BLOCK - one point per ore mined",
                                "SINGLE - one point total, as if the",
                                "player had broken a single block")),
                new Setting.Of("Max Vein Size", Material.CHAIN,
                        () -> String.valueOf(maxVeinSize),
                        forward -> setMaxVeinSize(Setting.step(maxVeinSize, SIZE_STEPS, forward)),
                        List.of("The most extra blocks one break",
                                "may take out.")),
                new Setting.Of("Require Correct Tool", Material.IRON_PICKAXE,
                        () -> onOff(requireCorrectTool),
                        forward -> setRequireCorrectTool(!requireCorrectTool),
                        List.of("Only veinmine with a tool that",
                                "actually drops the ore. Off lets a",
                                "wooden pick destroy a diamond vein.")),
                new Setting.Of("Enchant Chance", Material.ENCHANTING_TABLE,
                        () -> enchantChance + "%",
                        forward -> setEnchantChance(Setting.step(enchantChance, CHANCE_STEPS, forward)),
                        List.of("Chance an enchanting table adds",
                                "Veinminer to a tool.",
                                "Unused in SHIFT mode.")),
                new Setting.Of("Enchant Min Level", Material.EXPERIENCE_BOTTLE,
                        () -> String.valueOf(enchantMinLevel),
                        forward -> setEnchantMinLevel(Setting.step(enchantMinLevel, MIN_LEVEL_STEPS, forward)),
                        List.of("The enchanting table option must",
                                "cost at least this many levels",
                                "before Veinminer can roll.")));
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode value) {
        mode = value;
        write("mode", value.name().toLowerCase(Locale.ROOT));
    }

    public boolean requireSneak() {
        return requireSneak;
    }

    public void setRequireSneak(boolean value) {
        requireSneak = value;
        write("enchant.require-sneak", value);
    }

    public boolean hungerEnabled() {
        return hungerEnabled;
    }

    public void setHungerEnabled(boolean value) {
        hungerEnabled = value;
        write("hunger.enabled", value);
    }

    public Durability durability() {
        return durability;
    }

    public void setDurability(Durability value) {
        durability = value;
        write("durability.mode", value.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    public int maxVeinSize() {
        return maxVeinSize;
    }

    public void setMaxVeinSize(int value) {
        maxVeinSize = Math.clamp(value, 1, 4096);
        write("max-vein-size", maxVeinSize);
    }

    public boolean requireCorrectTool() {
        return requireCorrectTool;
    }

    public void setRequireCorrectTool(boolean value) {
        requireCorrectTool = value;
        write("require-correct-tool", value);
    }

    public int enchantChance() {
        return enchantChance;
    }

    public void setEnchantChance(int value) {
        enchantChance = Math.clamp(value, 0, 100);
        write("enchant.chance", enchantChance);
    }

    public int enchantMinLevel() {
        return enchantMinLevel;
    }

    public void setEnchantMinLevel(int value) {
        enchantMinLevel = Math.clamp(value, 1, 30);
        write("enchant.min-level", enchantMinLevel);
    }

    boolean shouldTrigger(Player player, Block block, ItemStack tool) {
        if (!enabled || !blocks.contains(block.getType())
                || !player.hasPermission("hcg.veinminer.use")) {
            return false;
        }
        if (requireCorrectTool && !block.isValidTool(tool)) {
            return false;
        }
        boolean sneaking = player.isSneaking();
        return switch (mode) {
            case SHIFT -> sneaking;
            case ENCHANT -> enchant.has(tool) && (sneaking || !requireSneak);
            case BOTH -> sneaking || (enchant.has(tool) && !requireSneak);
        };
    }

    int mine(Player player, Block origin, ItemStack held) {
        List<Block> vein = findVein(origin);
        if (vein.isEmpty()) {
            return 0;
        }
        boolean survival = player.getGameMode() == GameMode.SURVIVAL
                || player.getGameMode() == GameMode.ADVENTURE;
        ItemStack tool = held;
        int broken = 0;
        for (Block block : vein) {
            BlockBreakEvent event = new BlockBreakEvent(block, player);
            event.setDropItems(survival);
            if (!event.callEvent()) {
                continue;
            }
            if (event.isDropItems()) {
                block.breakNaturally(tool, true, true);
            } else {
                block.setType(Material.AIR);
            }
            broken++;
            if (survival && durability == Durability.PER_BLOCK && isDamageable(tool)) {
                tool = damage(player, tool);
                if (tool.getType().isAir()) {
                    break;
                }
            }
        }
        if (survival && hungerEnabled && broken > 0) {
            player.setExhaustion(player.getExhaustion() + hungerPerBlock * broken);
        }
        return broken;
    }

    private List<Block> findVein(Block origin) {
        Material type = origin.getType();
        List<Block> found = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < maxVeinSize) {
            Block current = queue.poll();
            for (int[] offset : NEIGHBOURS) {
                if (found.size() >= maxVeinSize) {
                    break;
                }
                Block next = current.getRelative(offset[0], offset[1], offset[2]);
                if (!seen.add(next)
                        || !Bukkit.isOwnedByCurrentRegion(next)
                        || !next.getWorld().isChunkLoaded(next.getX() >> 4, next.getZ() >> 4)
                        || next.getType() != type) {
                    continue;
                }
                found.add(next);
                queue.add(next);
            }
        }
        return found;
    }

    private static boolean isDamageable(ItemStack tool) {
        return tool != null && !tool.getType().isAir() && tool.getType().getMaxDurability() > 0;
    }

    private ItemStack damage(Player player, ItemStack tool) {
        ItemStack result = tool.damage(1, player);
        player.getInventory().setItemInMainHand(result);
        return result;
    }


    int rollEnchant(ItemStack item, int cost) {
        if (!enabled || mode == Mode.SHIFT || item == null || !tools.contains(item.getType())
                || cost < enchantMinLevel || enchant.has(item)) {
            return 0;
        }
        return ThreadLocalRandom.current().nextInt(100) < enchantChance ? 1 : 0;
    }

    boolean isEnchantable(ItemStack item) {
        return item != null && tools.contains(item.getType());
    }

    private void write(String key, Object value) {
        plugin.getConfig().set(PATH + key, value);
        plugin.saveConfig();
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private <E extends Enum<E>> E parse(String raw, Class<E> type, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown veinminer " + type.getSimpleName().toLowerCase(Locale.ROOT)
                    + " '" + raw + "' in config.yml, using " + fallback.name().toLowerCase(Locale.ROOT) + ".");
            return fallback;
        }
    }

    private Set<Material> materials(List<String> names, String what) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                plugin.getLogger().warning("Ignoring unknown veinminer " + what + " '" + name
                        + "' in config.yml.");
                continue;
            }
            set.add(material);
        }
        return set;
    }

    private static int[][] neighbourOffsets() {
        int[][] offsets = new int[26][];
        int index = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        offsets[index++] = new int[] {x, y, z};
                    }
                }
            }
        }
        return offsets;
    }
}
