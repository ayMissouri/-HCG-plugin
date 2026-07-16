package dev.amissouri.hcg.tweaks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class TreecapitatorTweak implements Tweak {

    public enum Mode {
        SHIFT,
        ENCHANT,
        BOTH
    }

    public enum Scope {
        WHOLE_TREE,
        ABOVE
    }

    public enum Durability {
        PER_BLOCK,
        SINGLE
    }

    private static final String PATH = "tweaks.treecapitator.";
    private static final int[] SIZE_STEPS = {16, 32, 64, 128, 256, 512};
    private static final int[] CHANCE_STEPS = {5, 10, 25, 33, 50, 75, 100};
    private static final int[] MIN_LEVEL_STEPS = {1, 5, 10, 15, 20, 25, 30};
    private static final int[][] NEIGHBOURS = neighbourOffsets();
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int LEAF_RANGE = 7;
    private static final int MAX_LEAVES = 4096;
    private static final int DECAY_WAVES = 50;
    private static final long DECAY_WAVE_TICKS = 2L;

    private static final List<String> DEFAULT_TOOLS = List.of(
            "WOODEN_AXE", "STONE_AXE", "IRON_AXE",
            "GOLDEN_AXE", "DIAMOND_AXE", "NETHERITE_AXE");

    private final JavaPlugin plugin;
    private final TweakEnchant enchant;
    private final HcgScheduler scheduler;
    private final NamespacedKey fallKey;

    private volatile boolean enabled;
    private volatile Mode mode;
    private volatile boolean requireSneak;
    private volatile Scope scope;
    private volatile boolean animation;
    private volatile boolean fastLeafDecay;
    private volatile boolean replantSapling;
    private volatile boolean requireLeaves;
    private volatile boolean requireAxe;
    private volatile int maxTreeSize;
    private volatile boolean hungerEnabled;
    private volatile float hungerPerBlock;
    private volatile Durability durability;
    private volatile int enchantChance;
    private volatile int enchantMinLevel;
    private volatile Set<Material> blocks;
    private volatile Set<Material> tools;

    public TreecapitatorTweak(JavaPlugin plugin, TweakEnchant enchant, HcgScheduler scheduler) {
        this.plugin = plugin;
        this.enchant = enchant;
        this.scheduler = scheduler;
        this.fallKey = new NamespacedKey(plugin, "treecapitator-fall");
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean(PATH + "enabled", false);
        mode = parse(config.getString(PATH + "mode"), Mode.class, Mode.SHIFT);
        requireSneak = config.getBoolean(PATH + "enchant.require-sneak", true);
        scope = parse(config.getString(PATH + "scope"), Scope.class, Scope.WHOLE_TREE);
        animation = config.getBoolean(PATH + "animation", true);
        fastLeafDecay = config.getBoolean(PATH + "fast-leaf-decay", true);
        replantSapling = config.getBoolean(PATH + "replant-sapling", true);
        requireLeaves = config.getBoolean(PATH + "require-leaves", true);
        requireAxe = config.getBoolean(PATH + "require-axe", true);
        maxTreeSize = Math.clamp(config.getInt(PATH + "max-tree-size", 128), 1, 4096);
        hungerEnabled = config.getBoolean(PATH + "hunger.enabled", true);
        hungerPerBlock = (float) Math.clamp(
                config.getDouble(PATH + "hunger.exhaustion-per-block", 0.05), 0.0, 40.0);
        durability = parse(config.getString(PATH + "durability.mode"), Durability.class,
                Durability.PER_BLOCK);
        enchantChance = Math.clamp(config.getInt(PATH + "enchant.chance", 25), 0, 100);
        enchantMinLevel = Math.clamp(config.getInt(PATH + "enchant.min-level", 15), 1, 30);
        blocks = config.contains(PATH + "blocks")
                ? materials(config.getStringList(PATH + "blocks"), "block")
                : EnumSet.copyOf(Tag.LOGS.getValues());
        tools = materials(config.contains(PATH + "enchant.tools")
                ? config.getStringList(PATH + "enchant.tools") : DEFAULT_TOOLS, "tool");
    }

    @Override
    public String id() {
        return "treecapitator";
    }

    @Override
    public String displayName() {
        return Messages.raw("tweaks.treecapitator.name");
    }

    @Override
    public Material icon() {
        return Material.DIAMOND_AXE;
    }

    @Override
    public List<String> summary() {
        return List.of(Messages.raw("tweaks.treecapitator.summary"));
    }

    @Override
    public String command() {
        return "/treecapitator";
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
                        List.of("How players fell trees:",
                                "SHIFT - anyone, while sneaking",
                                "ENCHANT - only an enchanted axe",
                                "BOTH - either one")),
                new Setting.Of("Sneak With Enchant", Material.LEATHER_BOOTS,
                        () -> onOff(requireSneak),
                        forward -> setRequireSneak(!requireSneak),
                        List.of("Whether an enchanted axe must also",
                                "sneak. Off means it always fells.",
                                "Unused in SHIFT mode.")),
                new Setting.Of("Fell Scope", Material.LADDER,
                        () -> scope.name().replace('_', ' '),
                        forward -> setScope(Setting.step(scope, Scope.values(), forward)),
                        List.of("WHOLE TREE - every connected log,",
                                "no matter which one was broken",
                                "ABOVE - only the logs from the",
                                "broken block upward")),
                new Setting.Of("Falling Animation", Material.SAND,
                        () -> onOff(animation),
                        forward -> setAnimation(!animation),
                        List.of("Topple the logs as falling blocks",
                                "that drop their wood where they",
                                "land, instead of breaking in place.")),
                new Setting.Of("Fast Leaf Decay", Material.SHEARS,
                        () -> onOff(fastLeafDecay),
                        forward -> setFastLeafDecay(!fastLeafDecay),
                        List.of("Break the felled tree's leaves",
                                "within seconds instead of letting",
                                "them decay slowly.")),
                new Setting.Of("Replant Sapling", Material.OAK_SAPLING,
                        () -> onOff(replantSapling),
                        forward -> setReplantSapling(!replantSapling),
                        List.of("Plant a matching sapling on the",
                                "soil where the trunk stood.")),
                new Setting.Of("Require Leaves", Material.OAK_LEAVES,
                        () -> onOff(requireLeaves),
                        forward -> setRequireLeaves(!requireLeaves),
                        List.of("Only fell when the trunk touches",
                                "natural leaves, so log builds",
                                "are safe.")),
                new Setting.Of("Require Axe", Material.IRON_AXE,
                        () -> onOff(requireAxe),
                        forward -> setRequireAxe(!requireAxe),
                        List.of("Only fell when the log was",
                                "broken with an axe.")),
                new Setting.Of("Hunger Cost", Material.COOKED_BEEF,
                        () -> onOff(hungerEnabled),
                        forward -> setHungerEnabled(!hungerEnabled),
                        List.of("Whether the extra logs add",
                                "exhaustion, draining hunger.")),
                new Setting.Of("Durability Cost", Material.ANVIL,
                        () -> durability.name().replace('_', ' '),
                        forward -> setDurability(Setting.step(durability, Durability.values(), forward)),
                        List.of("PER BLOCK - one point per log felled",
                                "SINGLE - one point total, as if the",
                                "player had broken a single block")),
                new Setting.Of("Max Tree Size", Material.CHAIN,
                        () -> String.valueOf(maxTreeSize),
                        forward -> setMaxTreeSize(Setting.step(maxTreeSize, SIZE_STEPS, forward)),
                        List.of("The most extra logs one break",
                                "may fell.")),
                new Setting.Of("Enchant Chance", Material.ENCHANTING_TABLE,
                        () -> enchantChance + "%",
                        forward -> setEnchantChance(Setting.step(enchantChance, CHANCE_STEPS, forward)),
                        List.of("Chance an enchanting table adds",
                                "Treecapitator to an axe.",
                                "Unused in SHIFT mode.")),
                new Setting.Of("Enchant Min Level", Material.EXPERIENCE_BOTTLE,
                        () -> String.valueOf(enchantMinLevel),
                        forward -> setEnchantMinLevel(Setting.step(enchantMinLevel, MIN_LEVEL_STEPS, forward)),
                        List.of("The enchanting table option must",
                                "cost at least this many levels",
                                "before Treecapitator can roll.")));
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

    public Scope scope() {
        return scope;
    }

    public void setScope(Scope value) {
        scope = value;
        write("scope", value.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    public boolean animation() {
        return animation;
    }

    public void setAnimation(boolean value) {
        animation = value;
        write("animation", value);
    }

    public boolean fastLeafDecay() {
        return fastLeafDecay;
    }

    public void setFastLeafDecay(boolean value) {
        fastLeafDecay = value;
        write("fast-leaf-decay", value);
    }

    public boolean replantSapling() {
        return replantSapling;
    }

    public void setReplantSapling(boolean value) {
        replantSapling = value;
        write("replant-sapling", value);
    }

    public boolean requireLeaves() {
        return requireLeaves;
    }

    public void setRequireLeaves(boolean value) {
        requireLeaves = value;
        write("require-leaves", value);
    }

    public boolean requireAxe() {
        return requireAxe;
    }

    public void setRequireAxe(boolean value) {
        requireAxe = value;
        write("require-axe", value);
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

    public int maxTreeSize() {
        return maxTreeSize;
    }

    public void setMaxTreeSize(int value) {
        maxTreeSize = Math.clamp(value, 1, 4096);
        write("max-tree-size", maxTreeSize);
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
                || !player.hasPermission("hcg.treecapitator.use")) {
            return false;
        }
        if (requireAxe && (tool == null || !Tag.ITEMS_AXES.isTagged(tool.getType()))) {
            return false;
        }
        boolean sneaking = player.isSneaking();
        return switch (mode) {
            case SHIFT -> sneaking;
            case ENCHANT -> enchant.has(tool) && (sneaking || !requireSneak);
            case BOTH -> sneaking || (enchant.has(tool) && !requireSneak);
        };
    }

    int fell(Player player, Block origin, ItemStack held) {
        List<Block> tree = findTree(origin);
        if (tree.isEmpty()) {
            return 0;
        }
        boolean survival = player.getGameMode() == GameMode.SURVIVAL
                || player.getGameMode() == GameMode.ADVENTURE;
        Vector direction = fallDirection(player);
        int originY = origin.getY();
        ItemStack tool = held;
        int felled = 0;
        List<Replant> replants = new ArrayList<>();
        addReplant(replants, origin);
        for (Block block : tree) {
            BlockBreakEvent event = new BlockBreakEvent(block, player);
            event.setDropItems(survival);
            if (!event.callEvent()) {
                continue;
            }
            addReplant(replants, block);
            if (animation) {
                topple(block, originY, direction, event.isDropItems());
            } else if (event.isDropItems()) {
                block.breakNaturally(tool, true, true);
            } else {
                block.setType(Material.AIR);
            }
            felled++;
            if (survival && durability == Durability.PER_BLOCK && isDamageable(tool)) {
                tool = damage(player, tool);
                if (tool.getType().isAir()) {
                    break;
                }
            }
        }
        if (felled > 0) {
            if (animation) {
                origin.getWorld().playSound(origin.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.0f, 0.8f);
            }
            if (fastLeafDecay) {
                List<Block> leaves = findLeaves(origin, tree);
                if (!leaves.isEmpty()) {
                    scheduler.regionDelayed(origin.getLocation(),
                            new LeafDecay(origin.getLocation(), leaves), 2L * DECAY_WAVE_TICKS);
                }
            }
            if (replantSapling && !replants.isEmpty()) {
                scheduler.regionDelayed(origin.getLocation(), () -> plant(replants), 5L);
            }
        }
        if (survival && hungerEnabled && felled > 0) {
            player.setExhaustion(player.getExhaustion() + hungerPerBlock * felled);
        }
        return felled;
    }

    private record Replant(Block block, Material sapling) {
    }

    private void addReplant(List<Replant> replants, Block log) {
        if (!replantSapling) {
            return;
        }
        Material sapling = saplingFor(log.getType());
        if (sapling != null && canPlantOn(sapling, log.getRelative(0, -1, 0).getType())) {
            replants.add(new Replant(log, sapling));
        }
    }

    private void plant(List<Replant> replants) {
        for (Replant replant : replants) {
            Block block = replant.block();
            if (!Bukkit.isOwnedByCurrentRegion(block)
                    || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                    || !block.getType().isAir()
                    || !canPlantOn(replant.sapling(), block.getRelative(0, -1, 0).getType())) {
                continue;
            }
            block.setType(replant.sapling());
        }
    }

    private static Material saplingFor(Material log) {
        String name = log.name();
        if (name.startsWith("STRIPPED_")) {
            name = name.substring("STRIPPED_".length());
        }
        int cut = name.lastIndexOf('_');
        if (cut < 0) {
            return null;
        }
        String suffix = name.substring(cut + 1);
        if (!suffix.equals("LOG") && !suffix.equals("WOOD")
                && !suffix.equals("STEM") && !suffix.equals("HYPHAE")) {
            return null;
        }
        String species = name.substring(0, cut);
        return switch (species) {
            case "MANGROVE" -> Material.MANGROVE_PROPAGULE;
            case "CRIMSON" -> Material.CRIMSON_FUNGUS;
            case "WARPED" -> Material.WARPED_FUNGUS;
            default -> Material.matchMaterial(species + "_SAPLING");
        };
    }

    private static boolean canPlantOn(Material sapling, Material soil) {
        if (sapling == Material.CRIMSON_FUNGUS || sapling == Material.WARPED_FUNGUS) {
            return soil == Material.CRIMSON_NYLIUM || soil == Material.WARPED_NYLIUM
                    || soil == Material.NETHERRACK || soil == Material.SOUL_SOIL;
        }
        return Tag.DIRT.isTagged(soil);
    }

    private List<Block> findLeaves(Block origin, List<Block> logs) {
        List<Block> found = new ArrayList<>();
        Set<Block> seen = new HashSet<>(logs);
        seen.add(origin);
        List<Block> frontier = new ArrayList<>(logs.size() + 1);
        frontier.add(origin);
        frontier.addAll(logs);
        for (int depth = 0; depth < LEAF_RANGE && !frontier.isEmpty() && found.size() < MAX_LEAVES; depth++) {
            List<Block> next = new ArrayList<>();
            for (Block current : frontier) {
                for (int[] face : FACES) {
                    Block leaf = current.getRelative(face[0], face[1], face[2]);
                    if (!seen.add(leaf)
                            || !Bukkit.isOwnedByCurrentRegion(leaf)
                            || !leaf.getWorld().isChunkLoaded(leaf.getX() >> 4, leaf.getZ() >> 4)
                            || !Tag.LEAVES.isTagged(leaf.getType())) {
                        continue;
                    }
                    if (leaf.getBlockData() instanceof Leaves data && !data.isPersistent()) {
                        found.add(leaf);
                        next.add(leaf);
                    }
                }
            }
            frontier = next;
        }
        return found;
    }

    private final class LeafDecay implements Runnable {

        private final Location origin;
        private final List<Block> leaves;
        private int wavesLeft = DECAY_WAVES;

        private LeafDecay(Location origin, List<Block> leaves) {
            this.origin = origin;
            this.leaves = leaves;
        }

        @Override
        public void run() {
            Iterator<Block> iterator = leaves.iterator();
            while (iterator.hasNext()) {
                Block leaf = iterator.next();
                if (!Bukkit.isOwnedByCurrentRegion(leaf)
                        || !leaf.getWorld().isChunkLoaded(leaf.getX() >> 4, leaf.getZ() >> 4)) {
                    continue;
                }
                if (!(leaf.getBlockData() instanceof Leaves data) || data.isPersistent()) {
                    iterator.remove();
                    continue;
                }
                if (data.getDistance() < LEAF_RANGE) {
                    continue;
                }
                if (new LeavesDecayEvent(leaf).callEvent()) {
                    leaf.breakNaturally(true);
                }
                iterator.remove();
            }
            if (!leaves.isEmpty() && --wavesLeft > 0) {
                scheduler.regionDelayed(origin, this, DECAY_WAVE_TICKS);
            }
        }
    }

    private void topple(Block block, int originY, Vector direction, boolean drop) {
        BlockData data = block.getBlockData();
        Location spawn = block.getLocation().add(0.5, 0.0, 0.5);
        block.setType(Material.AIR);
        double lean = Math.clamp(0.05 * (block.getY() - originY + 1), 0.02, 0.32);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector velocity = direction.clone().multiply(lean)
                .add(new Vector(random.nextDouble(-0.02, 0.02), 0.0, random.nextDouble(-0.02, 0.02)));
        block.getWorld().spawn(spawn, FallingBlock.class, falling -> {
            falling.setBlockData(data);
            falling.setDropItem(false);
            falling.setCancelDrop(true);
            falling.setHurtEntities(false);
            falling.getPersistentDataContainer()
                    .set(fallKey, PersistentDataType.BYTE, (byte) (drop ? 1 : 0));
            falling.setVelocity(velocity);
        });
    }

    boolean handleLand(FallingBlock falling) {
        Byte flag = falling.getPersistentDataContainer().get(fallKey, PersistentDataType.BYTE);
        if (flag == null) {
            return false;
        }
        falling.getPersistentDataContainer().remove(fallKey);
        if (flag != 0) {
            falling.getWorld().dropItemNaturally(falling.getLocation(),
                    new ItemStack(falling.getBlockData().getMaterial()));
        }
        falling.remove();
        return true;
    }

    void handleRemoved(Entity entity) {
        if (!(entity instanceof FallingBlock falling)) {
            return;
        }
        Byte flag = falling.getPersistentDataContainer().get(fallKey, PersistentDataType.BYTE);
        if (flag == null) {
            return;
        }
        falling.getPersistentDataContainer().remove(fallKey);
        if (flag != 0) {
            falling.getWorld().dropItemNaturally(falling.getLocation(),
                    new ItemStack(falling.getBlockData().getMaterial()));
        }
    }

    private List<Block> findTree(Block origin) {
        List<Block> found = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        boolean leaves = !requireLeaves || hasNaturalLeaves(origin);
        int minY = scope == Scope.ABOVE ? origin.getY() : Integer.MIN_VALUE;
        while (!queue.isEmpty() && found.size() < maxTreeSize) {
            Block current = queue.poll();
            for (int[] offset : NEIGHBOURS) {
                if (found.size() >= maxTreeSize) {
                    break;
                }
                Block next = current.getRelative(offset[0], offset[1], offset[2]);
                if (next.getY() < minY
                        || !seen.add(next)
                        || !Bukkit.isOwnedByCurrentRegion(next)
                        || !next.getWorld().isChunkLoaded(next.getX() >> 4, next.getZ() >> 4)
                        || !blocks.contains(next.getType())) {
                    continue;
                }
                if (!leaves) {
                    leaves = hasNaturalLeaves(next);
                }
                found.add(next);
                queue.add(next);
            }
        }
        return leaves ? found : List.of();
    }

    private boolean hasNaturalLeaves(Block log) {
        for (int[] offset : NEIGHBOURS) {
            Block next = log.getRelative(offset[0], offset[1], offset[2]);
            if (!Bukkit.isOwnedByCurrentRegion(next)
                    || !next.getWorld().isChunkLoaded(next.getX() >> 4, next.getZ() >> 4)
                    || !Tag.LEAVES.isTagged(next.getType())) {
                continue;
            }
            if (next.getBlockData() instanceof Leaves leaf && !leaf.isPersistent()) {
                return true;
            }
        }
        return false;
    }

    private static Vector fallDirection(Player player) {
        double yaw = Math.toRadians(player.getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
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
            plugin.getLogger().warning("Unknown treecapitator " + type.getSimpleName().toLowerCase(Locale.ROOT)
                    + " '" + raw + "' in config.yml, using " + fallback.name().toLowerCase(Locale.ROOT) + ".");
            return fallback;
        }
    }

    private Set<Material> materials(List<String> names, String what) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                plugin.getLogger().warning("Ignoring unknown treecapitator " + what + " '" + name
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
