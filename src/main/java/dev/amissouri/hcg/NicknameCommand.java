package dev.amissouri.hcg;

import java.util.Arrays;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /nickname [player] <text|reset>, sets a display name (chat + tab list), with &-color code support.
 */
public final class NicknameCommand implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        Player target;
        String input;
        Player named = args.length >= 2 ? Bukkit.getPlayerExact(args[0]) : null;
        if (named != null) {
            target = named;
            input = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        } else if (sender instanceof Player player) {
            target = player;
            input = String.join(" ", args);
        } else {
            Messages.send(sender, "general.console-needs-player");
            return true;
        }

        if (input.equalsIgnoreCase("reset") || input.equalsIgnoreCase("off")) {
            target.displayName(null);
            target.playerListName(null);
            Messages.send(sender, "commands.nickname.reset", "player", target.getName());
            return true;
        }

        Component nickname = LEGACY.deserialize(input);
        target.displayName(nickname);
        target.playerListName(nickname);

        sender.sendMessage(Messages.msg("commands.nickname.now-known", "player", target.getName())
                .append(nickname));
        if (!target.equals(sender)) {
            target.sendMessage(Messages.msg("commands.nickname.you-are-known").append(nickname));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            return List.of("reset").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
