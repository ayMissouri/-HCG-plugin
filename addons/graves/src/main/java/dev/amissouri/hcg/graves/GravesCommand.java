package dev.amissouri.hcg.graves;
import dev.amissouri.hcg.Messages;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class GravesCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("on", "off", "status", "remove");
    private static final int REMOVE_RANGE = 20;

    private final GravesManager manager;

    public GravesCommand(GravesManager manager) {
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
            case "status" -> Messages.send(sender, "graves.status",
                    "state", manager.isEnabled() ? "enabled" : "disabled",
                    "count", String.valueOf(manager.count()));
            case "remove" -> removeTarget(sender);
            case "set" -> {
                if (args.length >= 2) {
                    switch (args[1].toLowerCase()) {
                        case "on" -> turnOn(sender);
                        case "off" -> turnOff(sender);
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

    private void turnOn(CommandSender sender) {
        if (manager.isEnabled()) {
            Messages.send(sender, "graves.already-enabled");
            return;
        }
        manager.setEnabled(true);
        Messages.broadcastOps("graves.enabled-broadcast");
    }

    private void turnOff(CommandSender sender) {
        if (!manager.isEnabled()) {
            Messages.send(sender, "graves.not-enabled");
            return;
        }
        manager.setEnabled(false);
        Messages.broadcastOps("graves.disabled-broadcast");
    }

    private void removeTarget(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return;
        }
        Block target = player.getTargetBlockExact(REMOVE_RANGE);
        GravesManager.Grave grave = manager.graveAt(target);
        if (grave == null) {
            Messages.send(sender, "graves.no-grave-in-sight", "range", String.valueOf(REMOVE_RANGE));
            return;
        }
        manager.forceRemove(target);
        Messages.send(sender, "graves.removed", "owner", grave.ownerName());
    }

    private void showMenu(CommandSender sender) {
        boolean enabled = manager.isEnabled();

        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Graves", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ---", NamedTextColor.DARK_GRAY)));

        Component toggle = enabled
                ? button("[Disable]", NamedTextColor.RED, "/graves set off",
                        "Deaths drop items and XP normally again")
                : button("[Enable]", NamedTextColor.GREEN, "/graves set on",
                        "Deaths store items and XP in a grave");
        sender.sendMessage(Component.text("Status: ", NamedTextColor.GRAY)
                .append(enabled
                        ? Component.text("ENABLED", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("DISABLED", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(toggle));

        sender.sendMessage(Component.text("Graves: ", NamedTextColor.GRAY)
                .append(Component.text(manager.count() + " placed", NamedTextColor.YELLOW))
                .append(Component.text("  "))
                .append(button("[Remove target]", NamedTextColor.LIGHT_PURPLE, "/graves remove",
                        "Force remove the grave you're looking at, its contents drop on the floor")));

        sender.sendMessage(Component.text(
                "On death a player head stores the victim's items and full XP. "
                        + "Only the owner can collect it (right-click or break it).",
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
        return List.of();
    }
}
