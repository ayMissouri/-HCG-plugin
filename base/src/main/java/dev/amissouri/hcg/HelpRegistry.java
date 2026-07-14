package dev.amissouri.hcg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry that backs {@code /hcg help}. The base plugin registers its own
 * Admin/Item/World categories; each installed addon registers its category from its own
 * {@code onEnable} (and should {@link #unregister(String)} in {@code onDisable}). This is what
 * lets the help menu reflect exactly which addons are installed.
 *
 * <p>All access happens on the server's main thread during plugin enable/disable, so the plain
 * map is guarded by {@code synchronized} purely as a defensive measure.
 */
public final class HelpRegistry {

    /** One command line in a category: its usage and a short description. */
    public record Entry(String usage, String description) {}

    /** A named group of commands shown in {@code /hcg help}. Lower {@code order} sorts first. */
    public record Category(String name, int order, List<Entry> entries) {}

    // Suggested ordering: feature (addon) categories first, base categories last.
    public static final int ORDER_ADMIN = 900;
    public static final int ORDER_ITEM = 910;
    public static final int ORDER_WORLD = 920;

    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();

    private HelpRegistry() {
    }

    /** Adds or replaces a category by name. */
    public static synchronized void register(String name, int order, List<Entry> entries) {
        CATEGORIES.put(name, new Category(name, order, List.copyOf(entries)));
    }

    /** Removes a category by name; safe to call for a name that was never registered. */
    public static synchronized void unregister(String name) {
        CATEGORIES.remove(name);
    }

    /** A snapshot of the registered categories, sorted by order then name. */
    public static synchronized List<Category> categories() {
        List<Category> list = new ArrayList<>(CATEGORIES.values());
        list.sort(Comparator.comparingInt(Category::order).thenComparing(Category::name));
        return list;
    }
}
