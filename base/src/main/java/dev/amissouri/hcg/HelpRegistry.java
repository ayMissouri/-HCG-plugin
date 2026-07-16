package dev.amissouri.hcg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HelpRegistry {
    public record Entry(String usage, String description) {}
    public record Category(String name, int order, List<Entry> entries) {}

    public static final int ORDER_TWEAKS = 800;
    public static final int ORDER_ADMIN = 900;
    public static final int ORDER_ITEM = 910;
    public static final int ORDER_WORLD = 920;

    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();

    private HelpRegistry() {
    }

    public static synchronized void register(String name, int order, List<Entry> entries) {
        CATEGORIES.put(name, new Category(name, order, List.copyOf(entries)));
    }

    public static synchronized void unregister(String name) {
        CATEGORIES.remove(name);
    }

    public static synchronized List<Category> categories() {
        List<Category> list = new ArrayList<>(CATEGORIES.values());
        list.sort(Comparator.comparingInt(Category::order).thenComparing(Category::name));
        return list;
    }
}
