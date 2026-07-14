package dev.amissouri.hcg.healthshare;
import dev.amissouri.hcg.Messages;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Splits players into teams that share a single health pool. */
public final class HealthShareManager {

    private static final String TEAM_PREFIX = "hcg_hs_";
    private static final List<NamedTextColor> COLORS = List.of(
            NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN,
            NamedTextColor.YELLOW, NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.GOLD, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_PURPLE, NamedTextColor.GRAY, NamedTextColor.WHITE);

    public record ShareTeam(int id, List<UUID> members) {
        String displayName() {
            return "Team " + id;
        }
    }

    private final JavaPlugin plugin;
    private final List<ShareTeam> teams = new ArrayList<>();
    private final Map<UUID, ShareTeam> teamByPlayer = new HashMap<>();
    private int teamSize;
    private boolean enabled;
    private boolean wiping;

    public HealthShareManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int teamSize() {
        return teamSize;
    }

    public List<ShareTeam> teams() {
        return List.copyOf(teams);
    }

    public int start(int size) {
        stop();
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                players.add(player);
            }
        }
        Collections.shuffle(players);

        teamSize = size;
        for (int from = 0; from < players.size(); from += size) {
            List<Player> group = players.subList(from, Math.min(from + size, players.size()));
            ShareTeam team = new ShareTeam(teams.size() + 1, new ArrayList<>());
            teams.add(team);
            Team board = registerBoardTeam(team);
            for (Player member : group) {
                team.members().add(member.getUniqueId());
                teamByPlayer.put(member.getUniqueId(), team);
                board.addEntry(member.getName());
            }
            syncToLowest(team);
            for (Player member : group) {
                sendTeamInfo(member, team);
            }
        }
        enabled = true;
        return teams.size();
    }

    public void stop() {
        enabled = false;
        teamSize = 0;
        teams.clear();
        teamByPlayer.clear();
        clearBoardTeams();
    }

    public void shutdown() {
        stop();
    }

    void scheduleSync(Player player) {
        if (!enabled || wiping || !teamByPlayer.containsKey(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!enabled || wiping || !player.isOnline() || player.isDead()) {
                return;
            }
            ShareTeam team = teamByPlayer.get(player.getUniqueId());
            double health = player.getHealth();
            if (team == null || health <= 0.0) {
                return;
            }
            for (Player member : livingMembers(team)) {
                if (!member.equals(player)) {
                    applyHealth(member, health);
                }
            }
        });
    }

    ShareTeam onDeath(Player victim) {
        if (!enabled || wiping) {
            return null;
        }
        ShareTeam team = teamByPlayer.get(victim.getUniqueId());
        if (team == null) {
            return null;
        }
        List<Player> others = livingMembers(team);
        others.remove(victim);
        if (others.isEmpty()) {
            return null;
        }
        wiping = true;
        try {
            for (Player member : others) {
                member.setHealth(0.0);
            }
        } finally {
            wiping = false;
        }
        Messages.broadcast("healthshare.team-wiped", "victim", victim.getName(), "team", team.displayName());
        return team;
    }

    void handleJoin(Player player) {
        if (!enabled) {
            return;
        }
        ShareTeam existing = teamByPlayer.get(player.getUniqueId());
        if (existing == null) {
            if (player.getGameMode() == GameMode.SPECTATOR || teams.isEmpty()) {
                return;
            }
            existing = smallestTeam();
            existing.members().add(player.getUniqueId());
            teamByPlayer.put(player.getUniqueId(), existing);
            Team board = registerBoardTeam(existing);
            board.addEntry(player.getName());
            for (Player member : livingMembers(existing)) {
                if (!member.equals(player)) {
                    Messages.send(member, "healthshare.teammate-joined", "player", player.getName());
                }
            }
        }
        ShareTeam team = existing;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!enabled || !player.isOnline() || player.isDead()) {
                return;
            }
            syncToLowest(team);
            sendTeamInfo(player, team);
        });
    }

    double teamHealth(ShareTeam team) {
        double lowest = -1.0;
        for (Player member : livingMembers(team)) {
            if (lowest < 0.0 || member.getHealth() < lowest) {
                lowest = member.getHealth();
            }
        }
        return lowest;
    }

    String memberNames(ShareTeam team, Player exclude) {
        List<String> names = new ArrayList<>();
        for (UUID id : team.members()) {
            if (exclude != null && id.equals(exclude.getUniqueId())) {
                continue;
            }
            String name = Bukkit.getOfflinePlayer(id).getName();
            if (name != null) {
                names.add(name);
            }
        }
        return String.join(", ", names);
    }

    private void sendTeamInfo(Player player, ShareTeam team) {
        if (team.members().size() <= 1) {
            Messages.send(player, "healthshare.your-team-alone", "team", team.displayName());
        } else {
            Messages.send(player, "healthshare.your-team",
                    "team", team.displayName(), "members", memberNames(team, player));
        }
    }

    private void syncToLowest(ShareTeam team) {
        double health = teamHealth(team);
        if (health <= 0.0) {
            return;
        }
        for (Player member : livingMembers(team)) {
            applyHealth(member, health);
        }
    }

    private ShareTeam smallestTeam() {
        ShareTeam smallest = teams.get(0);
        for (ShareTeam team : teams) {
            if (team.members().size() < smallest.members().size()) {
                smallest = team;
            }
        }
        return smallest;
    }

    private List<Player> livingMembers(ShareTeam team) {
        List<Player> living = new ArrayList<>();
        for (UUID id : team.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && !member.isDead()) {
                living.add(member);
            }
        }
        return living;
    }

    private void applyHealth(Player player, double health) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = attribute != null ? attribute.getValue() : 20.0;
        player.setHealth(Math.clamp(health, 0.0, max));
    }

    private Team registerBoardTeam(ShareTeam team) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = TEAM_PREFIX + team.id();
        Team board = scoreboard.getTeam(name);
        if (board == null) {
            board = scoreboard.registerNewTeam(name);
        }
        board.color(COLORS.get((team.id() - 1) % COLORS.size()));
        return board;
    }

    private void clearBoardTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team board : List.copyOf(scoreboard.getTeams())) {
            if (board.getName().startsWith(TEAM_PREFIX)) {
                board.unregister();
            }
        }
    }
}
