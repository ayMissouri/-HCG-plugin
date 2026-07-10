package dev.amissouri.hcg;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static YamlConfiguration config = new YamlConfiguration();

    private Messages() {
    }

    static void init(HCGPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        InputStream bundled = plugin.getResource("messages.yml");
        if (bundled != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
    }

    public static String raw(String key, String... pairs) {
        String text = config.getString(key, "&cMissing message: " + key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return text;
    }

    public static Component msg(String key, String... pairs) {
        return LEGACY.deserialize(raw(key, pairs));
    }

    public static void send(CommandSender sender, String key, String... pairs) {
        sender.sendMessage(msg(key, pairs));
    }

    public static void broadcast(String key, String... pairs) {
        Bukkit.broadcast(msg(key, pairs));
    }

    public static void broadcastOps(String key, String... pairs) {
        Component message = msg(key, pairs);
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                player.sendMessage(message);
            }
        }
    }
}
