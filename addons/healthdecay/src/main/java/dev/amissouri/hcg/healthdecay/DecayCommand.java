package dev.amissouri.hcg.healthdecay;
import dev.amissouri.hcg.HcgText;
import dev.amissouri.hcg.Messages;
import org.bukkit.plugin.java.JavaPlugin;

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

public final class DecayCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("on", "off", "status", "restore", "interval", "amount");

    private static final long INTERVAL_STEP = 10;
    private static final long INTERVAL_MIN = 5;
    private static final double AMOUNT_STEP = 0.5;
    private static final double AMOUNT_MIN = 0.5;
    private static final double AMOUNT_MAX = 10.0;

    private final JavaPlugin plugin;
    private final DecayManager decayManager;

    public DecayCommand(JavaPlugin plugin, DecayManager decayManager) {
        this.plugin = plugin;
        this.decayManager = decayManager;
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
            case "status" -> {
                Messages.send(sender, "healthdecay.status-state",
                        "state", decayManager.isRunning() ? "running" : "stopped");
                Messages.send(sender, "healthdecay.status-health",
                        "current", HcgText.formatHearts(decayManager.currentMaxHp()),
                        "floor", HcgText.formatHearts(decayManager.minimumHp()));
                Messages.send(sender, "healthdecay.status-rate",
                        "amount", HcgText.formatHearts(decayManager.decayAmountHp()),
                        "seconds", String.valueOf(plugin.getConfig().getLong("decay.interval-seconds", 60)));
            }
            case "restore" -> {
                restore();
                Messages.send(sender, "healthdecay.restored");
            }
            case "interval" -> {
                Long seconds = parsePositive(args, sender, "seconds");
                if (seconds != null) {
                    setInterval(seconds);
                    Messages.send(sender, "healthdecay.interval-set", "seconds", String.valueOf(seconds));
                }
            }
            case "amount" -> {
                Double hearts = parsePositiveDouble(args, sender, "hearts");
                if (hearts != null) {
                    setAmount(hearts);
                    Messages.send(sender, "healthdecay.amount-set", "hearts", String.valueOf(hearts));
                }
            }
            case "set" -> {
                handleSet(sender, args);
                showMenu(sender);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void turnOn(CommandSender sender) {
        if (decayManager.isRunning()) {
            Messages.send(sender, "healthdecay.already-running");
            return;
        }
        decayManager.start();
        Messages.broadcastOps("healthdecay.start-broadcast",
                "hearts", HcgText.formatHearts(decayManager.minimumHp()));
    }

    private void turnOff(CommandSender sender) {
        if (!decayManager.isRunning()) {
            Messages.send(sender, "healthdecay.not-running");
            return;
        }
        decayManager.stop();
        Messages.broadcastOps("healthdecay.stop-broadcast");
    }

    private void restore() {
        decayManager.resetHealth();
        decayManager.restartTimer();
    }

    private void setInterval(long seconds) {
        plugin.getConfig().set("decay.interval-seconds", seconds);
        plugin.saveConfig();
        decayManager.restartTimer();
    }

    private void setAmount(double hearts) {
        plugin.getConfig().set("decay.amount-hearts", hearts);
        plugin.saveConfig();
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return;
        }
        switch (args[1].toLowerCase()) {
            case "on" -> turnOn(sender);
            case "off" -> turnOff(sender);
            case "restore" -> {
                restore();
                Messages.send(sender, "healthdecay.restored");
            }
            case "interval" -> {
                if (args.length >= 3) {
                    try {
                        setInterval(Math.max(1, Long.parseLong(args[2])));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            case "amount" -> {
                if (args.length >= 3) {
                    try {
                        setAmount(Math.clamp(Double.parseDouble(args[2]), AMOUNT_MIN, AMOUNT_MAX));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            default -> { }
        }
    }

    private void showMenu(CommandSender sender) {
        boolean running = decayManager.isRunning();
        long interval = plugin.getConfig().getLong("decay.interval-seconds", 60);
        double amount = plugin.getConfig().getDouble("decay.amount-hearts", 0.5);

        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Health Decay", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ---", NamedTextColor.DARK_GRAY)));

        Component toggle = running
                ? button("[Stop]", NamedTextColor.RED, "/healthdecay set off",
                        "Stop the decay and restore everyone")
                : button("[Start]", NamedTextColor.GREEN, "/healthdecay set on",
                        "Start the decay game mode");
        sender.sendMessage(Component.text("Status: ", NamedTextColor.GRAY)
                .append(running
                        ? Component.text("RUNNING", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("STOPPED", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(toggle));

        sender.sendMessage(Component.text("Max health: ", NamedTextColor.GRAY)
                .append(Component.text(
                        HcgText.formatHearts(decayManager.currentMaxHp()) + " / "
                                + HcgText.formatHearts(decayManager.maximumHp()) + " hearts",
                        NamedTextColor.YELLOW))
                .append(Component.text(" (floor " + HcgText.formatHearts(decayManager.minimumHp()) + ")",
                        NamedTextColor.DARK_GRAY))
                .append(Component.text("  "))
                .append(button("[Restore]", NamedTextColor.AQUA, "/healthdecay set restore",
                        "Restore everyone's health now")));

        long down = Math.max(INTERVAL_MIN, interval - INTERVAL_STEP);
        long up = interval + INTERVAL_STEP;
        sender.sendMessage(Component.text("Interval: ", NamedTextColor.GRAY)
                .append(Component.text(interval + "s", NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text("Click to type a custom interval")))
                        .clickEvent(ClickEvent.suggestCommand("/healthdecay interval ")))
                .append(Component.text("  "))
                .append(button("[-" + INTERVAL_STEP + "s]", NamedTextColor.RED,
                        "/healthdecay set interval " + down, "Set interval to " + down + "s"))
                .append(Component.text(" "))
                .append(button("[+" + INTERVAL_STEP + "s]", NamedTextColor.GREEN,
                        "/healthdecay set interval " + up, "Set interval to " + up + "s")));

        double amountDown = Math.max(AMOUNT_MIN, amount - AMOUNT_STEP);
        double amountUp = Math.min(AMOUNT_MAX, amount + AMOUNT_STEP);
        sender.sendMessage(Component.text("Amount: ", NamedTextColor.GRAY)
                .append(Component.text(fmt(amount) + " heart(s) per tick", NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text("Click to type a custom amount")))
                        .clickEvent(ClickEvent.suggestCommand("/healthdecay amount ")))
                .append(Component.text("  "))
                .append(button("[-" + fmt(AMOUNT_STEP) + "]", NamedTextColor.RED,
                        "/healthdecay set amount " + amountDown, "Set amount to " + fmt(amountDown)))
                .append(Component.text(" "))
                .append(button("[+" + fmt(AMOUNT_STEP) + "]", NamedTextColor.GREEN,
                        "/healthdecay set amount " + amountUp, "Set amount to " + fmt(amountUp))));

        sender.sendMessage(Component.text("Click the buttons to change settings.", NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.ITALIC));
    }

    private Component button(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(hover)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private static String fmt(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private Long parsePositive(String[] args, CommandSender sender, String name) {
        if (args.length < 2) {
            Messages.send(sender, "general.missing-argument", "name", name);
            return null;
        }
        try {
            long value = Long.parseLong(args[1]);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            Messages.send(sender, "general.not-positive", "input", args[1]);
            return null;
        }
    }

    private Double parsePositiveDouble(String[] args, CommandSender sender, String name) {
        if (args.length < 2) {
            Messages.send(sender, "general.missing-argument", "name", name);
            return null;
        }
        try {
            double value = Double.parseDouble(args[1]);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            Messages.send(sender, "general.not-positive", "input", args[1]);
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
