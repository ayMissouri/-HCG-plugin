package dev.amissouri.hcg.tweaks;

import java.util.ArrayList;
import java.util.List;

import dev.amissouri.hcg.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class TweakEnchant {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V"};

    private final NamespacedKey key;
    private final String messagePath;

    public TweakEnchant(Plugin plugin, String id) {
        this.key = new NamespacedKey(plugin, id);
        this.messagePath = "tweaks." + id + ".";
    }

    public int level(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    public boolean has(ItemStack item) {
        return level(item) > 0;
    }

    public void apply(ItemStack item, int level) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (level <= 0) {
            meta.getPersistentDataContainer().remove(key);
        } else {
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);
        }
        meta.lore(withLoreLine(meta.lore(), level));
        item.setItemMeta(meta);
    }

    public String displayName(int level) {
        String name = Messages.raw(messagePath + "enchant-name");
        return level >= 1 && level <= NUMERALS.length ? name + " " + NUMERALS[level - 1] : name;
    }

    private List<Component> withLoreLine(List<Component> current, int level) {
        String name = Messages.raw(messagePath + "enchant-name");
        List<Component> lore = new ArrayList<>();
        if (current != null) {
            for (Component line : current) {
                if (!PLAIN.serialize(line).contains(name)) {
                    lore.add(line);
                }
            }
        }
        if (level > 0) {
            lore.add(Messages.msg(messagePath + "enchant-lore", "name", displayName(level))
                    .decoration(TextDecoration.ITALIC, false));
        }
        return lore.isEmpty() ? null : lore;
    }
}
