package dev.amissouri.hcg.npcs;

import java.util.List;

import dev.amissouri.hcg.HcgScheduler;
import dev.amissouri.hcg.HelpRegistry;
import dev.amissouri.hcg.HelpRegistry.Entry;
import dev.amissouri.hcg.Messages;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** NPCs addon: packet-based player NPCs with skins, holograms, and click actions. */
public final class NpcsPlugin extends JavaPlugin {

    private static final String CATEGORY = "NPCs";

    private NpcManager npcManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.registerDefaults(this);

        HcgScheduler scheduler = new HcgScheduler(this);
        npcManager = new NpcManager(this, scheduler);
        if (npcManager.isAvailable()) {
            getServer().getPluginManager().registerEvents(new NpcListener(npcManager), this);
        }
        register("npc", new NpcCommand(this, scheduler, npcManager));

        npcManager.load();

        HelpRegistry.register(CATEGORY, 50, List.of(
                new Entry("/npc create <name>", "Create a player NPC at your location."),
                new Entry("/npc remove <name>", "Delete an NPC."),
                new Entry("/npc list", "List all NPCs."),
                new Entry("/npc info <name>", "Show an NPC's settings."),
                new Entry("/npc skin <name> <player|url|reset>", "Set the skin from a player name or image URL."),
                new Entry("/npc displayname <name> <text|none>", "Hologram name above the NPC; & colors, | for lines."),
                new Entry("/npc equipment <name> set <slot>", "Equip your held item (empty hand clears)."),
                new Entry("/npc equipment <name> <clear|list> [slot]", "Clear or list equipment."),
                new Entry("/npc glowing <name> <color|off>", "Make the NPC glow in a team color."),
                new Entry("/npc collidable <name> <on|off>", "Whether players bump into the NPC."),
                new Entry("/npc showintab <name> <on|off>", "Show the NPC in the tab list."),
                new Entry("/npc movehere <name>", "Move the NPC to your location."),
                new Entry("/npc rotate <name> <yaw> <pitch>", "Set the NPC's facing."),
                new Entry("/npc teleport <name>", "Teleport yourself to the NPC."),
                new Entry("/npc turntoplayer <name> <on|off> [dist]", "NPC looks at nearby players."),
                new Entry("/npc cooldown <name> <seconds>", "Delay between clicks per player."),
                new Entry("/npc action <name> <trigger> add <type> <...>",
                        "Add a click action (message, commands, sound, wait)."),
                new Entry("/npc action <name> <trigger> <list|remove|clear>", "Manage a trigger's actions.")));
    }

    @Override
    public void onDisable() {
        HelpRegistry.unregister(CATEGORY);
        if (npcManager != null) {
            npcManager.shutdown();
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
