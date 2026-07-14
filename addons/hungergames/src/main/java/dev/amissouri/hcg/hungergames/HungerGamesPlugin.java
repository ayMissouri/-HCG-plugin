package dev.amissouri.hcg.hungergames;

import java.util.List;

import dev.amissouri.hcg.HelpRegistry;
import dev.amissouri.hcg.HelpRegistry.Entry;
import dev.amissouri.hcg.Messages;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** Hunger Games addon: match control, spawn scattering, and a staged shrinking world border. */
public final class HungerGamesPlugin extends JavaPlugin {

    private static final String CATEGORY = "Hunger Games";

    private HungerGamesManager hungerGamesManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.registerDefaults(this);

        hungerGamesManager = new HungerGamesManager(this);
        register("hungergames", new HungerGamesCommand(hungerGamesManager));

        HelpRegistry.register(CATEGORY, 40, List.of(
                new Entry("/hungergames addspawn", "Add a spawn point at your location."),
                new Entry("/hungergames spawns", "List all spawn points."),
                new Entry("/hungergames delspawn <#>", "Remove a spawn point by number."),
                new Entry("/hungergames clearspawns", "Remove all spawn points."),
                new Entry("/hungergames scatter", "Teleport all online players to random distinct spawns."),
                new Entry("/hungergames setcenter", "Set the world border center to where you stand."),
                new Entry("/hungergames start [seconds]", "Title countdown, border expands, then shrinks in stages."),
                new Entry("/hungergames stop", "Cancel the sequence and restore the previous border."),
                new Entry("/hungergames status", "Show phase, border sizes, and stage settings."),
                new Entry("/hungergames startsize <blocks>", "Border size during the countdown."),
                new Entry("/hungergames expandsize <blocks>", "Border size after the expansion."),
                new Entry("/hungergames finalsize <blocks>", "Border size after the last stage."),
                new Entry("/hungergames countdown <s>", "Length of the starting countdown."),
                new Entry("/hungergames expandtime <s>", "How long the expansion takes."),
                new Entry("/hungergames stages <n>", "Number of shrink stages."),
                new Entry("/hungergames stagetime <s>", "Hold time between stages."),
                new Entry("/hungergames shrinktime <s>", "How long each stage's shrink takes.")));
    }

    @Override
    public void onDisable() {
        HelpRegistry.unregister(CATEGORY);
        if (hungerGamesManager != null) {
            hungerGamesManager.shutdown();
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
