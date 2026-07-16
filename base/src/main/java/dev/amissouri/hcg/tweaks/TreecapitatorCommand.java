package dev.amissouri.hcg.tweaks;

import java.util.List;
import java.util.Locale;

import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.Messages;
import dev.amissouri.hcg.tweaks.TreecapitatorTweak.Durability;
import dev.amissouri.hcg.tweaks.TreecapitatorTweak.Mode;
import dev.amissouri.hcg.tweaks.TreecapitatorTweak.Scope;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class TreecapitatorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("on", "off", "mode", "scope",
            "animation", "decay", "replant", "leaves", "axe", "hunger", "durability", "size",
            "sneak", "chance", "minlevel", "grant", "remove", "gui", "reload");
    private static final List<String> STATES = List.of("on", "off");

    private final TreecapitatorTweak tweak;
    private final TweakEnchant enchant;
    private final TweaksGui gui;
    private final HcgScheduler scheduler;

    public TreecapitatorCommand(TreecapitatorTweak tweak, TweakEnchant enchant, TweaksGui gui,
            HcgScheduler scheduler) {
        this.tweak = tweak;
        this.enchant = enchant;
        this.gui = gui;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            openOrStatus(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu", "gui" -> openOrStatus(sender);
            case "on" -> setEnabled(sender, true);
            case "off" -> setEnabled(sender, false);
            case "reload" -> {
                tweak.reload();
                Messages.send(sender, "tweaks.treecapitator.reloaded");
            }
            case "mode" -> {
                Mode mode = parse(sender, args, Mode.class);
                if (mode != null) {
                    tweak.setMode(mode);
                    report(sender, "Mode", mode.name());
                }
            }
            case "scope" -> {
                Scope scope = parse(sender, args, Scope.class);
                if (scope != null) {
                    tweak.setScope(scope);
                    report(sender, "Fell scope", scope.name());
                }
            }
            case "durability" -> {
                Durability durability = parse(sender, args, Durability.class);
                if (durability != null) {
                    tweak.setDurability(durability);
                    report(sender, "Durability cost", durability.name());
                }
            }
            case "animation" -> {
                Boolean value = state(sender, args);
                if (value != null) {
                    tweak.setAnimation(value);
                    report(sender, "Falling animation", onOff(value));
                }
            }
            case "decay" -> {
                Boolean value = state(sender, args);
                if (value != null) {
                    tweak.setFastLeafDecay(value);
                    report(sender, "Fast leaf decay", onOff(value));
                }
            }
            case "replant" -> {
                Boolean value = state(sender, args);
                if (value != null) {
                    tweak.setReplantSapling(value);
                    report(sender, "Replant sapling", onOff(value));
                }
            }
            case "leaves" -> {
                Boolean value = state(sender, args);
                if (value != null) {
                    tweak.setRequireLeaves(value);
                    report(sender, "Require leaves", onOff(value));
                }
            }
            case "axe" -> {
                Boolean value = state(sender, args);
                if (value != null) {
                    tweak.setRequireAxe(value);
                    report(sender, "Require axe", onOff(value));
                }
            }
            case "hunger" -> {
                Boolean value = state(sender, args);
                if (value != null) {
                    tweak.setHungerEnabled(value);
                    report(sender, "Hunger cost", onOff(value));
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
                    tweak.setMaxTreeSize(value);
                    report(sender, "Max tree size", String.valueOf(tweak.maxTreeSize()));
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

    private void openOrStatus(CommandSender sender) {
        if (sender instanceof Player player) {
            openGui(player);
        } else {
            sendStatus(sender);
        }
    }

    private void openGui(Player player) {
        if (!player.hasPermission("hcg.tweaks")) {
            Messages.send(player, "general.no-permission");
            return;
        }
        gui.openTweak(player, tweak);
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
                Messages.send(sender, "tweaks.treecapitator.not-enchantable");
                return;
            }
            if (!grant && !enchant.has(item)) {
                Messages.send(sender, "tweaks.treecapitator.not-enchanted");
                return;
            }
            enchant.apply(item, grant ? 1 : 0);
            target.getInventory().setItemInMainHand(item);
            Messages.send(sender, grant ? "tweaks.treecapitator.granted" : "tweaks.treecapitator.removed",
                    "player", target.getName(), "name", enchant.displayName(1));
        });
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(Component.text("--- ", NamedTextColor.DARK_GRAY)
                .append(Component.text(tweak.displayName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ---", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("Status: ", NamedTextColor.GRAY)
                .append(tweak.isEnabled()
                        ? Component.text("ENABLED", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("DISABLED", NamedTextColor.RED, TextDecoration.BOLD)));
        line(sender, "Mode", tweak.mode().name());
        line(sender, "Fell scope", tweak.scope().name().replace('_', ' '));
        line(sender, "Falling animation", onOff(tweak.animation()));
        line(sender, "Fast leaf decay", onOff(tweak.fastLeafDecay()));
        line(sender, "Replant sapling", onOff(tweak.replantSapling()));
        line(sender, "Require leaves", onOff(tweak.requireLeaves()));
        line(sender, "Require axe", onOff(tweak.requireAxe()));
        line(sender, "Hunger cost", onOff(tweak.hungerEnabled()));
        line(sender, "Durability cost", tweak.durability().name().replace('_', ' '));
        line(sender, "Max tree size", String.valueOf(tweak.maxTreeSize()));
        line(sender, "Sneak with enchant", onOff(tweak.requireSneak()));
        line(sender, "Enchant chance", tweak.enchantChance() + "%");
        line(sender, "Enchant min level", String.valueOf(tweak.enchantMinLevel()));
        sender.sendMessage(Component.text("Change with /treecapitator <setting> <value>, "
                + "or open the chest menu in-game with /treecapitator.", NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.ITALIC));
    }

    private void line(CommandSender sender, String name, String value) {
        sender.sendMessage(Component.text(name + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.YELLOW)));
    }

    private void report(CommandSender sender, String name, String value) {
        Messages.send(sender, "tweaks.treecapitator.setting-set",
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
            Messages.send(sender, "tweaks.treecapitator.bad-value", "input", args[1],
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
            Messages.send(sender, "tweaks.treecapitator.bad-value", "input", args[1], "options", "on, off");
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
                case "scope" -> List.of("whole-tree", "above");
                case "durability" -> List.of("per-block", "single");
                case "animation", "decay", "replant", "leaves", "axe", "hunger", "sneak" -> STATES;
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
