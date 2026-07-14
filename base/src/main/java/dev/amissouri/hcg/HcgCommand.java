package dev.amissouri.hcg;

import java.util.Arrays;
import java.util.List;

import dev.amissouri.hcg.HelpRegistry.Category;
import dev.amissouri.hcg.HelpRegistry.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * /hcg help, clickable list of command categories.
 * /hcg help <category> [page], paginated commands in that category.
 *
 * <p>Categories are supplied at runtime by {@link HelpRegistry}: the base plugin registers its
 * Admin/Item/World categories, and each installed addon registers its own. The menu therefore
 * shows exactly the features that are installed.
 */
public final class HcgCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 8;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && !args[0].equalsIgnoreCase("help")) {
            return false;
        }
        if (args.length <= 1) {
            sendCategoryList(sender);
            return true;
        }

        int page = 1;
        int categoryEnd = args.length;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[args.length - 1]);
                categoryEnd = args.length - 1;
            } catch (NumberFormatException ignored) {
            }
        }
        String query = String.join(" ", Arrays.copyOfRange(args, 1, categoryEnd));

        Category category = findCategory(query);
        if (category == null) {
            sender.sendMessage(Component.text("Unknown category '" + query + "'.", NamedTextColor.RED));
            sendCategoryList(sender);
            return true;
        }
        sendCategoryPage(sender, category, page);
        return true;
    }

    private static Category findCategory(String query) {
        String q = query.toLowerCase().replace(" ", "").replace("_", "");
        if (q.isEmpty()) {
            return null;
        }
        for (Category category : HelpRegistry.categories()) {
            String slug = slug(category);
            if (slug.equals(q) || slug.startsWith(q)) {
                return category;
            }
        }
        return null;
    }

    private static String slug(Category category) {
        return category.name().toLowerCase().replace(" ", "");
    }

    private void sendCategoryList(CommandSender sender) {
        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text("HCGplugin Help", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ---", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("Click a category to view its commands:", NamedTextColor.GRAY));
        for (Category category : HelpRegistry.categories()) {
            sender.sendMessage(Component.text(" ▪ ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("[" + category.name() + "]", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("View " + category.name() + " commands")))
                            .clickEvent(ClickEvent.runCommand("/hcg help " + category.name())))
                    .append(Component.text(" (" + category.entries().size() + " commands)",
                            NamedTextColor.GRAY)));
        }
        sender.sendMessage(Component.text("Or type: /hcg help <category>", NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.ITALIC));
    }

    private void sendCategoryPage(CommandSender sender, Category category, int page) {
        int pages = (category.entries().size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.clamp(page, 1, pages);

        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text(category.name(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" (" + page + "/" + pages + ") ", NamedTextColor.GRAY))
                .append(Component.text("---", NamedTextColor.DARK_GRAY)));

        int start = (page - 1) * PAGE_SIZE;
        List<Entry> entries = category.entries();
        for (Entry entry : entries.subList(start, Math.min(start + PAGE_SIZE, entries.size()))) {
            String suggestion = entry.usage().split(",")[0].split(" \\[")[0].split(" <")[0].split("\\|")[0].trim();
            sender.sendMessage(Component.text(entry.usage(), NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to put ", NamedTextColor.GRAY)
                                    .append(Component.text(suggestion, NamedTextColor.YELLOW))
                                    .append(Component.text(" in your chat bar", NamedTextColor.GRAY))))
                    .clickEvent(ClickEvent.suggestCommand(suggestion + " "))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(entry.description(), NamedTextColor.GRAY)));
        }

        String base = "/hcg help " + category.name() + " ";
        Component back = Component.text("◀ Categories", NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text("Back to the category list")))
                .clickEvent(ClickEvent.runCommand("/hcg help"));
        Component prev = page > 1
                ? Component.text("◀ Prev", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page - 1))))
                        .clickEvent(ClickEvent.runCommand(base + (page - 1)))
                : Component.text("◀ Prev", NamedTextColor.DARK_GRAY);
        Component next = page < pages
                ? Component.text("Next ▶", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page + 1))))
                        .clickEvent(ClickEvent.runCommand(base + (page + 1)))
                : Component.text("Next ▶", NamedTextColor.DARK_GRAY);

        sender.sendMessage(Component.text("  ")
                .append(back)
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(prev)
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(next));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && "help".startsWith(args[0].toLowerCase())) {
            return List.of("help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return HelpRegistry.categories().stream()
                    .map(HcgCommand::slug)
                    .filter(slug -> slug.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("help")) {
            Category category = findCategory(args[1]);
            if (category != null) {
                int pages = (category.entries().size() + PAGE_SIZE - 1) / PAGE_SIZE;
                return java.util.stream.IntStream.rangeClosed(1, pages)
                        .mapToObj(String::valueOf)
                        .filter(s -> s.startsWith(args[2]))
                        .toList();
            }
        }
        return List.of();
    }
}
