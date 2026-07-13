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

/** /firstto craft|obtain|stop|status, plus nether|end|tpspawn toggles, item race with a slot-machine roll. */
public final class FirstToCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("craft", "obtain", "stop", "status", "nether", "end", "tpspawn");
    private static final List<String> TOGGLES = List.of("nether", "end", "tpspawn");

    private final FirstToManager manager;

    public FirstToCommand(FirstToManager manager) {
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
            case "craft" -> startRound(sender, FirstToManager.Mode.CRAFT);
            case "obtain", "get" -> startRound(sender, FirstToManager.Mode.OBTAIN);
            case "stop" -> stop(sender);
            case "status" -> showStatus(sender);
            case "nether", "end", "tpspawn" -> {
                if (args.length < 2) {
                    return false;
                }
                return toggle(sender, args[0].toLowerCase(), args[1].toLowerCase());
            }
            case "set" -> {
                if (args.length >= 3 && TOGGLES.contains(args[1].toLowerCase())) {
                    toggle(sender, args[1].toLowerCase(), args[2].toLowerCase());
                }
                showMenu(sender);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void startRound(CommandSender sender, FirstToManager.Mode mode) {
        if (manager.isRunning()) {
            Messages.send(sender, "firstto.already-running");
            return;
        }
        if (!manager.start(mode)) {
            Messages.send(sender, "firstto.pool-empty");
        }
    }

    private void stop(CommandSender sender) {
        if (!manager.isRunning()) {
            Messages.send(sender, "firstto.not-running");
            return;
        }
        manager.stop();
        Messages.broadcast("firstto.stopped-broadcast");
    }

    private boolean toggle(CommandSender sender, String setting, String value) {
        boolean state;
        switch (value) {
            case "on" -> state = true;
            case "off" -> state = false;
            default -> {
                return false;
            }
        }
        switch (setting) {
            case "nether" -> {
                manager.setIncludeNether(state);
                Messages.send(sender, "firstto.nether-set", "state", value);
            }
            case "end" -> {
                manager.setIncludeEnd(state);
                Messages.send(sender, "firstto.end-set", "state", value);
            }
            case "tpspawn" -> {
                manager.setTpSpawnOnWin(state);
                Messages.send(sender, "firstto.tpspawn-set", "state", value);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void showStatus(CommandSender sender) {
        if (manager.isRolling()) {
            Messages.send(sender, "firstto.status-rolling");
        } else if (manager.isRunning()) {
            Messages.send(sender, "firstto.status-running",
                    "action", Messages.raw(manager.mode() == FirstToManager.Mode.CRAFT
                            ? "firstto.action-craft" : "firstto.action-obtain"),
                    "item", FirstToManager.prettyName(manager.target()));
        } else {
            Messages.send(sender, "firstto.status-idle");
        }
        Messages.send(sender, "firstto.status-settings",
                "nether", manager.includeNether() ? "on" : "off",
                "end", manager.includeEnd() ? "on" : "off",
                "tpspawn", manager.tpSpawnOnWin() ? "on" : "off");
    }

    private void showMenu(CommandSender sender) {
        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text("First To", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ---", NamedTextColor.DARK_GRAY)));

        Component round;
        if (manager.isRolling()) {
            round = Component.text("ROLLING...", NamedTextColor.YELLOW, TextDecoration.BOLD);
        } else if (manager.isRunning()) {
            String action = manager.mode() == FirstToManager.Mode.CRAFT ? "craft" : "obtain";
            round = Component.text("first to " + action + " ", NamedTextColor.GREEN)
                    .append(Component.text(FirstToManager.prettyName(manager.target()),
                            NamedTextColor.YELLOW, TextDecoration.BOLD));
        } else {
            round = Component.text("IDLE", NamedTextColor.GRAY, TextDecoration.BOLD);
        }
        sender.sendMessage(Component.text("Round: ", NamedTextColor.GRAY).append(round));

        sender.sendMessage(Component.text("  ")
                .append(button("[Craft Round]", NamedTextColor.GREEN, "/firstto craft",
                        "Roll a craftable item, first to craft it wins"))
                .append(Component.text("  "))
                .append(button("[Obtain Round]", NamedTextColor.AQUA, "/firstto obtain",
                        "Roll any survival item, first to get one wins"))
                .append(Component.text("  "))
                .append(button("[Stop]", NamedTextColor.RED, "/firstto stop",
                        "Cancel the current round")));

        sender.sendMessage(toggleLine("Nether items", manager.includeNether(), "nether",
                "Allow nether-only items as targets"));
        sender.sendMessage(toggleLine("End items", manager.includeEnd(), "end",
                "Allow end-only items as targets"));
        sender.sendMessage(toggleLine("TP to spawn on win", manager.tpSpawnOnWin(), "tpspawn",
                "Teleport everyone to spawn when someone wins"));
    }

    private Component toggleLine(String name, boolean state, String setting, String hover) {
        return Component.text(name + ": ", NamedTextColor.GRAY)
                .append(state
                        ? Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("OFF", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(state
                        ? button("[Turn off]", NamedTextColor.RED, "/firstto set " + setting + " off", hover)
                        : button("[Turn on]", NamedTextColor.GREEN, "/firstto set " + setting + " on", hover));
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
        if (args.length == 2 && TOGGLES.contains(args[0].toLowerCase())) {
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
