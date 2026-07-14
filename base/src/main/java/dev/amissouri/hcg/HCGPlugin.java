package dev.amissouri.hcg;

import java.util.List;

import dev.amissouri.hcg.HelpRegistry.Entry;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The HCGplugin base. It provides the shared infrastructure every addon relies on
 * ({@link Messages}, {@link HelpRegistry}, {@link HcgText}, the {@code /hcg} help menu) plus the
 * always-on Admin, Item, and World commands. Feature categories (Health Decay, Lava Raise, ...)
 * ship as separate addon plugins that hard-depend on this one.
 */
public final class HCGPlugin extends JavaPlugin {

    private FreezeManager freezeManager;
    private VanishManager vanishManager;

    @Override
    public void onEnable() {
        Messages.init(this);

        freezeManager = new FreezeManager();
        vanishManager = new VanishManager(this);

        getServer().getPluginManager().registerEvents(new FreezeListener(freezeManager), this);
        getServer().getPluginManager().registerEvents(new VanishListener(vanishManager), this);

        register("hcg", new HcgCommand());
        register("flyspeed", new FlySpeedCommand());
        register("fly", new FlyCommand());
        GamemodeCommand gamemode = new GamemodeCommand();
        register("gmc", gamemode);
        register("gms", gamemode);
        register("gmsp", gamemode);
        register("gma", gamemode);
        register("tpall", new TpAllCommand());
        register("freeze", new FreezeCommand(freezeManager));
        register("invsee", new InvseeCommand());
        HealFeedCommand healFeed = new HealFeedCommand();
        register("heal", healFeed);
        register("feed", healFeed);
        OpenMenuCommand openMenu = new OpenMenuCommand();
        register("anvil", openMenu);
        register("craft", openMenu);
        register("enderchest", openMenu);
        register("burn", new BurnCommand());
        register("enchant", new EnchantCommand());
        register("god", new GodCommand());
        register("hat", new HatCommand());
        ItemTextCommand itemText = new ItemTextCommand();
        register("lore", itemText);
        register("name", itemText);
        register("nickname", new NicknameCommand());
        register("lightning", new LightningCommand());
        register("remove", new RemoveCommand());
        register("spawner", new SpawnerCommand());
        register("spawnmob", new SpawnMobCommand());
        register("sudo", new SudoCommand());
        register("vanish", new VanishCommand(vanishManager));

        registerHelp();
    }

    private void registerHelp() {
        HelpRegistry.register("Admin Commands", HelpRegistry.ORDER_ADMIN, List.of(
                new Entry("/hcg help [category]", "Show this help menu."),
                new Entry("/flyspeed <1-10>", "Set your fly speed (vanilla default is 1)."),
                new Entry("/fly", "Toggle flight in any gamemode."),
                new Entry("/gmc, /gms, /gmsp, /gma [player]",
                        "Gamemode shortcuts: creative, survival, spectator, adventure."),
                new Entry("/tpall here", "Teleport all other players to you."),
                new Entry("/tpall looking", "Teleport all other players to where you're looking."),
                new Entry("/tpall <player>", "Teleport all other players to that player."),
                new Entry("/freeze <player>", "Toggle freeze on a player (can look, can't move)."),
                new Entry("/freeze all", "Freeze everyone except you; run again to unfreeze."),
                new Entry("/invsee <player>", "Open and edit another player's inventory."),
                new Entry("/heal [player|all]", "Heal yourself, a player, or everyone."),
                new Entry("/feed [player|all]", "Restore hunger for yourself, a player, or everyone."),
                new Entry("/god [player]", "Toggle invulnerability."),
                new Entry("/vanish [player]", "Toggle invisibility to other players."),
                new Entry("/burn <player> [seconds]", "Set a player on fire."),
                new Entry("/nickname [player] <text|reset>", "Set a display name; supports & colors."),
                new Entry("/sudo <player> <msg or /cmd>", "Make a player chat or run a command.")));

        HelpRegistry.register("Item Commands", HelpRegistry.ORDER_ITEM, List.of(
                new Entry("/anvil", "Open a virtual anvil."),
                new Entry("/craft", "Open a virtual crafting table."),
                new Entry("/enderchest [player]", "Open your (or another player's) ender chest."),
                new Entry("/enchant <enchantment> <level>", "Enchant your held item beyond vanilla limits."),
                new Entry("/hat", "Wear your held item as a hat."),
                new Entry("/name <text|reset>", "Rename your held item; supports & colors."),
                new Entry("/lore <text|reset>", "Set held item lore; & colors, | for new lines.")));

        HelpRegistry.register("World Commands", HelpRegistry.ORDER_WORLD, List.of(
                new Entry("/lightning", "Strike lightning where you're looking."),
                new Entry("/remove <items|entities|mobs> [hostile|neutral]",
                        "Remove loaded ground items, entities, or mobs."),
                new Entry("/spawner <mob>", "Change the spawner you're looking at."),
                new Entry("/spawnmob <mob> [amount]", "Spawn mobs at your location.")));
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }
}
