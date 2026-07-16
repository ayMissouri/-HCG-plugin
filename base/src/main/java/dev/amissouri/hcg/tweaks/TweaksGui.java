package dev.amissouri.hcg.tweaks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class TweaksGui {

    @FunctionalInterface
    interface SlotAction {
        void click(Player player, boolean leftClick);
    }

    static final class Holder implements InventoryHolder {

        private final Map<Integer, SlotAction> actions = new HashMap<>();
        private Inventory inventory;
        private Tweak tweak;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int ROOT_SIZE = 27;
    private static final int TWEAK_SIZE = 36;
    private static final int BACK_SLOT = 31;
    private static final int HEADER_SLOT = 4;
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final TweaksManager manager;
    private final HcgScheduler scheduler;

    public TweaksGui(TweaksManager manager, HcgScheduler scheduler) {
        this.manager = manager;
        this.scheduler = scheduler;
    }

    public void openRoot(Player player) {
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, ROOT_SIZE, Messages.msg("tweaks.gui.title"));
        buildRoot(holder);
        player.openInventory(holder.inventory);
    }

    public void openTweak(Player player, Tweak tweak) {
        Holder holder = new Holder();
        holder.tweak = tweak;
        holder.inventory = Bukkit.createInventory(holder, TWEAK_SIZE,
                Messages.msg("tweaks.gui.tweak-title", "tweak", tweak.displayName()));
        buildTweak(holder);
        player.openInventory(holder.inventory);
    }

    void handleClick(Holder holder, Player player, int slot, boolean leftClick) {
        SlotAction action = holder.actions.get(slot);
        if (action != null) {
            action.click(player, leftClick);
        }
    }

    void refresh(Holder holder) {
        if (holder.tweak == null) {
            buildRoot(holder);
        } else {
            buildTweak(holder);
        }
    }

    private void buildRoot(Holder holder) {
        reset(holder);
        int index = 0;
        for (Tweak tweak : manager.all()) {
            if (index >= SLOTS.length) {
                break;
            }
            int slot = SLOTS[index++];
            List<Component> lore = new ArrayList<>();
            for (String line : tweak.summary()) {
                lore.add(text(line, NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(statusLine(tweak.isEnabled()));
            lore.add(Component.empty());
            lore.add(hint("tweaks.gui.hint-toggle"));
            if (!tweak.settings().isEmpty()) {
                lore.add(hint("tweaks.gui.hint-manage"));
            }
            holder.inventory.setItem(slot, icon(tweak.icon(),
                    text(tweak.displayName(), tweak.isEnabled()
                            ? NamedTextColor.GREEN : NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    lore));
            holder.actions.put(slot, (player, left) -> {
                if (left || tweak.settings().isEmpty()) {
                    tweak.setEnabled(!tweak.isEnabled());
                    refresh(holder);
                } else {
                    scheduler.entity(player, () -> openTweak(player, tweak));
                }
            });
        }
    }

    private void buildTweak(Holder holder) {
        reset(holder);
        Tweak tweak = holder.tweak;

        List<Component> headerLore = new ArrayList<>();
        for (String line : tweak.summary()) {
            headerLore.add(text(line, NamedTextColor.GRAY));
        }
        headerLore.add(Component.empty());
        headerLore.add(statusLine(tweak.isEnabled()));
        headerLore.add(Component.empty());
        headerLore.add(hint("tweaks.gui.hint-toggle"));
        holder.inventory.setItem(HEADER_SLOT, icon(tweak.icon(),
                text(tweak.displayName(), tweak.isEnabled()
                        ? NamedTextColor.GREEN : NamedTextColor.RED).decorate(TextDecoration.BOLD),
                headerLore));
        holder.actions.put(HEADER_SLOT, (player, left) -> {
            tweak.setEnabled(!tweak.isEnabled());
            refresh(holder);
        });

        int index = 0;
        for (Setting setting : tweak.settings()) {
            if (index >= SLOTS.length) {
                break;
            }
            int slot = SLOTS[index++];
            List<Component> lore = new ArrayList<>();
            for (String line : setting.description()) {
                lore.add(text(line, NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(text("Value: ", NamedTextColor.GRAY)
                    .append(text(setting.valueLabel(), NamedTextColor.YELLOW)
                            .decorate(TextDecoration.BOLD)));
            lore.add(Component.empty());
            lore.add(hint("tweaks.gui.hint-cycle"));
            holder.inventory.setItem(slot, icon(setting.icon(),
                    text(setting.name(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore));
            holder.actions.put(slot, (player, left) -> {
                setting.cycle(left);
                refresh(holder);
            });
        }

        holder.inventory.setItem(BACK_SLOT, icon(Material.ARROW,
                text(Messages.raw("tweaks.gui.back"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                List.of()));
        holder.actions.put(BACK_SLOT, (player, left) -> scheduler.entity(player, () -> openRoot(player)));
    }

    private void reset(Holder holder) {
        holder.actions.clear();
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 0; slot < holder.inventory.getSize(); slot++) {
            holder.inventory.setItem(slot, filler);
        }
    }

    private Component statusLine(boolean enabled) {
        return text("Status: ", NamedTextColor.GRAY)
                .append(enabled
                        ? text("ENABLED", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                        : text("DISABLED", NamedTextColor.RED).decorate(TextDecoration.BOLD));
    }

    private Component hint(String key) {
        return Messages.msg(key).decoration(TextDecoration.ITALIC, false);
    }

    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }
}
