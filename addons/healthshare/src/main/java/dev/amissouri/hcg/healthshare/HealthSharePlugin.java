package dev.amissouri.hcg.healthshare;

import java.util.List;

import dev.amissouri.hcg.HcgPlatform;
import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.HelpRegistry;
import dev.amissouri.hcg.HelpRegistry.Entry;
import dev.amissouri.hcg.Messages;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** Health Share addon: split players into teams that share a single health pool. */
public final class HealthSharePlugin extends JavaPlugin {

    private static final String CATEGORY = "Health Share";

    private HealthShareManager healthShareManager;

    @Override
    public void onEnable() {
        Messages.registerDefaults(this);

        healthShareManager = new HealthShareManager(this, new HcgScheduler(this));
        if (HcgPlatform.isFolia()) {
            getLogger().info("Folia does not support the scoreboard API, so team name colours are off."
                    + " Shared health works normally.");
        }
        getServer().getPluginManager().registerEvents(new HealthShareListener(healthShareManager), this);
        register("healthshare", new HealthShareCommand(healthShareManager));

        HelpRegistry.register(CATEGORY, 70, List.of(
                new Entry("/healthshare <players-per-team>",
                        "Shuffle everyone into teams that share one health pool."),
                new Entry("/healthshare teams", "List the teams, their members, and their shared health."),
                new Entry("/healthshare status", "Show whether health share is running."),
                new Entry("/healthshare stop", "Disband the teams and stop sharing health.")));
    }

    @Override
    public void onDisable() {
        HelpRegistry.unregister(CATEGORY);
        if (healthShareManager != null) {
            healthShareManager.shutdown();
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
