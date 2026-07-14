package dev.amissouri.hcg.hungergames;
import dev.amissouri.hcg.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /hungergames addspawn|delspawn|spawns|clearspawns, manage spawn points.
 * /hungergames scatter, teleport all online players to random distinct spawns.
 * /hungergames setcenter|start|stop|status, run the border sequence.
 */
public final class HungerGamesCommand implements CommandExecutor, TabCompleter {

    private record Setting(String key, int min, int max, String label, List<String> suggestions) {}

    private static final Map<String, Setting> SETTINGS = Map.of(
            "countdown", new Setting("countdown-seconds", 3, 3600, "Starting countdown",
                    List.of("30", "60", "120", "300")),
            "startsize", new Setting("border.start-size", 10, 100000, "Starting border size",
                    List.of("50", "100", "200")),
            "expandsize", new Setting("border.expanded-size", 10, 100000, "Expanded border size",
                    List.of("500", "1000", "2000")),
            "finalsize", new Setting("border.final-size", 1, 100000, "Final border size",
                    List.of("10", "30", "50")),
            "expandtime", new Setting("border.expand-seconds", 1, 3600, "Expansion time (s)",
                    List.of("10", "15", "30")),
            "stages", new Setting("stages", 1, 50, "Shrink stages",
                    List.of("3", "5", "8")),
            "stagetime", new Setting("stage-hold-seconds", 5, 7200, "Time between stages (s)",
                    List.of("60", "120", "180", "300")),
            "shrinktime", new Setting("stage-shrink-seconds", 1, 3600, "Stage shrink time (s)",
                    List.of("30", "60", "120")));

    private static final List<String> SUBCOMMANDS = List.of(
            "addspawn", "delspawn", "spawns", "clearspawns", "scatter",
            "setcenter", "start", "stop", "status",
            "countdown", "startsize", "expandsize", "finalsize", "expandtime",
            "stages", "stagetime", "shrinktime");

    private final HungerGamesManager manager;

