package dev.amissouri.hcg;

import java.util.List;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /heal [player|all] and /feed [player|all], restore health or hunger. */
public final class HealFeedCommand implements CommandExecutor, TabCompleter {

    private final HcgScheduler scheduler;

    public HealFeedCommand(HcgScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean heal = command.getName().equalsIgnoreCase("heal");

        List<Player> targets;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                Messages.send(sender, "general.console-needs-player");
                return true;
            }
            targets = List.of(player);
        } else if (args[0].equalsIgnoreCase("all")) {
            targets = List.copyOf(Bukkit.getOnlinePlayers());
        } else {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Messages.send(sender, "general.player-not-online", "player", args[0]);
                return true;
            }
            targets = List.of(target);
        }

        Players.forEach(scheduler, targets, target -> {
            if (heal) {
                AttributeInstance maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                target.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
                target.setFireTicks(0);
            } else {
                target.setFoodLevel(20);
                target.setSaturation(20f);
                target.setExhaustion(0f);
            }
        });

        String who = targets.size() == 1 ? targets.get(0).getName() : targets.size() + " players";
        Messages.send(sender, heal ? "commands.heal.done" : "commands.feed.done", "who", who);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Stream.concat(
                            Stream.of("all"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
