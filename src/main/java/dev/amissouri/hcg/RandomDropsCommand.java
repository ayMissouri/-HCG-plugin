package dev.amissouri.hcg;

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
 * /randomdrops [on|off|status|mode <dynamic|static>|reroll], bare command opens a clickable settings menu.
 */
public final class RandomDropsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("on", "off", "status", "mode", "enchants", "mobs", "reroll");

    private final HCGPlugin plugin;
    private final RandomDropsManager manager;

    public RandomDropsCommand(HCGPlugin plugin, RandomDropsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showMenu(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "menu" -> showMenu(sender);
            case "on" -> turnOn(sender);
            case "off" -> turnOff(sender);
            case "status" -> Messages.send(sender, "randomdrops.status",
                    "state", manager.isEnabled() ? "enabled" : "disabled",
                    "mode", manager.mode().name().toLowerCase(),
                    "enchants", manager.isEnchanted() ? "on" : "off",
                    "mobs", manager.isMobsEnabled() ? "on" : "off",
                    "pool", String.valueOf(manager.poolSize()));
            case "mode" -> {
                if (!setModeFromArg(sender, args)) {
                    Messages.send(sender, "randomdrops.mode-usage");
                }
            }
            case "enchants" -> {
                if (!setEnchantsFromArg(sender, args)) {
                    Messages.send(sender, "randomdrops.enchants-usage");
                }
            }
            case "mobs" -> {
                if (!setMobsFromArg(sender, args)) {
                    Messages.send(sender, "randomdrops.mobs-usage");
                }
            }
            case "reroll" -> {
                manager.reroll();
                Messages.send(sender, "randomdrops.rerolled");
            }
            case "set" -> {
                if (args.length >= 2) {
                    switch (args[1].toLowerCase()) {
                        case "on" -> turnOn(sender);
                        case "off" -> turnOff(sender);
                        case "mode" -> setModeFromArg(sender, args);
                        case "enchants" -> setEnchantsFromArg(sender, args);
                        case "mobs" -> setMobsFromArg(sender, args);
                        case "reroll" -> {
                            manager.reroll();
                            Messages.send(sender, "randomdrops.rerolled-short");
                        }
                        default -> { }
                    }
                }
                showMenu(sender);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean setModeFromArg(CommandSender sender, String[] args) {
        String value = args[args.length - 1].toLowerCase();
        RandomDropsManager.Mode mode = switch (value) {
            case "dynamic" -> RandomDropsManager.Mode.DYNAMIC;
            case "static" -> RandomDropsManager.Mode.STATIC;
            default -> null;
        };
        if (mode == null) {
            return false;
        }
        if (manager.mode() != mode) {
            manager.setMode(mode);
            Messages.send(sender, "randomdrops.mode-set", "mode", value);
        }
        return true;
    }

    private boolean setEnchantsFromArg(CommandSender sender, String[] args) {
        String value = args[args.length - 1].toLowerCase();
        Boolean state = switch (value) {
            case "on" -> true;
            case "off" -> false;
            default -> null;
        };
        if (state == null) {
            return false;
        }
        if (manager.isEnchanted() != state) {
            manager.setEnchanted(state);
            Messages.send(sender, "randomdrops.enchants-set", "state", value);
        }
        return true;
    }

    private boolean setMobsFromArg(CommandSender sender, String[] args) {
        String value = args[args.length - 1].toLowerCase();
        Boolean state = switch (value) {
            case "on" -> true;
            case "off" -> false;
            default -> null;
        };
        if (state == null) {
            return false;
        }
        if (manager.isMobsEnabled() != state) {
            manager.setMobsEnabled(state);
            Messages.send(sender, "randomdrops.mobs-set", "state", value);
        }
        return true;
    }

    private void turnOn(CommandSender sender) {
        if (manager.isEnabled()) {
            Messages.send(sender, "randomdrops.already-enabled");
            return;
        }
        manager.setEnabled(true);
        Messages.broadcastOps("randomdrops.enabled-broadcast", "mode", manager.mode().name().toLowerCase());
    }

    private void turnOff(CommandSender sender) {
        if (!manager.isEnabled()) {
            Messages.send(sender, "randomdrops.not-enabled");
            return;
        }
        manager.setEnabled(false);
        Messages.broadcastOps("randomdrops.disabled-broadcast");
    }

    private void showMenu(CommandSender sender) {
        boolean enabled = manager.isEnabled();
        boolean isStatic = manager.mode() == RandomDropsManager.Mode.STATIC;

        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Random Drops", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ---", NamedTextColor.DARK_GRAY)));

        Component toggle = enabled
                ? button("[Disable]", NamedTextColor.RED, "/randomdrops set off",
                        "Blocks drop normally again")
                : button("[Enable]", NamedTextColor.GREEN, "/randomdrops set on",
                        "Every block break drops a random item");
        sender.sendMessage(Component.text("Status: ", NamedTextColor.GRAY)
                .append(enabled
                        ? Component.text("ENABLED", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("DISABLED", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(toggle));

        Component dynamicButton = isStatic
                ? button("[Dynamic]", NamedTextColor.AQUA, "/randomdrops set mode dynamic",
                        "Every break rolls a fresh random drop")
                : Component.text("[Dynamic]", NamedTextColor.GOLD, TextDecoration.BOLD);
        Component staticButton = isStatic
                ? Component.text("[Static]", NamedTextColor.GOLD, TextDecoration.BOLD)
                : button("[Static]", NamedTextColor.AQUA, "/randomdrops set mode static",
                        "Each block type always drops the same random item");
        sender.sendMessage(Component.text("Mode: ", NamedTextColor.GRAY)
                .append(Component.text(isStatic ? "STATIC" : "DYNAMIC", NamedTextColor.YELLOW,
                        TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(dynamicButton)
                .append(Component.text(" "))
                .append(staticButton));

        boolean enchants = manager.isEnchanted();
        Component enchantToggle = enchants
                ? button("[Disable]", NamedTextColor.RED, "/randomdrops set enchants off",
                        "Drops come without enchantments")
                : button("[Enable]", NamedTextColor.GREEN, "/randomdrops set enchants on",
                        "Every drop gets 1-3 random enchantments");
        sender.sendMessage(Component.text("Enchants: ", NamedTextColor.GRAY)
                .append(enchants
                        ? Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("OFF", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(enchantToggle));

        boolean mobsOn = manager.isMobsEnabled();
        Component mobToggle = mobsOn
                ? button("[Disable]", NamedTextColor.RED, "/randomdrops set mobs off",
                        "Mobs drop their normal loot again")
                : button("[Enable]", NamedTextColor.GREEN, "/randomdrops set mobs on",
                        "Every mob death drops a random item");
        sender.sendMessage(Component.text("Mob drops: ", NamedTextColor.GRAY)
                .append(mobsOn
                        ? Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("OFF", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(mobToggle));

        if (isStatic) {
            sender.sendMessage(Component.text("Drop table: ", NamedTextColor.GRAY)
                    .append(Component.text("each block type has one fixed drop  ", NamedTextColor.YELLOW))
                    .append(button("[Reroll]", NamedTextColor.LIGHT_PURPLE, "/randomdrops set reroll",
                            "Randomize the whole drop table again")));
        }

        sender.sendMessage(Component.text(
                (isStatic
                        ? "Same block, same drop — until you reroll. "
                        : "Every break rolls a fresh random drop. ")
                        + manager.poolSize() + " survival-obtainable items in the pool.",
                NamedTextColor.GRAY));
    }

    private Component button(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(hover)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return List.of("dynamic", "static").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 2
                && (args[0].equalsIgnoreCase("enchants") || args[0].equalsIgnoreCase("mobs"))) {
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
