package dev.amissouri.hcg.randomdrops;

import java.util.List;

import dev.amissouri.hcg.HelpRegistry;
import dev.amissouri.hcg.HelpRegistry.Entry;
import dev.amissouri.hcg.Messages;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** Random Drops addon: every block broken drops a random survival-obtainable item. */
public final class RandomDropsPlugin extends JavaPlugin {

    private static final String CATEGORY = "Random Drops";

    private RandomDropsManager randomDropsManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.registerDefaults(this);

        randomDropsManager = new RandomDropsManager(this);
        getServer().getPluginManager().registerEvents(new RandomDropsListener(randomDropsManager), this);
        register("randomdrops", new RandomDropsCommand(this, randomDropsManager));

        HelpRegistry.register(CATEGORY, 20, List.of(
                new Entry("/randomdrops", "Open the random drops settings menu."),
                new Entry("/randomdrops on|off", "Enable or disable random block drops."),
                new Entry("/randomdrops status", "Show whether random drops are on."),
                new Entry("/randomdrops mode <dynamic|static>",
                        "Fresh roll per break, or one fixed drop per block type."),
                new Entry("/randomdrops enchants <on|off>", "Give every drop 1-3 random enchantments."),
                new Entry("/randomdrops mobs <on|off>", "Mobs also drop random items instead of loot."),
                new Entry("/randomdrops reroll", "Randomize the static drop table again.")));
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