    public HungerGamesCommand(HungerGamesManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase();

        Setting setting = SETTINGS.get(sub);
        if (setting != null) {
            if (args.length < 2) {
                return false;
            }
            int value;
            try {
                value = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Messages.send(sender, "general.not-a-number", "input", args[1]);
                return true;
            }
            if (value < setting.min() || value > setting.max()) {
                Messages.send(sender, "general.value-range",
                        "min", String.valueOf(setting.min()), "max", String.valueOf(setting.max()));
                return true;
            }
            manager.setSetting(setting.key(), value);
            Messages.send(sender, "hungergames.value-set",
                    "setting", setting.label(), "value", String.valueOf(value));
            return true;
        }

        switch (sub) {
            case "addspawn" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, "general.only-players");
                    return true;
                }
                Location location = player.getLocation();
                int index = manager.addSpawn(location);
                Messages.send(sender, "hungergames.spawn-added",
                        "index", String.valueOf(index),
                        "x", String.valueOf(location.getBlockX()),
                        "y", String.valueOf(location.getBlockY()),
                        "z", String.valueOf(location.getBlockZ()),
                        "world", location.getWorld().getName());
            }
            case "delspawn" -> {
                if (args.length < 2) {
                    return false;
                }
                int index;
                try {
                    index = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    Messages.send(sender, "general.not-a-number", "input", args[1]);
                    return true;
                }
                if (manager.removeSpawn(index)) {
                    Messages.send(sender, "hungergames.spawn-removed", "index", String.valueOf(index));
                } else {
                    Messages.send(sender, "hungergames.spawn-invalid-index",
                            "input", args[1], "count", String.valueOf(manager.spawnCount()));
                }
            }
            case "spawns", "listspawns" -> {
                List<String> raw = manager.rawSpawns();
                if (raw.isEmpty()) {
                    Messages.send(sender, "hungergames.no-spawns");
                    return true;
                }
                Messages.send(sender, "hungergames.spawns-header", "count", String.valueOf(raw.size()));
                for (int i = 0; i < raw.size(); i++) {
                    String[] parts = raw.get(i).split(";");
                    Messages.send(sender, "hungergames.spawns-entry",
                            "index", String.valueOf(i + 1),
                            "world", parts.length > 0 ? parts[0] : "?",
                            "x", blockCoord(parts, 1),
                            "y", blockCoord(parts, 2),
                            "z", blockCoord(parts, 3));
                }
            }
            case "clearspawns" -> {
                int count = manager.clearSpawns();
                Messages.send(sender, "hungergames.spawns-cleared", "count", String.valueOf(count));
            }
            case "scatter" -> scatter(sender);
            case "setcenter" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, "general.only-players");
                    return true;
                }
                Location location = player.getLocation();
                manager.setBorderCenter(location);
                Messages.send(sender, "hungergames.center-set",
                        "x", String.valueOf(location.getBlockX()),
                        "z", String.valueOf(location.getBlockZ()),
                        "world", location.getWorld().getName());
            }
            case "start" -> {
                if (manager.isRunning()) {
                    Messages.send(sender, "hungergames.already-running");
                    return true;
                }
                if (manager.borderCenter() == null) {
                    Messages.send(sender, "hungergames.no-center");
                    return true;
                }
                int countdown = manager.countdownSeconds();
                if (args.length >= 2) {
                    try {
                        countdown = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        Messages.send(sender, "general.not-a-number", "input", args[1]);
                        return true;
                    }
                    if (countdown < 3 || countdown > 3600) {
                        Messages.send(sender, "general.value-range", "min", "3", "max", "3600");
                        return true;
                    }
                }
                manager.start(countdown);
                Messages.send(sender, "hungergames.started", "seconds", String.valueOf(countdown));
            }
            case "stop" -> {
                if (!manager.isRunning()) {
                    Messages.send(sender, "hungergames.not-running");
                    return true;
                }
                manager.stop();
                Messages.send(sender, "hungergames.stopped");
            }
            case "status" -> {
                Location center = manager.borderCenter();
                Messages.send(sender, "hungergames.status-state", "state", manager.stateDescription());
                Messages.send(sender, "hungergames.status-border",
                        "center", center == null ? "not set"
                                : center.getBlockX() + ", " + center.getBlockZ()
                                        + " (" + center.getWorld().getName() + ")",
                        "start", String.valueOf(manager.startSize()),
                        "expanded", String.valueOf(manager.expandedSize()),
                        "expandseconds", String.valueOf(manager.expandSeconds()),
                        "final", String.valueOf(manager.finalSize()));
                Messages.send(sender, "hungergames.status-stages",
                        "stages", String.valueOf(manager.stages()),
                        "hold", String.valueOf(manager.stageHoldSeconds()),
                        "shrink", String.valueOf(manager.stageShrinkSeconds()),
                        "countdown", String.valueOf(manager.countdownSeconds()));
                Messages.send(sender, "hungergames.status-spawns",
                        "count", String.valueOf(manager.spawnCount()));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void scatter(CommandSender sender) {
        List<Location> spawns = manager.spawns();
        if (spawns.isEmpty()) {
            Messages.send(sender, "hungergames.no-spawns");
            return;
        }
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.size() > spawns.size()) {
            Messages.send(sender, "hungergames.too-many-players",
                    "players", String.valueOf(players.size()),
                    "spawns", String.valueOf(spawns.size()));
            return;
        }
        for (int i = 0; i < spawns.size(); i++) {
            if (spawns.get(i) == null) {
                Messages.send(sender, "hungergames.spawn-world-missing", "index", String.valueOf(i + 1));
                return;
            }
        }
        Collections.shuffle(spawns);
        for (int i = 0; i < players.size(); i++) {
            players.get(i).teleport(spawns.get(i));
        }
        Messages.send(sender, "hungergames.scattered",
                "players", String.valueOf(players.size()),
                "spawns", String.valueOf(spawns.size()));
    }

    private static String blockCoord(String[] parts, int index) {
        if (index >= parts.length) {
            return "?";
        }
        try {
            return String.valueOf((int) Math.floor(Double.parseDouble(parts[index])));
        } catch (NumberFormatException e) {
            return parts[index];
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            Setting setting = SETTINGS.get(args[0].toLowerCase());
            if (setting != null) {
                return setting.suggestions().stream()
                        .filter(s -> s.startsWith(args[1]))
                        .toList();
            }
            if (args[0].equalsIgnoreCase("delspawn")) {
                return IntStream.rangeClosed(1, manager.spawnCount())
                        .mapToObj(String::valueOf)
                        .filter(s -> s.startsWith(args[1]))
                        .toList();
            }
            if (args[0].equalsIgnoreCase("start")) {
                return List.of("30", "60", "120").stream()
                        .filter(s -> s.startsWith(args[1]))
                        .toList();
            }
        }
        return List.of();
    }
}
