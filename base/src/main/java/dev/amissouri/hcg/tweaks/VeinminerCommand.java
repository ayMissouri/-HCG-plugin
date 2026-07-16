package dev.amissouri.hcg.tweaks;

import java.util.List;
import java.util.Locale;

import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.Messages;
import dev.amissouri.hcg.tweaks.VeinminerTweak.Durability;
import dev.amissouri.hcg.tweaks.VeinminerTweak.Mode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class VeinminerCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("on", "off", "mode", "hunger",
            "durability", "size", "tool", "sneak", "chance", "minlevel", "grant", "remove",
            "gui", "reload");
    private static final List<String> STATES = List.of("on", "off");

    private final VeinminerTweak tweak;
    private final VeinminerEnchant enchant;
    private final TweaksGui gui;
    private final HcgScheduler scheduler;

    public VeinminerCommand(VeinminerTweak tweak, VeinminerEnchant enchant, TweaksGui gui,
            HcgScheduler scheduler) {
        this.tweak = tweak;
        this.enchant = enchant;
        this.gui = gui;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showMenu(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> showMenu(sender);
            case "on" -> setEnabled(sender, true);
            case "off" -> setEnabled(sender, false);
            case "gui" -> openGui(sender);
            case "reload" -> {
                tweak.reload();
                Messages.send(sender, "tweaks.veinminer.reloaded");
            }
            case "mode" -> {
                Mode mode = parse(sender, args, Mode.class);
                if (mode != null) {
                    tweak.setMode(mode);
                    report(sender, "Mode", mode.name());
                }
            }
            case "durability" -> {
                Durability durability = parse(sender, args, Durability.class);
                if (durability != null) {
                    tweak.setDurability(durability);
                    report(sender, "Durability cost", durability.name());
                }
            }
            case "hunger" -> {
                Boolean value = state(sender, args);
                if (value != null) {
                    tweak.setHungerEnabled(value);
                    report(sender, "Hunger cost", onOff(value));
                }
            }
            case "tool" -> {
                Boolean value = state(sender, args);
                if (value != null) {
                    tweak.setRequireCorrectTool(value);
                    report(sender, "Require correct tool", onOff(value));
                }
            }
            case "sneak" -> {
                Boolean value = state(sender, args);
                if (value != null) {
                    tweak.setRequireSneak(value);
                    report(sender, "Sneak with enchant", onOff(value));
                }
            }
            case "size" -> {
                Integer value = number(sender, args, 1, 4096);
                if (value != null) {
                    tweak.setMaxVeinSize(value);
                    report(sender, "Max vein size", String.valueOf(tweak.maxVeinSize()));
                }
            }
            case "chance" -> {
                Integer value = number(sender, args, 0, 100);
                if (value != null) {
                    tweak.setEnchantChance(value);
                    report(sender, "Enchant chance", tweak.enchantChance() + "%");
                }
            }
            case "minlevel" -> {
                Integer value = number(sender, args, 1, 30);
                if (value != null) {
                    tweak.setEnchantMinLevel(value);
                    report(sender, "Enchant min level", String.valueOf(tweak.enchantMinLevel()));
                }
            }
            case "grant" -> applyEnchant(sender, args, true);
            case "remove" -> applyEnchant(sender, args, false);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void setEnabled(CommandSender sender, boolean on) {
        if (tweak.isEnabled() == on) {
            Messages.send(sender, "tweaks.already",
                    "tweak", tweak.displayName(), "state", on ? "enabled" : "disabled");
            return;
        }
        tweak.setEnabled(on);
        Messages.broadcastOps("tweaks.set",
                "tweak", tweak.displayName(), "state", on ? "enabled" : "disabled");
    }

    private void openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return;
        }
        if (!player.hasPermission("hcg.tweaks")) {
            Messages.send(sender, "general.no-permission");
            return;
        }
        gui.openTweak(player, tweak);
    }

    private void applyEnchant(CommandSender sender, String[] args, boolean grant) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Messages.send(sender, "general.player-not-online", "player", args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Messages.send(sender, "general.console-needs-player");
            return;
        }
        scheduler.entity(target, () -> {
            ItemStack item = target.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                Messages.send(sender, "commands.enchant.hold-item");
                return;
            }
            if (grant && !tweak.isEnchantable(item)) {
                Messages.send(sender, "tweaks.veinminer.not-enchantable");
                return;
            }
            if (!grant && !enchant.has(item)) {
                Messages.send(sender, "tweaks.veinminer.not-enchanted");
                return;
            }
            enchant.apply(item, grant ? 1 : 0);
            target.getInventory().setItemInMainHand(item);
            Messages.send(sender, grant ? "tweaks.veinminer.granted" : "tweaks.veinminer.removed",
                    "player", target.getName(), "name", enchant.displayName(1));
        });
    }

    private void showMenu(CommandSender sender) {
        boolean on = tweak.isEnabled();
        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text(tweak.displayName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ---", NamedTextColor.DARK_GRAY)));

        sender.sendMessage(Component.text("Status: ", NamedTextColor.GRAY)
                .append(on
                        ? Component.text("ENABLED", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("DISABLED", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(on
                        ? button("[Disable]", NamedTextColor.RED, "/veinminer off",
                                "Ores break one at a time again")
                        : button("[Enable]", NamedTextColor.GREEN, "/veinminer on",
                                "Breaking one ore breaks the whole vein")));

        line(sender, "Mode", tweak.mode().name(), "/veinminer mode ");
        line(sender, "Hunger cost", onOff(tweak.hungerEnabled()), "/veinminer hunger ");
        line(sender, "Durability cost", tweak.durability().name().replace('_', ' '),
                "/veinminer durability ");
        line(sender, "Max vein size", String.valueOf(tweak.maxVeinSize()), "/veinminer size ");
        line(sender, "Require correct tool", onOff(tweak.requireCorrectTool()), "/veinminer tool ");
        line(sender, "Sneak with enchant", onOff(tweak.requireSneak()), "/veinminer sneak ");
        line(sender, "Enchant chance", tweak.enchantChance() + "%", "/veinminer chance ");
        line(sender, "Enchant min level", String.valueOf(tweak.enchantMinLevel()),
                "/veinminer minlevel ");

        sender.sendMessage(Component.text("  ")
                .append(button("[Open GUI]", NamedTextColor.AQUA, "/veinminer gui",
                        "Manage every setting from a chest menu")));
    }

    private void line(CommandSender sender, String name, String value, String fill) {
        sender.sendMessage(Component.text(name + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.YELLOW))
                .append(Component.text("  "))
                .append(Component.text("[Change]", NamedTextColor.LIGHT_PURPLE)
                        .hoverEvent(HoverEvent.showText(Component.text("Fills in " + fill.trim())))
                        .clickEvent(ClickEvent.suggestCommand(fill))));
    }

    private Component button(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(hover)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private void report(CommandSender sender, String name, String value) {
        Messages.send(sender, "tweaks.veinminer.setting-set",
                "setting", name, "value", value.replace('_', ' '));
    }

    private <E extends Enum<E>> E parse(CommandSender sender, String[] args, Class<E> type) {
        if (args.length < 2) {
            Messages.send(sender, "general.missing-argument", "name", type.getSimpleName().toLowerCase(Locale.ROOT));
            return null;
        }
        try {
            return Enum.valueOf(type, args[1].toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            Messages.send(sender, "tweaks.veinminer.bad-value", "input", args[1],
                    "options", options(type.getEnumConstants()));
            return null;
        }
    }

    private Boolean state(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Messages.send(sender, "general.missing-argument", "name", "on|off");
            return null;
        }
        String value = args[1].toLowerCase(Locale.ROOT);
        if (!STATES.contains(value)) {
            Messages.send(sender, "tweaks.veinminer.bad-value", "input", args[1], "options", "on, off");
            return null;
        }
        return value.equals("on");
    }

    private Integer number(CommandSender sender, String[] args, int min, int max) {
        if (args.length < 2) {
            Messages.send(sender, "general.missing-argument", "name", "number");
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            Messages.send(sender, "general.not-a-number", "input", args[1]);
            return null;
        }
        if (value < min || value > max) {
            Messages.send(sender, "general.value-range",
                    "min", String.valueOf(min), "max", String.valueOf(max));
            return null;
        }
        return value;
    }

    private static <E extends Enum<E>> String options(E[] values) {
        StringBuilder builder = new StringBuilder();
        for (E value : values) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(value.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return builder.toString();
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2) {
            List<String> options = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "mode" -> List.of("shift", "enchant", "both");
                case "durability" -> List.of("per-block", "single");
                case "hunger", "tool", "sneak" -> STATES;
                case "grant", "remove" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
            return options.stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT)
                            .startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
