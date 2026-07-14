package dev.amissouri.hcg.healthdecay;

import java.util.List;

import dev.amissouri.hcg.HelpRegistry;
import dev.amissouri.hcg.HelpRegistry.Entry;
import dev.amissouri.hcg.Messages;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** Health Decay addon: max health ticks down until a confirmed PvP kill restores everyone. */
public final class HealthDecayPlugin extends JavaPlugin {

    private static final String CATEGORY = "Health Decay";

    private DecayManager decayManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.registerDefaults(this);

        decayManager = new DecayManager(this);
        getServer().getPluginManager().registerEvents(new KillListener(this, decayManager), this);
        register("healthdecay", new DecayCommand(this, decayManager));

        HelpRegistry.register(CATEGORY, 10, List.of(
                new Entry("/healthdecay", "Open the health decay settings menu."),
                new Entry("/healthdecay on|off", "Start or stop the health decay game mode."),
                new Entry("/healthdecay status", "Show current max health, floor, and decay rate."),
                new Entry("/healthdecay restore", "Restore everyone's health now."),
                new Entry("/healthdecay interval <s>", "Set seconds between decay ticks."),
                new Entry("/healthdecay amount <hearts>", "Set hearts lost per decay tick.")));

        if (getConfig().getBoolean("enabled", true)) {
            decayManager.start();
        }
    }

    @Override
    public void onDisable() {
        HelpRegistry.unregister(CATEGORY);
        if (decayManager != null) {
            decayManager.shutdown();
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
