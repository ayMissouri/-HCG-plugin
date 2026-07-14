package dev.amissouri.hcg.lavaraise;
import dev.amissouri.hcg.Messages;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /volcano setcenter, marks the block you're looking at as the crater.
 * /volcano erupt [seconds].
 */
public final class VolcanoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("setcenter", "erupt", "stop", "schedule", "duration", "radius", "shake", "status");

    private final VolcanoManager manager;

    public VolcanoCommand(VolcanoManager manager) {
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
            case "setcenter" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, "general.only-players");
                    return true;
                }
                Block target = player.getTargetBlockExact(100);
                Location center = target != null
                        ? target.getLocation().add(0, 1, 0)
                        : player.getLocation();
                manager.setCenter(center);
                Messages.send(sender, "volcano.center-set",
                        "x", String.valueOf(center.getBlockX()),
                        "y", String.valueOf(center.getBlockY()),
                        "z", String.valueOf(center.getBlockZ()),
                        "world", center.getWorld().getName());
            }
            case "erupt" -> {
                int seconds = manager.durationSeconds();
                if (args.length >= 2) {
                    try {
                        seconds = Math.clamp(Integer.parseInt(args[1]), 3, 600);
                    } catch (NumberFormatException e) {
                        Messages.send(sender, "volcano.invalid-seconds", "input", args[1]);
                        return true;
                    }
                }
                if (manager.center() == null) {
                    Messages.send(sender, "volcano.no-center");
                } else if (!manager.erupt(seconds)) {
                    Messages.send(sender, "volcano.already-erupting");
                }
            }
            case "stop" -> {
                if (manager.isErupting()) {
                    manager.stop();
                } else {
                    Messages.send(sender, "volcano.not-erupting");
                }
            }
            case "schedule" -> {
                if (args.length < 2) {
                    return false;
                }
                if (args[1].equalsIgnoreCase("off")) {
                    manager.disableSchedule();
                    Messages.send(sender, "volcano.schedule-off");
                    return true;
                }
                int ticks = LavaRaiseManager.parseTime(args[1]);
                if (ticks < 0) {
                    Messages.send(sender, "volcano.invalid-time", "input", args[1]);
                    return true;
                }
                manager.setSchedule(ticks);
                Messages.send(sender, "volcano.schedule-set", "time", LavaRaiseManager.formatTime(ticks));
                if (manager.center() == null) {
                    Messages.send(sender, "volcano.schedule-no-center");
                }
            }
            case "duration" -> {
                if (args.length < 2) {
                    return false;
                }
                try {
                    int seconds = Math.clamp(Integer.parseInt(args[1]), 3, 600);
                    manager.setDurationSeconds(seconds);
                    Messages.send(sender, "volcano.duration-set", "seconds", String.valueOf(seconds));
                } catch (NumberFormatException e) {
                    Messages.send(sender, "general.not-a-number", "input", args[1]);
                }
            }
            case "radius" -> {
                if (args.length < 2) {
                    return false;
                }
                try {
                    int blocks = Math.clamp(Integer.parseInt(args[1]), 10, 1000);
                    manager.setShakeRadius(blocks);
                    Messages.send(sender, "volcano.radius-set", "blocks", String.valueOf(blocks));
                } catch (NumberFormatException e) {
                    Messages.send(sender, "general.not-a-number", "input", args[1]);
                }
            }
            case "shake" -> {
                if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
                    return false;
                }
                boolean on = args[1].equalsIgnoreCase("on");
                manager.setShakeEnabled(on);
                Messages.send(sender, "volcano.shake-set", "state", on ? "on" : "off");
            }
            case "status" -> {
                Location center = manager.center();
                String where = center == null ? "not set"
                        : center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ()
                                + " (" + center.getWorld().getName() + ")";
                Messages.send(sender, "volcano.status",
                        "state", manager.isErupting() ? "ERUPTING" : "quiet",
                        "center", where,
                        "seconds", String.valueOf(manager.durationSeconds()));
                Messages.send(sender, manager.isScheduled()
                                ? "volcano.status-schedule-on" : "volcano.status-schedule-off",
                        "time", LavaRaiseManager.formatTime(manager.eruptTime()));
            }
            // Hidden: menu buttons apply and redraw.
            case "ui" -> {
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

    private void showMenu(CommandSender sender) {
        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Volcano", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ---", NamedTextColor.DARK_GRAY)));

        Location center = manager.center();
        Component centerLine = Component.text("Center: ", NamedTextColor.GRAY)
                .append(center == null
                        ? Component.text("not set", NamedTextColor.RED)
                        : Component.text(center.getBlockX() + ", " + center.getBlockY() + ", "
                                + center.getBlockZ(), NamedTextColor.YELLOW))
                .append(Component.text("  "))
                .append(button("[Set to Crosshair]", NamedTextColor.AQUA, "/volcano ui setcenter",
                        "Set the center to the block you're looking at"));
        sender.sendMessage(centerLine);

        Component action = manager.isErupting()
                ? button("[Stop]", NamedTextColor.RED, "/volcano ui stop", "Calm the volcano")
                : button("[Erupt!]", NamedTextColor.GOLD, "/volcano ui erupt", "Trigger an eruption now");
        sender.sendMessage(Component.text("Status: ", NamedTextColor.GRAY)
                .append(manager.isErupting()
                        ? Component.text("ERUPTING", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                        : Component.text("QUIET", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(action));

        Component scheduleLine = Component.text("Schedule: ", NamedTextColor.GRAY);
        if (manager.isScheduled()) {
            scheduleLine = scheduleLine
                    .append(Component.text("daily at " + LavaRaiseManager.formatTime(manager.eruptTime()),
                            NamedTextColor.YELLOW)
                            .hoverEvent(HoverEvent.showText(Component.text("Click to type a new time")))
                            .clickEvent(ClickEvent.suggestCommand("/volcano schedule ")))
                    .append(Component.text("  "))
                    .append(button("[Disable]", NamedTextColor.RED, "/volcano ui schedule off",
                            "Back to manual eruptions only"));
        } else {
            scheduleLine = scheduleLine
                    .append(Component.text("manual only", NamedTextColor.YELLOW))
                    .append(Component.text("  "))
                    .append(Component.text("[Set Time]", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .hoverEvent(HoverEvent.showText(Component.text(
                                    "Erupt daily at a world-clock time (ticks, HH:MM, or noon/midnight/...)")))
                            .clickEvent(ClickEvent.suggestCommand("/volcano schedule ")));
        }
        sender.sendMessage(scheduleLine);

        int duration = manager.durationSeconds();
        sender.sendMessage(Component.text("Duration: ", NamedTextColor.GRAY)
                .append(Component.text(duration + "s", NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text("Click to type a duration")))
                        .clickEvent(ClickEvent.suggestCommand("/volcano duration ")))
                .append(Component.text("  "))
                .append(button("[-5s]", NamedTextColor.RED,
                        "/volcano ui duration " + Math.max(3, duration - 5),
                        "Set duration to " + Math.max(3, duration - 5) + "s"))
                .append(Component.text(" "))
                .append(button("[+5s]", NamedTextColor.GREEN,
                        "/volcano ui duration " + Math.min(600, duration + 5),
                        "Set duration to " + Math.min(600, duration + 5) + "s")));

        boolean shake = manager.isShakeEnabled();
        sender.sendMessage(Component.text("Screen shake: ", NamedTextColor.GRAY)
                .append(shake
                        ? Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("OFF", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(shake
                        ? button("[Disable]", NamedTextColor.RED, "/volcano ui shake off",
                                "Eruptions no longer shake screens")
                        : button("[Enable]", NamedTextColor.GREEN, "/volcano ui shake on",
                                "Eruptions shake nearby players' screens")));

        int radius = manager.shakeRadius();
        sender.sendMessage(Component.text("Shake radius: ", NamedTextColor.GRAY)
                .append(Component.text(radius + " blocks", NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text("Click to type a radius")))
                        .clickEvent(ClickEvent.suggestCommand("/volcano radius ")))
                .append(Component.text("  "))
                .append(button("[-25]", NamedTextColor.RED,
                        "/volcano ui radius " + Math.max(10, radius - 25),
                        "Set shake radius to " + Math.max(10, radius - 25) + " blocks"))
                .append(Component.text(" "))
                .append(button("[+25]", NamedTextColor.GREEN,
                        "/volcano ui radius " + Math.min(1000, radius + 25),
                        "Set shake radius to " + Math.min(1000, radius + 25) + " blocks")));
    }

    private Component button(String text, NamedTextColor color, String command, String hover) {
        return Component.text(text, color, TextDecoration.BOLD)
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
        if (args.length == 2 && (args[0].equalsIgnoreCase("erupt") || args[0].equalsIgnoreCase("duration"))) {
            return List.of("10", "20", "30", "60").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("radius")) {
            return List.of("100", "150", "200", "300", "500").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("shake")) {
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("schedule")) {
            return List.of("off", "day", "noon", "sunset", "night", "midnight", "sunrise").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
