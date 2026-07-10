package dev.amissouri.hcg;

import java.util.Arrays;
import java.util.List;

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
 */
public final class HcgCommand implements CommandExecutor, TabCompleter {

    private record Entry(String usage, String description) {}

    private record Category(String name, List<Entry> entries) {}

    private static final int PAGE_SIZE = 8;

    private static final List<Category> CATEGORIES = List.of(
            new Category("Health Decay", List.of(
                    new Entry("/healthdecay", "Open the health decay settings menu."),
                    new Entry("/healthdecay on|off", "Start or stop the health decay game mode."),
                    new Entry("/healthdecay status", "Show current max health, floor, and decay rate."),
                    new Entry("/healthdecay restore", "Restore everyone's health now."),
                    new Entry("/healthdecay interval <s>", "Set seconds between decay ticks."),
                    new Entry("/healthdecay amount <hearts>", "Set hearts lost per decay tick."))),
            new Category("Random Drops", List.of(
                    new Entry("/randomdrops", "Open the random drops settings menu."),
                    new Entry("/randomdrops on|off", "Enable or disable random block drops."),
                    new Entry("/randomdrops status", "Show whether random drops are on."),
                    new Entry("/randomdrops mode <dynamic|static>", "Fresh roll per break, or one fixed drop per block type."),
                    new Entry("/randomdrops enchants <on|off>", "Give every drop 1-3 random enchantments."),
                    new Entry("/randomdrops mobs <on|off>", "Mobs also drop random items instead of loot."),
                    new Entry("/randomdrops reroll", "Randomize the static drop table again."))),
            new Category("Lava Raise", List.of(
                    new Entry("/lavaraise", "Open the lava raise settings menu."),
                    new Entry("/lavaraise on|off", "Arm or disarm the daily rising lava event."),
                    new Entry("/lavaraise status", "Show phase, current lava level, and settings."),
                    new Entry("/lavaraise start <time>", "World-clock time the lava starts rising."),
                    new Entry("/lavaraise end <time>", "World-clock time the lava starts draining."),
                    new Entry("/lavaraise duration <s>", "Seconds to travel bedrock <-> max level."),
                    new Entry("/lavaraise maxy <y>", "The Y level the lava rises to."),
                    new Entry("/lavaraise water <on|off>", "Whether oceans fill with lava too."),
                    new Entry("/lavaraise blocks <on|off>", "Whether player-placed burnables burn away."),
                    new Entry("/lavaraise mobs <on|off>", "Whether mobs burn in the lava too."),
                    new Entry("/lavaraise cancel", "Drain the lava immediately."),
                    new Entry("/lavaraise purge <y>", "Remove REAL lava blocks in the region up to Y."))),
            new Category("Hunger Games", List.of(
                    new Entry("/hungergames addspawn", "Add a spawn point at your location."),
                    new Entry("/hungergames spawns", "List all spawn points."),
                    new Entry("/hungergames delspawn <#>", "Remove a spawn point by number."),
                    new Entry("/hungergames clearspawns", "Remove all spawn points."),
                    new Entry("/hungergames scatter", "Teleport all online players to random distinct spawns."),
                    new Entry("/hungergames setcenter", "Set the world border center to where you stand."),
                    new Entry("/hungergames start [seconds]", "Title countdown, border expands, then shrinks in stages."),
                    new Entry("/hungergames stop", "Cancel the sequence and restore the previous border."),
                    new Entry("/hungergames status", "Show phase, border sizes, and stage settings."),
                    new Entry("/hungergames startsize <blocks>", "Border size during the countdown."),
                    new Entry("/hungergames expandsize <blocks>", "Border size after the expansion."),
                    new Entry("/hungergames finalsize <blocks>", "Border size after the last stage."),
                    new Entry("/hungergames countdown <s>", "Length of the starting countdown."),
                    new Entry("/hungergames expandtime <s>", "How long the expansion takes."),
                    new Entry("/hungergames stages <n>", "Number of shrink stages."),
                    new Entry("/hungergames stagetime <s>", "Hold time between stages."),
                    new Entry("/hungergames shrinktime <s>", "How long each stage's shrink takes."))),
            new Category("Admin Commands", List.of(
                    new Entry("/hcg help [category]", "Show this help menu."),
                    new Entry("/flyspeed <1-10>", "Set your fly speed (vanilla default is 1)."),
                    new Entry("/fly", "Toggle flight in any gamemode."),
                    new Entry("/gmc, /gms, /gmsp, /gma [player]", "Gamemode shortcuts: creative, survival, spectator, adventure."),
                    new Entry("/tpall here", "Teleport all other players to you."),
                    new Entry("/tpall looking", "Teleport all other players to where you're looking."),
                    new Entry("/tpall <player>", "Teleport all other players to that player."),
                    new Entry("/freeze <player>", "Toggle freeze on a player (can look, can't move)."),
                    new Entry("/freeze all", "Freeze everyone except you; run again to unfreeze."),
                    new Entry("/invsee <player>", "Open and edit another player's inventory."),
                    new Entry("/heal [player|all]", "Heal yourself, a player, or everyone."),
                    new Entry("/feed [player|all]", "Restore hunger for yourself, a player, or everyone."),
                    new Entry("/god [player]", "Toggle invulnerability."),
                    new Entry("/burn <player> [seconds]", "Set a player on fire."),
                    new Entry("/nickname [player] <text|reset>", "Set a display name; supports & colors."),
                    new Entry("/sudo <player> <msg or /cmd>", "Make a player chat or run a command."))),
            new Category("Item Commands", List.of(
                    new Entry("/anvil", "Open a virtual anvil."),
                    new Entry("/craft", "Open a virtual crafting table."),
                    new Entry("/enderchest [player]", "Open your (or another player's) ender chest."),
                    new Entry("/enchant <enchantment> <level>", "Enchant your held item beyond vanilla limits."),
                    new Entry("/hat", "Wear your held item as a hat."),
                    new Entry("/name <text|reset>", "Rename your held item; supports & colors."),
                    new Entry("/lore <text|reset>", "Set held item lore; & colors, | for new lines."))),
            new Category("World Commands", List.of(
                    new Entry("/lightning", "Strike lightning where you're looking."),
                    new Entry("/remove <items|entities|mobs> [hostile|neutral]", "Remove loaded ground items, entities, or mobs."),
                    new Entry("/spawner <mob>", "Change the spawner you're looking at."),
                    new Entry("/spawnmob <mob> [amount]", "Spawn mobs at your location."),
                    new Entry("/volcano setcenter", "Mark the crater at the block you're looking at."),
                    new Entry("/volcano erupt [seconds]", "Eruption: debris, particles, screen shake."),
                    new Entry("/volcano schedule <time|off>", "Erupt daily at a world-clock time."),
                    new Entry("/volcano stop", "Calm the volcano immediately."))));

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
        for (Category category : CATEGORIES) {
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
        for (Category category : CATEGORIES) {
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
            return CATEGORIES.stream()
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
