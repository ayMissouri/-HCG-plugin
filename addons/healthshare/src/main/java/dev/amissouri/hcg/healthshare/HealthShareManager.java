package dev.amissouri.hcg.healthshare;
import dev.amissouri.hcg.HcgPlatform;
import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.Messages;
import dev.amissouri.hcg.Players;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
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

    public static final class ShareTeam {

        private final int id;
        private final List<UUID> members = new CopyOnWriteArrayList<>();
        private final AtomicBoolean wiping = new AtomicBoolean();
        private volatile double pooledHealth = -1.0;

        ShareTeam(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public List<UUID> members() {
            return members;
        }

        public String displayName() {
            return "Team " + id;
        }

        boolean claimWipe() {
            return wiping.compareAndSet(false, true);
        }

        void releaseWipe() {
            wiping.set(false);
        }

        boolean isWiping() {
            return wiping.get();
        }
    }

    private final JavaPlugin plugin;
    private final HcgScheduler scheduler;
    private final List<ShareTeam> teams = new CopyOnWriteArrayList<>();
    private final Map<UUID, ShareTeam> teamByPlayer = new ConcurrentHashMap<>();
    private volatile int teamSize;
    private volatile boolean enabled;

    public HealthShareManager(JavaPlugin plugin, HcgScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
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

    public void start(int size, int minPlayers, IntConsumer onDone) {
        List<? extends Player> candidates = List.copyOf(Bukkit.getOnlinePlayers());
        List<Player> eligible = Collections.synchronizedList(new ArrayList<>());
        Players.forEach(scheduler, candidates, player -> {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                eligible.add(player);
            }
        }, () -> {
            if (eligible.size() < minPlayers) {
                onDone.accept(0);
                return;
            }
            clearState();
            clearBoardTeams();
            List<Player> players = new ArrayList<>(eligible);
            Collections.shuffle(players);
            teamSize = size;
            for (int from = 0; from < players.size(); from += size) {
                List<Player> group = players.subList(from, Math.min(from + size, players.size()));
                ShareTeam team = new ShareTeam(teams.size() + 1);
                teams.add(team);
                for (Player member : group) {
                    team.members().add(member.getUniqueId());
                    teamByPlayer.put(member.getUniqueId(), team);
                    addToBoardTeam(team, member.getName());
                }
                syncToLowest(team);
                for (Player member : group) {
                    sendTeamInfo(member, team);
                }
            }
            enabled = true;
            onDone.accept(teams.size());
        });
    }

    public void stop(Runnable onStopped) {
        scheduler.global(() -> {
            if (!enabled) {
                return;
            }
            clearState();
            clearBoardTeams();
            onStopped.run();
        });
    }

    public void shutdown() {
        clearState();
        try {
            clearBoardTeams();
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not clear health-share scoreboard teams on shutdown: " + t);
        }
    }

    private void clearState() {
        enabled = false;
        teamSize = 0;
        teams.clear();
        teamByPlayer.clear();
    }

    void scheduleSync(Player player) {
        if (!enabled) {
            return;
        }
        ShareTeam team = teamByPlayer.get(player.getUniqueId());
        if (team == null || team.isWiping()) {
            return;
        }
        scheduler.entity(player, () -> {
            if (!enabled || team.isWiping() || !player.isOnline() || player.isDead()) {
                return;
            }
            double health = player.getHealth();
            if (health <= 0.0) {
                return;
            }
            team.pooledHealth = health;
            for (UUID id : team.members()) {
                if (id.equals(player.getUniqueId())) {
                    continue;
                }
                Player member = Bukkit.getPlayer(id);
                if (member == null) {
                    continue;
                }
                scheduler.entity(member, () -> {
                    if (enabled && !team.isWiping() && !member.isDead()) {
                        applyHealth(member, health);
                    }
                });
            }
        });
    }

    void onDeath(Player victim) {
        if (!enabled) {
            return;
        }
        ShareTeam team = teamByPlayer.get(victim.getUniqueId());
        if (team == null || !team.claimWipe()) {
            return;
        }
        List<Player> others = onlineMembers(team);
        others.removeIf(member -> member.getUniqueId().equals(victim.getUniqueId()));
        if (others.isEmpty()) {
            team.releaseWipe();
            return;
        }

        String victimName = victim.getName();
        AtomicInteger killed = new AtomicInteger();
        AtomicInteger pending = new AtomicInteger(others.size());
        Runnable settle = () -> {
            if (pending.decrementAndGet() == 0) {
                team.pooledHealth = -1.0;
                team.releaseWipe();
                if (killed.get() > 0) {
                    Messages.broadcast("healthshare.team-wiped",
                            "victim", victimName, "team", team.displayName());
                }
            }
        };
        for (Player member : others) {
            ScheduledTask task = scheduler.entityOrDrop(member, () -> {
                try {
                    if (!member.isDead()) {
                        member.setHealth(0.0);
                        killed.incrementAndGet();
                    }
                } finally {
                    settle.run();
                }
            }, settle);
            if (task == null) {
                settle.run();
            }
        }
    }

    void handleJoin(Player player) {
        if (!enabled) {
            return;
        }
        ShareTeam existing = teamByPlayer.get(player.getUniqueId());
        if (existing == null) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            existing = smallestTeam();
            if (existing == null) {
                return;
            }
            existing.members().add(player.getUniqueId());
            teamByPlayer.put(player.getUniqueId(), existing);
            ShareTeam joined = existing;
            scheduler.global(() -> addToBoardTeam(joined, player.getName()));
            for (Player member : onlineMembers(existing)) {
                if (!member.equals(player)) {
                    Messages.send(member, "healthshare.teammate-joined", "player", player.getName());
                }
            }
        }
        ShareTeam team = existing;
        scheduler.entity(player, () -> {
            if (!enabled || !player.isOnline() || player.isDead()) {
                return;
            }
            sendTeamInfo(player, team);
            syncToLowest(team);
        });
    }

    double teamHealth(ShareTeam team) {
        return team.pooledHealth;
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
        List<Player> members = onlineMembers(team);
        if (members.isEmpty()) {
            return;
        }
        Map<UUID, Double> healths = new ConcurrentHashMap<>();
        Players.forEach(scheduler, members, member -> {
            if (!member.isDead()) {
                healths.put(member.getUniqueId(), member.getHealth());
            }
        }, () -> {
            double lowest = healths.values().stream().mapToDouble(Double::doubleValue).min().orElse(-1.0);
            team.pooledHealth = lowest;
            if (lowest > 0.0) {
                Players.forEach(scheduler, onlineMembers(team), member -> applyHealth(member, lowest));
            }
        });
    }

    private ShareTeam smallestTeam() {
        ShareTeam smallest = null;
        for (ShareTeam team : teams) {
            if (smallest == null || team.members().size() < smallest.members().size()) {
                smallest = team;
            }
        }
        return smallest;
    }

    private List<Player> onlineMembers(ShareTeam team) {
        List<Player> online = new ArrayList<>();
        for (UUID id : team.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null) {
                online.add(member);
            }
        }
        return online;
    }

    private void applyHealth(Player player, double health) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = attribute != null ? attribute.getValue() : 20.0;
        player.setHealth(Math.clamp(health, 0.0, max));
    }

    private void addToBoardTeam(ShareTeam team, String playerName) {
        if (HcgPlatform.isFolia()) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = TEAM_PREFIX + team.id();
        Team board = scoreboard.getTeam(name);
        if (board == null) {
            board = scoreboard.registerNewTeam(name);
        }
        board.color(COLORS.get((team.id() - 1) % COLORS.size()));
        board.addEntry(playerName);
    }

    private void clearBoardTeams() {
        if (HcgPlatform.isFolia()) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team board : List.copyOf(scoreboard.getTeams())) {
            if (board.getName().startsWith(TEAM_PREFIX)) {
                board.unregister();
            }
        }
    }
}
