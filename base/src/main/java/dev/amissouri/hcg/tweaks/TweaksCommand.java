package dev.amissouri.hcg.tweaks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.amissouri.hcg.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class TweaksCommand implements CommandExecutor, TabCompleter {

    private static final List<String> STATES = List.of("on", "off");

    private final TweaksManager manager;
    private final TweaksGui gui;

    public TweaksCommand(TweaksManager manager, TweaksGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                gui.openRoot(player);
            } else {
                showList(sender);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            showList(sender);
            return true;
        }
        Tweak tweak = manager.get(args[0]);
        if (tweak == null) {
            Messages.send(sender, "tweaks.unknown", "input", args[0]);
            return true;
        }
        if (args.length == 1) {
            Messages.send(sender, "tweaks.status",
                    "tweak", tweak.displayName(), "state", state(tweak.isEnabled()));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!STATES.contains(action)) {
            Messages.send(sender, "tweaks.unknown-state", "input", args[1]);
            return true;
        }
        boolean on = action.equals("on");
        if (on == tweak.isEnabled()) {
            Messages.send(sender, "tweaks.already",
                    "tweak", tweak.displayName(), "state", state(on));
            return true;
        }
        tweak.setEnabled(on);
        Messages.broadcastOps("tweaks.set", "tweak", tweak.displayName(), "state", state(on));
        return true;
    }

    private void showList(CommandSender sender) {
        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Tweaks", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ---", NamedTextColor.DARK_GRAY)));
        for (Tweak tweak : manager.all()) {
            boolean on = tweak.isEnabled();
            sender.sendMessage(Component.text(tweak.displayName() + ": ", NamedTextColor.GRAY)
                    .append(on
                            ? Component.text("ENABLED", NamedTextColor.GREEN, TextDecoration.BOLD)
                            : Component.text("DISABLED", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text("  "))
                    .append(button(on ? "[Disable]" : "[Enable]",
                            on ? NamedTextColor.RED : NamedTextColor.GREEN,
                            "/tweaks " + tweak.id() + (on ? " off" : " on"),
                            "Turn " + tweak.displayName() + (on ? " off" : " on")))
                    .append(Component.text("  "))
                    .append(button("[Settings]", NamedTextColor.AQUA, tweak.command(),
                            "Open " + tweak.displayName() + "'s own menu")));
        }
    }

    private Component button(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(hover)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private static String state(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("list");
            manager.all().forEach(tweak -> options.add(tweak.id()));
            return options.stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && manager.get(args[0]) != null) {
            return STATES.stream()
                    .filter(state -> state.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
