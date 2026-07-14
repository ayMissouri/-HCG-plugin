package dev.amissouri.hcg.lavaraise;

import java.util.List;

import dev.amissouri.hcg.HelpRegistry;
import dev.amissouri.hcg.HelpRegistry.Entry;
import dev.amissouri.hcg.Messages;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** Lava Raise addon: the daily rising-lava event plus volcano eruptions. */
public final class LavaRaisePlugin extends JavaPlugin {

    private static final String CATEGORY = "Lava Raise";

    private LavaRaiseManager lavaRaiseManager;
    private VolcanoManager volcanoManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.registerDefaults(this);

        lavaRaiseManager = new LavaRaiseManager(this);
        volcanoManager = new VolcanoManager(this);

        getServer().getPluginManager().registerEvents(lavaRaiseManager.burnTracker(), this);
        getServer().getPluginManager().registerEvents(new VolcanoListener(), this);

        register("lavaraise", new LavaRaiseCommand(lavaRaiseManager));
        register("volcano", new VolcanoCommand(volcanoManager));

        HelpRegistry.register(CATEGORY, 30, List.of(
                new Entry("/lavaraise", "Open the lava raise settings menu."),
                new Entry("/lavaraise on|off", "Arm or disarm the daily rising lava event."),
                new Entry("/lavaraise status", "Show phase, current lava level, and settings."),
                new Entry("/lavaraise start <time>", "World-clock time the lava starts rising."),
                new Entry("/lavaraise end <time>", "World-clock time the lava starts draining."),
                new Entry("/lavaraise duration <s>", "Seconds to travel bedrock <-> max level."),
                new Entry("/lavaraise maxy <y>", "The Y level the lava rises to."),
                new Entry("/lavaraise water <on|off>", "Whether oceans fill with lava too."),
                new Entry("/lavaraise blocks <on|off>", "Whether player-placed burnables burn away."),
                new Entry("/lavaraise mobs <on|off>", "Whether mobs burn in the lava too."),
                new Entry("/lavaraise cancel", "Drain the lava immediately."),
                new Entry("/lavaraise purge <y>", "Remove REAL lava blocks in the region up to Y."),
                new Entry("/volcano setcenter", "Mark the crater at the block you're looking at."),
                new Entry("/volcano erupt [seconds]", "Eruption: debris, particles, screen shake."),
                new Entry("/volcano schedule <time|off>", "Erupt daily at a world-clock time."),
                new Entry("/volcano stop", "Calm the volcano immediately.")));
    }

    @Override
    public void onDisable() {
        HelpRegistry.unregister(CATEGORY);
        if (lavaRaiseManager != null) {
            lavaRaiseManager.shutdown();
        }
        if (volcanoManager != null) {
            volcanoManager.shutdown();
        }
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }
}
