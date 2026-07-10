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

/** /lavaraise, clickable settings menu plus typed subcommands. */
public final class LavaRaiseCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "on", "off", "status", "start", "end", "duration", "maxy", "water",
            "blocks", "mobs", "cancel", "purge");
    private static final List<String> TIME_SUGGESTIONS =
            List.of("day", "noon", "sunset", "night", "midnight", "sunrise", "0", "6000", "12000", "18000");

    private final LavaRaiseManager manager;

    public LavaRaiseCommand(LavaRaiseManager manager) {
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
            case "on" -> {
                if (manager.isEnabled()) {
                    Messages.send(sender, "lavaraise.already-enabled");
                } else {
                    manager.enable();
                    Messages.send(sender, "lavaraise.enabled",
                            "time", LavaRaiseManager.formatTime(manager.startTime()));
                }
            }
            case "off" -> {
                if (!manager.isEnabled()) {
                    Messages.send(sender, "lavaraise.not-enabled");
                } else {
                    manager.disable();
                    Messages.send(sender, manager.isActive()
                            ? "lavaraise.disabled-draining" : "lavaraise.disabled");
                }
            }
            case "status" -> sendStatus(sender);
            case "cancel" -> {
                if (manager.isActive()) {
                    manager.cancelEvent();
                    Messages.send(sender, "lavaraise.draining-now");
                } else {
                    Messages.send(sender, "lavaraise.no-event");
                }
            }
            case "purge" -> {
                Integer y = parseInt(sender, args, -63, 319);
                if (y == null) {
                    return true;
                }
                if (manager.startPurge(y)) {
                    Messages.send(sender, "lavaraise.purge-start", "y", String.valueOf(y));
                } else {
                    Messages.send(sender, "lavaraise.purge-busy");
                }
            }
            case "start", "end" -> {
                if (args.length < 2) {
                    return false;
                }
                int ticks = LavaRaiseManager.parseTime(args[1]);
                if (ticks < 0) {
                    Messages.send(sender, "lavaraise.invalid-time", "input", args[1]);
                    return true;
                }
                boolean isStart = args[0].equalsIgnoreCase("start");
                manager.set(isStart ? "start-time" : "end-time", ticks);
                Messages.send(sender, isStart ? "lavaraise.start-set" : "lavaraise.end-set",
                        "time", LavaRaiseManager.formatTime(ticks));
            }
            case "duration" -> {
                Integer seconds = parseInt(sender, args, 10, 7200);
                if (seconds != null) {
                    manager.set("rise-duration-seconds", seconds);
                    Messages.send(sender, "lavaraise.duration-set", "seconds", String.valueOf(seconds));
                }
            }
            case "maxy" -> {
                Integer y = parseInt(sender, args, -63, 319);
                if (y != null) {
                    manager.set("max-y", y);
                    Messages.send(sender, "lavaraise.maxy-set", "y", String.valueOf(y));
                }
            }
            case "water", "blocks", "mobs" -> {
                if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
                    return false;
                }
                boolean on = args[1].equalsIgnoreCase("on");
                String state = on ? "on" : "off";
                switch (args[0].toLowerCase()) {
                    case "water" -> {
                        manager.set("replace-water", on);
                        Messages.send(sender, "lavaraise.water-set", "state", state);
                    }
                    case "blocks" -> {
                        manager.set("burn-placed-blocks", on);
                        Messages.send(sender, "lavaraise.blocks-set", "state", state);
                    }
                    default -> {
                        manager.set("damage-mobs", on);
                        Messages.send(sender, "lavaraise.mobs-set", "state", state);
                    }
                }
            }
            case "set" -> {
                if (args.length >= 2) {
                    String[] rest = new String[args.length - 1];
                    System.arraycopy(args, 1, rest, 0, rest.length);
                    onCommand(sender, command, label, rest);
                }
                showMenu(sender);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private Integer parseInt(CommandSender sender, String[] args, int min, int max) {
        if (args.length < 2) {
            Messages.send(sender, "general.missing-argument", "name", "value");
            return null;
        }
        try {
            int value = Integer.parseInt(args[1]);
            if (value < min || value > max) {
                Messages.send(sender, "general.value-range",
                        "min", String.valueOf(min), "max", String.valueOf(max));
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            Messages.send(sender, "general.not-a-number", "input", args[1]);
            return null;
        }
    }

    private void sendStatus(CommandSender sender) {
        String phase = switch (manager.phase()) {
            case IDLE -> "disabled";
            case ARMED -> "armed (waiting for start time)";
            case RISING -> "RISING — lava at Y=" + manager.currentLavaY();
            case HOLDING -> "HOLDING at Y=" + manager.currentLavaY();
            case DRAINING -> "DRAINING — lava at Y=" + manager.currentLavaY();
        };
        Messages.send(sender, "lavaraise.status-state", "state", phase);
        Messages.send(sender, "lavaraise.status-settings",
                "start", LavaRaiseManager.formatTime(manager.startTime()),
                "end", LavaRaiseManager.formatTime(manager.endTime()),
                "seconds", String.valueOf(manager.riseDurationSeconds()),
                "y", String.valueOf(manager.maxY()),
                "water", manager.replaceWater() ? "replaced" : "kept");
    }

    private void showMenu(CommandSender sender) {
        boolean enabled = manager.isEnabled();

        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Lava Raise", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ---", NamedTextColor.DARK_GRAY)));

        String phaseLabel = switch (manager.phase()) {
            case IDLE -> "DISABLED";
            case ARMED -> "ARMED";
            case RISING -> "RISING (Y=" + manager.currentLavaY() + ")";
            case HOLDING -> "HOLDING (Y=" + manager.currentLavaY() + ")";
            case DRAINING -> "DRAINING (Y=" + manager.currentLavaY() + ")";
        };
        Component toggle = enabled
                ? button("[Disable]", NamedTextColor.RED, "/lavaraise set off",
                        "Disable the mode (drains any lava)")
                : button("[Enable]", NamedTextColor.GREEN, "/lavaraise set on",
                        "Arm the daily lava raise");
        Component statusLine = Component.text("Status: ", NamedTextColor.GRAY)
                .append(Component.text(phaseLabel,
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(toggle);
        if (manager.isActive()) {
            statusLine = statusLine.append(Component.text(" "))
                    .append(button("[Drain Now]", NamedTextColor.GOLD, "/lavaraise set cancel",
                            "Drain the lava immediately"));
        }
        sender.sendMessage(statusLine);

        sender.sendMessage(Component.text("Rises at: ", NamedTextColor.GRAY)
                .append(clickValue(LavaRaiseManager.formatTime(manager.startTime()),
                        "/lavaraise start ", "Click to type a start time")));
        sender.sendMessage(Component.text("Drains at: ", NamedTextColor.GRAY)
                .append(clickValue(LavaRaiseManager.formatTime(manager.endTime()),
                        "/lavaraise end ", "Click to type an end time")));

        int duration = manager.riseDurationSeconds();
        sender.sendMessage(Component.text("Travel time: ", NamedTextColor.GRAY)
                .append(clickValue(duration + "s", "/lavaraise duration ", "Click to type a duration"))
                .append(Component.text("  "))
                .append(button("[-60s]", NamedTextColor.RED, "/lavaraise set duration " + Math.max(10, duration - 60),
                        "Set travel time to " + Math.max(10, duration - 60) + "s"))
                .append(Component.text(" "))
                .append(button("[+60s]", NamedTextColor.GREEN, "/lavaraise set duration " + Math.min(7200, duration + 60),
                        "Set travel time to " + Math.min(7200, duration + 60) + "s")));

        int maxY = manager.maxY();
        sender.sendMessage(Component.text("Max level: ", NamedTextColor.GRAY)
                .append(clickValue("Y=" + maxY, "/lavaraise maxy ", "Click to type a Y level"))
                .append(Component.text("  "))
                .append(button("[-8]", NamedTextColor.RED, "/lavaraise set maxy " + Math.max(-63, maxY - 8),
                        "Set max level to Y=" + Math.max(-63, maxY - 8)))
                .append(Component.text(" "))
                .append(button("[+8]", NamedTextColor.GREEN, "/lavaraise set maxy " + Math.min(319, maxY + 8),
                        "Set max level to Y=" + Math.min(319, maxY + 8))));

        sender.sendMessage(toggleLine("Replace water", manager.replaceWater(), "water",
                "Oceans fill with lava too; water no longer protects",
                "Lava sits on top of oceans; water is safe"));
        sender.sendMessage(toggleLine("Burn builds", manager.burnPlacedBlocks(), "blocks",
                "Player-placed burnable blocks burn away as the lava passes",
                "Player builds survive the lava"));
        sender.sendMessage(toggleLine("Damage mobs", manager.damageMobs(), "mobs",
                "Mobs in the lava burn and take damage",
                "Mobs ignore the lava"));

        sender.sendMessage(Component.text(
                "The lava is a client-side illusion — no blocks are ever changed. "
                        + "Players at or below the level burn like in real lava.",
                NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
    }

    private Component toggleLine(String label, boolean on, String subcommand,
                                 String enableHover, String disableHover) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(on
                        ? Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("OFF", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(on
                        ? button("[Disable]", NamedTextColor.RED, "/lavaraise set " + subcommand + " off",
                                disableHover)
                        : button("[Enable]", NamedTextColor.GREEN, "/lavaraise set " + subcommand + " on",
                                enableHover));
    }

    private Component button(String text, NamedTextColor color, String command, String hover) {
        return Component.text(text, color, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(hover)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private Component clickValue(String value, String suggest, String hover) {
        return Component.text(value, NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text(hover)))
                .clickEvent(ClickEvent.suggestCommand(suggest));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("end"))) {
            return TIME_SUGGESTIONS.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("water")
                || args[0].equalsIgnoreCase("blocks") || args[0].equalsIgnoreCase("mobs"))) {
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
