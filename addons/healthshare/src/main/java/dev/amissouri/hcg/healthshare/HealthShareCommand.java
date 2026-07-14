package dev.amissouri.hcg.healthshare;
import dev.amissouri.hcg.HcgText;
import dev.amissouri.hcg.Messages;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** /healthshare <players-per-team>|stop|status|teams, splits everyone into teams sharing one health pool. */
public final class HealthShareCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("stop", "status", "teams");
    private static final int MIN_TEAM_SIZE = 2;

    private final HealthShareManager manager;

    public HealthShareCommand(HealthShareManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showStatus(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "stop", "off" -> stop(sender);
            case "status" -> showStatus(sender);
            case "teams", "list" -> showTeams(sender);
            default -> {
                return start(sender, args[0]);
            }
        }
        return true;
    }

    private boolean start(CommandSender sender, String input) {
        int size;
        try {
            size = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return false;
        }
        if (size < MIN_TEAM_SIZE) {
            Messages.send(sender, "healthshare.size-too-small", "min", String.valueOf(MIN_TEAM_SIZE));
            return true;
        }
        long eligible = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .count();
        if (eligible < MIN_TEAM_SIZE) {
            Messages.send(sender, "healthshare.not-enough-players", "min", String.valueOf(MIN_TEAM_SIZE));
            return true;
        }
        int teams = manager.start(size);
        Messages.broadcast("healthshare.started-broadcast",
                "teams", String.valueOf(teams), "size", String.valueOf(size));
        return true;
    }

    private void stop(CommandSender sender) {
        if (!manager.isEnabled()) {
            Messages.send(sender, "healthshare.not-running");
            return;
        }
        manager.stop();
        Messages.broadcast("healthshare.stopped-broadcast");
    }

    private void showStatus(CommandSender sender) {
        if (manager.isEnabled()) {
            Messages.send(sender, "healthshare.status-running",
                    "teams", String.valueOf(manager.teams().size()),
                    "size", String.valueOf(manager.teamSize()));
        } else {
            Messages.send(sender, "healthshare.status-stopped");
        }
    }

    private void showTeams(CommandSender sender) {
        if (!manager.isEnabled()) {
            Messages.send(sender, "healthshare.not-running");
            return;
        }
        List<HealthShareManager.ShareTeam> teams = manager.teams();
        Messages.send(sender, "healthshare.teams-header", "count", String.valueOf(teams.size()));
        for (HealthShareManager.ShareTeam team : teams) {
            double health = manager.teamHealth(team);
            if (health < 0.0) {
                Messages.send(sender, "healthshare.teams-entry-dead",
                        "team", team.displayName(), "members", manager.memberNames(team, null));
            } else {
                Messages.send(sender, "healthshare.teams-entry",
                        "team", team.displayName(), "members", manager.memberNames(team, null),
                        "health", HcgText.formatHearts(health));
            }
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
