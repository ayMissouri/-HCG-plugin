package dev.amissouri.hcg.firstto;

import java.util.List;

import dev.amissouri.hcg.HelpRegistry;
import dev.amissouri.hcg.HelpRegistry.Entry;
import dev.amissouri.hcg.Messages;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** First To addon: a slot machine rolls a target item, first to craft or obtain it wins. */
public final class FirstToPlugin extends JavaPlugin {

    private static final String CATEGORY = "First To";

    private FirstToManager firstToManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.registerDefaults(this);

        firstToManager = new FirstToManager(this);
        getServer().getPluginManager().registerEvents(new FirstToListener(firstToManager), this);
        register("firstto", new FirstToCommand(firstToManager));

        HelpRegistry.register(CATEGORY, 80, List.of(
                new Entry("/firstto", "Open the first-to settings menu."),
                new Entry("/firstto craft", "Roll a random craftable item, first to craft it wins."),
                new Entry("/firstto obtain", "Roll any survival item, first to get one wins."),
                new Entry("/firstto stop", "Cancel the current round."),
                new Entry("/firstto status", "Show the current target and toggles."),
                new Entry("/firstto nether <on|off>", "Allow nether-only items as targets."),
                new Entry("/firstto end <on|off>", "Allow end-only items as targets."),
                new Entry("/firstto tpspawn <on|off>", "Teleport everyone to spawn when someone wins.")));
    }

    @Override
    public void onDisable() {
        HelpRegistry.unregister(CATEGORY);
        if (firstToManager != null) {
            firstToManager.shutdown();
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
