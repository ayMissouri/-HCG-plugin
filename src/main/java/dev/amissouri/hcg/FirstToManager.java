package dev.amissouri.hcg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.scheduler.BukkitTask;

/**
 * First-to race rounds: a chest GUI rolls through items like a slot machine, lands on a random
 * target, and the first player to craft it (craft mode) or hold it (obtain mode) wins.
 */
public final class FirstToManager {

    public enum Mode { CRAFT, OBTAIN }

    /** Marks the rolling GUI so the listener can cancel clicks inside it. */
    static final class RollHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300));
    private static final int CENTER_SLOT = 13;
    private static final int[] ROLL_DELAYS = {
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 12};
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int CLOSE_DELAY_TICKS = 60;

    /** Items that exist as items but can't be obtained in survival. */
    private static final Set<Material> UNOBTAINABLE = EnumSet.of(
            Material.BEDROCK, Material.BARRIER, Material.LIGHT, Material.SPAWNER,
            Material.TRIAL_SPAWNER, Material.VAULT, Material.END_PORTAL_FRAME,
            Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID, Material.JIGSAW,
            Material.DEBUG_STICK, Material.KNOWLEDGE_BOOK, Material.FARMLAND,
            Material.DIRT_PATH, Material.REINFORCED_DEEPSLATE, Material.BUDDING_AMETHYST,
            Material.CHORUS_PLANT, Material.FROGSPAWN, Material.PETRIFIED_OAK_SLAB,
            Material.PLAYER_HEAD, Material.BUNDLE, Material.SUSPICIOUS_SAND,
            Material.SUSPICIOUS_GRAVEL, Material.GLOBE_BANNER_PATTERN);

    /** Nether-gated items whose material name doesn't give them away. */
    private static final Set<Material> NETHER_EXTRA = EnumSet.of(
            Material.GHAST_TEAR, Material.BREWING_STAND, Material.BEACON, Material.LODESTONE,
            Material.ANCIENT_DEBRIS, Material.RESPAWN_ANCHOR, Material.WITHER_ROSE,
            Material.WITHER_SKELETON_SKULL, Material.PIGLIN_HEAD, Material.END_CRYSTAL,
            Material.ENDER_CHEST, Material.ENDER_EYE, Material.TWISTING_VINES,
            Material.WEEPING_VINES, Material.MUSIC_DISC_PIGSTEP,
            Material.PIGLIN_BANNER_PATTERN, Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE);

    /** End-gated items whose material name doesn't give them away. */
    private static final Set<Material> END_EXTRA = EnumSet.of(
            Material.END_ROD, Material.LINGERING_POTION, Material.TIPPED_ARROW,
            Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE);

    private final HCGPlugin plugin;
    private final Random random = new Random();
    private boolean includeNether;
    private boolean includeEnd;
    private boolean tpSpawnOnWin;

    private Mode mode;
    private Material target;
    private boolean rolling;
    private boolean active;
    private List<Material> pool;
    private RollHolder holder;
    private BukkitTask rollTask;
    private BukkitTask scanTask;
    private long startMillis;

    public FirstToManager(HCGPlugin plugin) {
        this.plugin = plugin;
        this.includeNether = plugin.getConfig().getBoolean("first-to.include-nether", true);
        this.includeEnd = plugin.getConfig().getBoolean("first-to.include-end", true);
        this.tpSpawnOnWin = plugin.getConfig().getBoolean("first-to.tp-spawn-on-win", false);
    }

    public boolean isRunning() {
        return rolling || active;
    }

    public boolean isRolling() {
        return rolling;
    }

    public Mode mode() {
        return mode;
    }

    public Material target() {
        return target;
    }

    public boolean includeNether() {
        return includeNether;
    }

    public boolean includeEnd() {
        return includeEnd;
    }

    public boolean tpSpawnOnWin() {
        return tpSpawnOnWin;
    }

    public void setIncludeNether(boolean value) {
        includeNether = value;
        plugin.getConfig().set("first-to.include-nether", value);
        plugin.saveConfig();
    }

    public void setIncludeEnd(boolean value) {
        includeEnd = value;
        plugin.getConfig().set("first-to.include-end", value);
        plugin.saveConfig();
    }

    public void setTpSpawnOnWin(boolean value) {
        tpSpawnOnWin = value;
        plugin.getConfig().set("first-to.tp-spawn-on-win", value);
        plugin.saveConfig();
    }

    /** Picks a target from the mode's pool and starts the slot-machine roll. False if the pool is empty. */
    public boolean start(Mode mode) {
        pool = buildPool(mode);
        if (pool.isEmpty()) {
            return false;
        }
        this.mode = mode;
        this.target = pool.get(random.nextInt(pool.size()));
        rolling = true;

        holder = new RollHolder();
        holder.inventory = Bukkit.createInventory(holder, 27, Messages.msg("firstto.gui-title"));
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 27; slot++) {
            holder.inventory.setItem(slot, filler);
        }
        holder.inventory.setItem(CENTER_SLOT - 9, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
        holder.inventory.setItem(CENTER_SLOT + 9, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.openInventory(holder.inventory);
        }
        Messages.broadcast("firstto.rolling-broadcast");

        rollTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick;
            private int nextSwitch;
            private int switchIndex;

            @Override
            public void run() {
                if (tick++ < nextSwitch) {
                    return;
                }
                if (switchIndex >= ROLL_DELAYS.length) {
                    finishRoll();
                    return;
                }
                holder.inventory.setItem(CENTER_SLOT,
                        new ItemStack(pool.get(random.nextInt(pool.size()))));
                float pitch = 0.8f + 1.0f * switchIndex / ROLL_DELAYS.length;
                playSound(Sound.BLOCK_NOTE_BLOCK_HAT, pitch);
                nextSwitch = tick + ROLL_DELAYS[switchIndex++];
            }
        }, 1, 1);
        return true;
    }

    private void finishRoll() {
        cancelRollTask();
        rolling = false;
        active = true;
        startMillis = System.currentTimeMillis();
        holder.inventory.setItem(CENTER_SLOT, new ItemStack(target));
        playSound(Sound.ENTITY_PLAYER_LEVELUP, 1f);

        Messages.broadcast(mode == Mode.CRAFT
                ? "firstto.craft-target-broadcast" : "firstto.obtain-target-broadcast",
                "item", prettyName(target));

        RollHolder finished = holder;
        Bukkit.getScheduler().runTaskLater(plugin, () -> closeGui(finished), CLOSE_DELAY_TICKS);

        if (mode == Mode.OBTAIN) {
            scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanInventories,
                    SCAN_INTERVAL_TICKS, SCAN_INTERVAL_TICKS);
        }
    }

    /** Reopens the rolling GUI next tick so players can't close it while an item is being picked. */
    void handleGuiClose(Player player, Inventory inventory) {
        if (!rolling || holder == null || inventory != holder.inventory) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (rolling && holder != null && player.isOnline()) {
                player.openInventory(holder.inventory);
            }
        });
    }

    /** Called by the listener on every craft; ends the round if it matches the target. */
    void handleCraft(Player player, Material crafted) {
        if (active && mode == Mode.CRAFT && crafted == target && isEligible(player)) {
            win(player);
        }
    }

    private void scanInventories() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isEligible(player)) {
                continue;
            }
            if (player.getInventory().contains(target) || player.getItemOnCursor().getType() == target) {
                win(player);
                return;
            }
        }
    }

    private boolean isEligible(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private void win(Player winner) {
        String item = prettyName(target);
        String action = Messages.raw(actionKey());
        String time = formatDuration(System.currentTimeMillis() - startMillis);
        clearRound();
        Messages.broadcast("firstto.winner-broadcast",
                "player", winner.getName(), "action", action, "item", item, "time", time);
        showTitle("firstto.winner-title", "firstto.winner-subtitle",
                "player", winner.getName(), "action", action, "item", item, "time", time);
        playSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
        if (tpSpawnOnWin) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            Messages.broadcast("firstto.tp-broadcast");
        }
    }

    public void stop() {
        clearRound();
    }

    public void shutdown() {
        clearRound();
    }

    private void clearRound() {
        cancelRollTask();
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        rolling = false;
        active = false;
        mode = null;
        target = null;
        pool = null;
        if (holder != null) {
            RollHolder gui = holder;
            holder = null;
            closeGui(gui);
        }
    }

    private void cancelRollTask() {
        if (rollTask != null) {
            rollTask.cancel();
            rollTask = null;
        }
    }

    private void closeGui(RollHolder gui) {
        for (HumanEntity viewer : List.copyOf(gui.inventory.getViewers())) {
            viewer.closeInventory();
        }
    }

    private String actionKey() {
        return mode == Mode.CRAFT ? "firstto.action-craft" : "firstto.action-obtain";
    }

    private List<Material> buildPool(Mode mode) {
        return mode == Mode.CRAFT ? buildCraftPool() : buildObtainPool();
    }

    private List<Material> buildCraftPool() {
        EnumSet<Material> results = EnumSet.noneOf(Material.class);
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
                Material result = recipe.getResult().getType();
                if (allowed(result)) {
                    results.add(result);
                }
            }
        }
        return new ArrayList<>(results);
    }

    private List<Material> buildObtainPool() {
        List<Material> items = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isLegacy() && material.isItem() && !material.isAir() && allowed(material)) {
                items.add(material);
            }
        }
        return items;
    }

    private boolean allowed(Material material) {
        if (isUnobtainable(material)) {
            return false;
        }
        if (!includeNether && isNether(material)) {
            return false;
        }
        return includeEnd || !isEnd(material);
    }

    private static boolean isUnobtainable(Material material) {
        String name = material.name();
        return UNOBTAINABLE.contains(material)
                || name.endsWith("_SPAWN_EGG")
                || name.startsWith("INFESTED_")
                || name.contains("COMMAND_BLOCK");
    }

    private static boolean isNether(Material material) {
        String name = material.name();
        return NETHER_EXTRA.contains(material)
                || name.contains("NETHER")
                || name.startsWith("CRIMSON_")
                || name.startsWith("WARPED_")
                || name.startsWith("SOUL_")
                || name.contains("BLACKSTONE")
                || name.contains("BASALT")
                || name.contains("QUARTZ")
                || name.contains("GLOWSTONE")
                || name.contains("SHROOMLIGHT")
                || name.contains("MAGMA")
                || name.contains("BLAZE");
    }

    private static boolean isEnd(Material material) {
        String name = material.name();
        return END_EXTRA.contains(material)
                || name.contains("END_STONE")
                || name.contains("PURPUR")
                || name.contains("CHORUS")
                || name.contains("SHULKER")
                || name.contains("DRAGON")
                || name.contains("ELYTRA");
    }

    static String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    public static String prettyName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder pretty = new StringBuilder();
        for (String word : words) {
            if (!pretty.isEmpty()) {
                pretty.append(' ');
            }
            pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return pretty.toString();
    }

    private void showTitle(String titleKey, String subtitleKey, String... pairs) {
        Title title = Title.title(Messages.msg(titleKey, pairs), Messages.msg(subtitleKey, pairs), TITLE_TIMES);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }

    private void playSound(Sound sound, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, 1f, pitch);
        }
    }
}
