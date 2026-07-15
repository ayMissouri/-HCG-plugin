package dev.amissouri.hcg.npcs;
import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.Messages;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /npc create|remove|list|info, manage NPCs.
 * /npc skin|displayname|equipment|glowing|collidable|showintab, appearance.
 * /npc movehere|rotate|teleport|turntoplayer, placement.
 * /npc action|cooldown, click-triggered actions.
 */
public final class NpcCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "remove", "list", "info", "skin", "displayname", "equipment",
            "glowing", "collidable", "showintab", "movehere", "rotate", "teleport",
            "turntoplayer", "action", "cooldown");

    private static final List<String> COLORS = List.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white");

    private static final List<String> ACTION_TYPES = List.of(
            "message", "player_command", "console_command", "sound", "wait");

    private final JavaPlugin plugin;
    private final HcgScheduler scheduler;
    private final NpcManager manager;

    public NpcCommand(JavaPlugin plugin, HcgScheduler scheduler, NpcManager manager) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!manager.isAvailable()) {
            Messages.send(sender, "npc.unavailable");
            return true;
        }
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("list")) {
            list(sender);
            return true;
        }
        if (sub.equals("create")) {
            if (args.length < 2) {
                return false;
            }
            create(sender, args[1]);
            return true;
        }
        if (args.length < 2) {
            return false;
        }
        NpcManager.Npc npc = manager.get(args[1]);
        if (npc == null) {
            Messages.send(sender, "npc.not-found", "name", args[1]);
            return true;
        }

        switch (sub) {
            case "remove" -> {
                manager.delete(npc);
                Messages.send(sender, "npc.removed", "name", npc.data.name());
            }
            case "info" -> info(sender, npc);
            case "skin" -> {
                if (args.length < 3) {
                    return false;
                }
                skin(sender, npc, args[2]);
            }
            case "displayname" -> {
                if (args.length < 3) {
                    return false;
                }
                displayName(sender, npc, String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            }
            case "equipment" -> {
                if (args.length < 3) {
                    return false;
                }
                return equipment(sender, npc, Arrays.copyOfRange(args, 2, args.length));
            }
            case "glowing" -> {
                if (args.length < 3) {
                    return false;
                }
                glowing(sender, npc, args[2]);
            }
            case "collidable" -> {
                Boolean state = parseOnOff(args, 2);
                if (state == null) {
                    return false;
                }
                npc.data.setCollidable(state);
                manager.applyCollidable(npc);
                Messages.send(sender, "npc.collidable-set",
                        "name", npc.data.name(), "state", state ? "on" : "off");
            }
            case "showintab" -> {
                Boolean state = parseOnOff(args, 2);
                if (state == null) {
                    return false;
                }
                npc.data.setShowInTab(state);
                manager.save();
                manager.rebuild(npc);
                Messages.send(sender, "npc.showintab-set",
                        "name", npc.data.name(), "state", state ? "on" : "off");
            }
            case "turntoplayer" -> {
                Boolean state = parseOnOff(args, 2);
                if (state == null) {
                    return false;
                }
                return turnToPlayer(sender, npc, state, args);
            }
            case "movehere" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, "general.only-players");
                    return true;
                }
                manager.applyLocation(npc, player.getLocation());
                Messages.send(sender, "npc.moved", "name", npc.data.name());
            }
            case "rotate" -> {
                if (args.length < 4) {
                    return false;
                }
                return rotate(sender, npc, args[2], args[3]);
            }
            case "teleport" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, "general.only-players");
                    return true;
                }
                Location location = npc.data.location();
                if (location == null) {
                    Messages.send(sender, "npc.world-not-loaded",
                            "name", npc.data.name(), "world", npc.data.worldName());
                    return true;
                }
                player.teleportAsync(location);
                Messages.send(sender, "npc.teleported", "name", npc.data.name());
            }
            case "cooldown" -> {
                if (args.length < 3) {
                    return false;
                }
                return cooldown(sender, npc, args[2]);
            }
            case "action" -> {
                if (args.length < 4) {
                    return false;
                }
                return action(sender, npc, Arrays.copyOfRange(args, 2, args.length));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- subcommands

    private void create(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return;
        }
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
            Messages.send(sender, "npc.invalid-name");
            return;
        }
        if (manager.get(name) != null) {
            Messages.send(sender, "npc.already-exists", "name", name);
            return;
        }
        manager.create(name, player.getLocation());
        Messages.send(sender, "npc.created", "name", name);
    }

    private void list(CommandSender sender) {
        if (manager.all().isEmpty()) {
            Messages.send(sender, "npc.list-empty");
            return;
        }
        Messages.send(sender, "npc.list-header", "count", String.valueOf(manager.all().size()));
        for (NpcManager.Npc npc : manager.all()) {
            Messages.send(sender, "npc.list-entry",
                    "name", npc.data.name(),
                    "world", npc.data.worldName(),
                    "x", String.valueOf((int) Math.floor(npc.data.location() == null ? 0 : npc.data.location().getX())),
                    "y", String.valueOf((int) Math.floor(npc.data.location() == null ? 0 : npc.data.location().getY())),
                    "z", String.valueOf((int) Math.floor(npc.data.location() == null ? 0 : npc.data.location().getZ())));
        }
    }

    private void info(CommandSender sender, NpcManager.Npc npc) {
        NpcData data = npc.data;
        Location location = data.location();
        Messages.send(sender, "npc.info-header", "name", data.name());
        Messages.send(sender, "npc.info-location",
                "world", data.worldName(),
                "x", location == null ? "?" : String.valueOf((int) Math.floor(location.getX())),
                "y", location == null ? "?" : String.valueOf((int) Math.floor(location.getY())),
                "z", location == null ? "?" : String.valueOf((int) Math.floor(location.getZ())));
        Messages.send(sender, "npc.info-appearance",
                "displayname", data.hasHologram() ? data.displayName() : "hidden",
                "skin", data.skinSource() == null ? "default" : data.skinSource(),
                "glowing", data.isGlowing() ? data.glowColor() : "off");
        int actionCount = data.allActions().values().stream().mapToInt(List::size).sum();
        Messages.send(sender, "npc.info-behavior",
                "turn", data.isTurnToPlayer()
                        ? "on (" + (int) data.turnDistance() + " blocks)" : "off",
                "collidable", data.isCollidable() ? "on" : "off",
                "tab", data.isShowInTab() ? "on" : "off",
                "cooldown", data.cooldownMillis() == 0 ? "none"
                        : (data.cooldownMillis() / 1000.0) + "s",
                "actions", String.valueOf(actionCount));
    }

    private void skin(CommandSender sender, NpcManager.Npc npc, String input) {
        if (input.equalsIgnoreCase("reset")) {
            npc.data.setSkin(null, null, null);
            manager.save();
            manager.rebuild(npc);
            Messages.send(sender, "npc.skin-reset", "name", npc.data.name());
            return;
        }
        Messages.send(sender, "npc.skin-fetching", "name", npc.data.name());
        String apiKey = plugin.getConfig().getString("npc.mineskin-api-key", "");
        String source = SkinFetcher.looksLikeUrl(input) ? "URL" : input;
        SkinFetcher.fetch(input, apiKey).whenComplete((skin, error) ->
                scheduler.global(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        Messages.send(sender, "npc.skin-failed", "reason", cause.getMessage());
                        return;
                    }
                    NpcManager.Npc current = manager.get(npc.data.name());
                    if (current == null) {
                        return;
                    }
                    current.data.setSkin(skin.value(), skin.signature(), source);
                    manager.save();
                    manager.rebuild(current);
                    Messages.send(sender, "npc.skin-set", "name", current.data.name(), "source", source);
                }));
    }

    private void displayName(CommandSender sender, NpcManager.Npc npc, String text) {
        npc.data.setDisplayName(text);
        manager.applyDisplayName(npc);
        if (npc.data.hasHologram()) {
            Messages.send(sender, "npc.displayname-set", "name", npc.data.name(), "displayname", text);
        } else {
            Messages.send(sender, "npc.displayname-hidden", "name", npc.data.name());
        }
    }

    private boolean equipment(CommandSender sender, NpcManager.Npc npc, String[] args) {
        String mode = args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("list")) {
            if (npc.data.equipment().isEmpty()) {
                Messages.send(sender, "npc.equipment-none", "name", npc.data.name());
                return true;
            }
            Messages.send(sender, "npc.equipment-header", "name", npc.data.name());
            npc.data.equipment().forEach((slot, item) ->
                    Messages.send(sender, "npc.equipment-entry",
                            "slot", slot, "item", itemName(item)));
            return true;
        }
        if (args.length < 2) {
            return false;
        }
        String slot = args[1].toLowerCase(Locale.ROOT);
        if (!NpcData.SLOTS.contains(slot)) {
            Messages.send(sender, "npc.invalid-slot", "input", args[1]);
            return true;
        }
        switch (mode) {
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, "general.only-players");
                    return true;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() == Material.AIR) {
                    npc.data.equipment().remove(slot);
                    manager.applyEquipment(npc);
                    Messages.send(sender, "npc.equipment-cleared", "name", npc.data.name(), "slot", slot);
                    return true;
                }
                npc.data.equipment().put(slot, held.clone());
                manager.applyEquipment(npc);
                Messages.send(sender, "npc.equipment-set",
                        "name", npc.data.name(), "slot", slot, "item", itemName(held));
            }
            case "clear" -> {
                npc.data.equipment().remove(slot);
                manager.applyEquipment(npc);
                Messages.send(sender, "npc.equipment-cleared", "name", npc.data.name(), "slot", slot);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void glowing(CommandSender sender, NpcManager.Npc npc, String input) {
        String color = input.toLowerCase(Locale.ROOT);
        if (!color.equals("off") && !COLORS.contains(color)) {
            Messages.send(sender, "npc.invalid-color", "input", input);
            return;
        }
        npc.data.setGlowColor(color);
        manager.applyGlowing(npc);
        if (color.equals("off")) {
            Messages.send(sender, "npc.glowing-off", "name", npc.data.name());
        } else {
            Messages.send(sender, "npc.glowing-set", "name", npc.data.name(), "color", color);
        }
    }

    private boolean turnToPlayer(CommandSender sender, NpcManager.Npc npc, boolean state, String[] args) {
        double distance = npc.data.turnDistance();
        if (args.length >= 4) {
            try {
                distance = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                Messages.send(sender, "general.not-a-number", "input", args[3]);
                return true;
            }
            if (distance < 1 || distance > 64) {
                Messages.send(sender, "general.value-range", "min", "1", "max", "64");
                return true;
            }
        }
        npc.data.setTurnToPlayer(state);
        npc.data.setTurnDistance(distance);
        manager.save();
        if (!state) {
            manager.applyRotation(npc, npc.data.location() == null ? 0
                    : npc.data.location().getYaw(), npc.data.location() == null ? 0
                    : npc.data.location().getPitch());
        }
        Messages.send(sender, "npc.turn-set",
                "name", npc.data.name(),
                "state", state ? "on" : "off",
                "distance", state ? " (" + (int) distance + " blocks)" : "");
        return true;
    }

    private boolean rotate(CommandSender sender, NpcManager.Npc npc, String yawText, String pitchText) {
        float yaw;
        float pitch;
        try {
            yaw = Float.parseFloat(yawText);
            pitch = Float.parseFloat(pitchText);
        } catch (NumberFormatException e) {
            Messages.send(sender, "general.not-a-number",
                    "input", yawText + " " + pitchText);
            return true;
        }
        pitch = Math.clamp(pitch, -90, 90);
        manager.applyRotation(npc, yaw, pitch);
        Messages.send(sender, "npc.rotated", "name", npc.data.name(),
                "yaw", String.valueOf((int) yaw), "pitch", String.valueOf((int) pitch));
        return true;
    }

    private boolean cooldown(CommandSender sender, NpcManager.Npc npc, String input) {
        double seconds;
        try {
            seconds = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            Messages.send(sender, "general.not-a-number", "input", input);
            return true;
        }
        if (seconds < 0 || seconds > 86400) {
            Messages.send(sender, "general.value-range", "min", "0", "max", "86400");
            return true;
        }
        npc.data.setCooldownMillis((long) (seconds * 1000));
        manager.save();
        Messages.send(sender, "npc.cooldown-set", "name", npc.data.name(),
                "seconds", String.valueOf(seconds));
        return true;
    }

    private boolean action(CommandSender sender, NpcManager.Npc npc, String[] args) {
        String trigger = args[0].toLowerCase(Locale.ROOT);
        if (!NpcData.TRIGGERS.contains(trigger)) {
            Messages.send(sender, "npc.invalid-trigger", "input", args[0]);
            return true;
        }
        List<String> actions = npc.data.actions(trigger);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 4) {
                    return false;
                }
                String type = args[2].toLowerCase(Locale.ROOT);
                if (!ACTION_TYPES.contains(type)) {
                    Messages.send(sender, "npc.invalid-action-type", "input", args[2]);
                    return true;
                }
                String entry = type + " " + String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                actions.add(entry);
                manager.save();
                Messages.send(sender, "npc.action-added",
                        "name", npc.data.name(), "trigger", trigger,
                        "index", String.valueOf(actions.size()), "action", entry);
            }
            case "remove" -> {
                if (args.length < 3) {
                    return false;
                }
                int index;
                try {
                    index = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    Messages.send(sender, "general.not-a-number", "input", args[2]);
                    return true;
                }
                if (index < 1 || index > actions.size()) {
                    Messages.send(sender, "npc.action-invalid-index",
                            "input", args[2], "name", npc.data.name(),
                            "count", String.valueOf(actions.size()), "trigger", trigger);
                    return true;
                }
                actions.remove(index - 1);
                manager.save();
                Messages.send(sender, "npc.action-removed",
                        "name", npc.data.name(), "trigger", trigger, "index", String.valueOf(index));
            }
            case "clear" -> {
                int count = actions.size();
                actions.clear();
                manager.save();
                Messages.send(sender, "npc.actions-cleared",
                        "name", npc.data.name(), "trigger", trigger, "count", String.valueOf(count));
            }
            case "list" -> {
                if (actions.isEmpty()) {
                    Messages.send(sender, "npc.actions-empty",
                            "name", npc.data.name(), "trigger", trigger);
                    return true;
                }
                Messages.send(sender, "npc.actions-header",
                        "name", npc.data.name(), "trigger", trigger,
                        "count", String.valueOf(actions.size()));
                for (int i = 0; i < actions.size(); i++) {
                    Messages.send(sender, "npc.actions-entry",
                            "index", String.valueOf(i + 1), "action", actions.get(i));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private static Boolean parseOnOff(String[] args, int index) {
        if (args.length <= index) {
            return null;
        }
        return switch (args[index].toLowerCase(Locale.ROOT)) {
            case "on", "true" -> true;
            case "off", "false" -> false;
            default -> null;
        };
    }

    private static String itemName(ItemStack item) {
        return item.getType().name().toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && !sub.equals("create") && !sub.equals("list")) {
            return filter(manager.names(), args[1]);
        }
        NpcManager.Npc npc = args.length >= 2 ? manager.get(args[1]) : null;
        if (args.length == 3) {
            return switch (sub) {
                case "skin" -> {
                    List<String> options = new java.util.ArrayList<>(List.of("reset"));
                    Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                    yield filter(options, args[2]);
                }
                case "displayname" -> filter(List.of("none"), args[2]);
                case "equipment" -> filter(List.of("set", "clear", "list"), args[2]);
                case "glowing" -> {
                    List<String> options = new java.util.ArrayList<>(COLORS);
                    options.add("off");
                    yield filter(options, args[2]);
                }
                case "collidable", "showintab", "turntoplayer" -> filter(List.of("on", "off"), args[2]);
                case "action" -> filter(NpcData.TRIGGERS, args[2]);
                case "cooldown" -> filter(List.of("0", "1", "5", "30"), args[2]);
                case "rotate" -> filter(List.of("0", "90", "180", "-90"), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4) {
            return switch (sub) {
                case "equipment" -> filter(NpcData.SLOTS, args[3]);
                case "action" -> filter(List.of("add", "remove", "list", "clear"), args[3]);
                case "turntoplayer" -> filter(List.of("5", "10", "20"), args[3]);
                case "rotate" -> filter(List.of("0", "15", "-15"), args[3]);
                default -> List.of();
            };
        }
        if (args.length == 5 && sub.equals("action")) {
            if (args[3].equalsIgnoreCase("add")) {
                return filter(ACTION_TYPES, args[4]);
            }
            if (args[3].equalsIgnoreCase("remove") && npc != null
                    && NpcData.TRIGGERS.contains(args[2].toLowerCase(Locale.ROOT))) {
                int count = npc.data.actions(args[2].toLowerCase(Locale.ROOT)).size();
                return IntStream.rangeClosed(1, count)
                        .mapToObj(String::valueOf)
                        .filter(s -> s.startsWith(args[4]))
                        .toList();
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
