package dev.amissouri.hcg.tweaks;

import java.util.List;

import dev.amissouri.hcg.Messages;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class RecoveryCompassTweak implements Tweak {

    private static final String PATH = "tweaks.recoverycompass.";

    private final JavaPlugin plugin;

    private volatile boolean enabled;
    private volatile boolean coordinates;
    private volatile boolean glow;

    public RecoveryCompassTweak(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean(PATH + "enabled", false);
        coordinates = config.getBoolean(PATH + "coordinates", true);
        glow = config.getBoolean(PATH + "glow", true);
    }

    @Override
    public String id() {
        return "recoverycompass";
    }

    @Override
    public String displayName() {
        return Messages.raw("tweaks.recoverycompass.name");
    }

    @Override
    public Material icon() {
        return Material.RECOVERY_COMPASS;
    }

    @Override
    public List<String> summary() {
        return List.of(Messages.raw("tweaks.recoverycompass.summary"));
    }

    @Override
    public String command() {
        return "/recoverycompass";
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
                new Setting.Of("Show Coordinates", Material.MAP,
                        () -> onOff(coordinates),
                        forward -> setCoordinates(!coordinates),
                        List.of("Write the death coordinates into",
                                "the compass lore. OFF just names it,",
                                "the needle still points there.")),
                new Setting.Of("Compass Glow", Material.GLOWSTONE_DUST,
                        () -> onOff(glow),
                        forward -> setGlow(!glow),
                        List.of("Give the compass an enchant glint so",
                                "it stands out in a fresh inventory.")));
    }

    public boolean coordinates() {
        return coordinates;
    }

    public void setCoordinates(boolean value) {
        coordinates = value;
        write("coordinates", value);
    }

    public boolean glow() {
        return glow;
    }

    public void setGlow(boolean value) {
        glow = value;
        write("glow", value);
    }

    private void write(String key, Object value) {
        plugin.getConfig().set(PATH + key, value);
        plugin.saveConfig();
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
