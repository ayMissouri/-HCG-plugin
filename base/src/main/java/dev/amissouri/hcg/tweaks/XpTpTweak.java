package dev.amissouri.hcg.tweaks;

import java.util.List;

import dev.amissouri.hcg.Messages;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class XpTpTweak implements Tweak {

    private static final String PATH = "tweaks.xptp.";

    private final JavaPlugin plugin;

    private volatile boolean enabled;
    private volatile boolean mending;
    private volatile boolean sound;
    private volatile boolean blocks;
    private volatile boolean includePlayers;

    public XpTpTweak(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean(PATH + "enabled", false);
        mending = config.getBoolean(PATH + "mending", true);
        sound = config.getBoolean(PATH + "sound", true);
        blocks = config.getBoolean(PATH + "blocks", true);
        includePlayers = config.getBoolean(PATH + "include-players", false);
    }

    @Override
    public String id() {
        return "xptp";
    }

    @Override
    public String displayName() {
        return Messages.raw("tweaks.xptp.name");
    }

    @Override
    public Material icon() {
        return Material.EXPERIENCE_BOTTLE;
    }

    @Override
    public List<String> summary() {
        return List.of(Messages.raw("tweaks.xptp.summary"));
    }

    @Override
    public String command() {
        return "/xptp";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        enabled = value;
        write("enabled", value);
    }

    @Override
    public List<Setting> settings() {
        return List.of(
                new Setting.Of("Mending", Material.ANVIL,
                        () -> onOff(mending),
                        forward -> setMending(!mending),
                        List.of("Repair the killer's Mending gear",
                                "first, exactly like picked-up orbs.")),
                new Setting.Of("Sound", Material.NOTE_BLOCK,
                        () -> onOff(sound),
                        forward -> setSound(!sound),
                        List.of("Play the orb pickup sound when",
                                "the XP arrives.")),
                new Setting.Of("Block XP", Material.DIAMOND_ORE,
                        () -> onOff(blocks),
                        forward -> setBlocks(!blocks),
                        List.of("Also send XP from mined blocks",
                                "(ores, spawners) straight to you.")),
                new Setting.Of("Include Players", Material.PLAYER_HEAD,
                        () -> onOff(includePlayers),
                        forward -> setIncludePlayers(!includePlayers),
                        List.of("Also send a slain player's dropped",
                                "XP straight to their killer.")));
    }

    public boolean mending() {
        return mending;
    }

    public void setMending(boolean value) {
        mending = value;
        write("mending", value);
    }

    public boolean sound() {
        return sound;
    }

    public void setSound(boolean value) {
        sound = value;
        write("sound", value);
    }

    public boolean blocks() {
        return blocks;
    }

    public void setBlocks(boolean value) {
        blocks = value;
        write("blocks", value);
    }

    public boolean includePlayers() {
        return includePlayers;
    }

    public void setIncludePlayers(boolean value) {
        includePlayers = value;
        write("include-players", value);
    }

    private void write(String key, Object value) {
        plugin.getConfig().set(PATH + key, value);
        plugin.saveConfig();
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
