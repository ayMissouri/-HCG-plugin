package dev.amissouri.hcg.graves;

import java.util.List;

import dev.amissouri.hcg.HelpRegistry;
import dev.amissouri.hcg.HelpRegistry.Entry;
import dev.amissouri.hcg.Messages;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** Graves addon: store a player's death drops and XP in a head only the owner can collect. */
public final class GravesPlugin extends JavaPlugin {

    private static final String CATEGORY = "Graves";

    private GravesManager gravesManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.registerDefaults(this);

        gravesManager = new GravesManager(this);
        getServer().getPluginManager().registerEvents(new GravesListener(gravesManager), this);
        register("graves", new GravesCommand(gravesManager));

        gravesManager.load();

        HelpRegistry.register(CATEGORY, 60, List.of(
                new Entry("/graves", "Open the graves settings menu."),
                new Entry("/graves on|off", "Store death drops and XP in a grave only the owner can open."),
                new Entry("/graves status", "Show whether graves are on and how many exist."),
                new Entry("/graves remove", "Force remove the grave you're looking at, spilling its contents.")));
    }

    @Override
    public void onDisable() {
        HelpRegistry.unregister(CATEGORY);
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }
}
