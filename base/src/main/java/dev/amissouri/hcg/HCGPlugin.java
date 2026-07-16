package dev.amissouri.hcg;

import java.util.List;

import dev.amissouri.hcg.HelpRegistry.Entry;
import dev.amissouri.hcg.menu.MenuListener;
import dev.amissouri.hcg.tweaks.MobHealthCommand;
import dev.amissouri.hcg.tweaks.MobHealthListener;
import dev.amissouri.hcg.tweaks.MobHealthTweak;
import dev.amissouri.hcg.tweaks.TreecapitatorCommand;
import dev.amissouri.hcg.tweaks.TreecapitatorListener;
import dev.amissouri.hcg.tweaks.TreecapitatorTweak;
import dev.amissouri.hcg.tweaks.TweakEnchant;
import dev.amissouri.hcg.tweaks.TweaksCommand;
import dev.amissouri.hcg.tweaks.TweaksGui;
import dev.amissouri.hcg.tweaks.TweaksGuiListener;
import dev.amissouri.hcg.tweaks.TweaksManager;
import dev.amissouri.hcg.tweaks.VeinminerCommand;
import dev.amissouri.hcg.tweaks.VeinminerListener;
import dev.amissouri.hcg.tweaks.VeinminerTweak;
import dev.amissouri.hcg.tweaks.XpTpCommand;
import dev.amissouri.hcg.tweaks.XpTpListener;
import dev.amissouri.hcg.tweaks.XpTpTweak;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The HCGplugin base. It provides the shared infrastructure every addon relies on
 */
public final class HCGPlugin extends JavaPlugin {

    private FreezeManager freezeManager;
    private VanishManager vanishManager;
    private MobHealthListener mobHealthListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.init(this);

        HcgScheduler scheduler = new HcgScheduler(this);
        LoadedChunks loadedChunks = new LoadedChunks();
        freezeManager = new FreezeManager();
        vanishManager = new VanishManager(this, scheduler);

        getServer().getPluginManager().registerEvents(loadedChunks, this);
        getServer().getPluginManager().registerEvents(new FreezeListener(freezeManager), this);
        getServer().getPluginManager().registerEvents(new VanishListener(vanishManager), this);
        getServer().getPluginManager().registerEvents(new GodListener(), this);
        getServer().getPluginManager().registerEvents(new MenuListener(scheduler), this);
        loadedChunks.seed(getLogger());

        register("hcg", new HcgCommand());
        register("flyspeed", new FlySpeedCommand());
        register("fly", new FlyCommand());
        GamemodeCommand gamemode = new GamemodeCommand(scheduler);
        register("gmc", gamemode);
        register("gms", gamemode);
        register("gmsp", gamemode);
        register("gma", gamemode);
        register("tpall", new TpAllCommand(scheduler));
        register("freeze", new FreezeCommand(freezeManager));
        register("invsee", new InvseeCommand());
        HealFeedCommand healFeed = new HealFeedCommand(scheduler);
        register("heal", healFeed);
        register("feed", healFeed);
        OpenMenuCommand openMenu = new OpenMenuCommand();
        register("anvil", openMenu);
        register("craft", openMenu);
        register("enderchest", openMenu);
        register("burn", new BurnCommand(scheduler));
        register("enchant", new EnchantCommand());
        register("god", new GodCommand(scheduler));
        register("hat", new HatCommand());
        ItemTextCommand itemText = new ItemTextCommand();
        register("lore", itemText);
        register("name", itemText);
        register("nickname", new NicknameCommand(scheduler));
        register("lightning", new LightningCommand(scheduler));
        register("remove", new RemoveCommand(scheduler));
        register("spawner", new SpawnerCommand());
        register("spawnmob", new SpawnMobCommand());
        register("sudo", new SudoCommand(scheduler));
        register("vanish", new VanishCommand(vanishManager));

        registerTweaks(scheduler);
        registerHelp();
        getLogger().info("Running on " + HcgPlatform.describe());
    }

    private void registerTweaks(HcgScheduler scheduler) {
        TweaksManager tweaks = new TweaksManager();
        TweaksGui gui = new TweaksGui(tweaks, scheduler);

        TweakEnchant veinEnchant = new TweakEnchant(this, "veinminer");
        VeinminerTweak veinminer = new VeinminerTweak(this, veinEnchant);
        tweaks.register(veinminer);

        TweakEnchant treeEnchant = new TweakEnchant(this, "treecapitator");
        TreecapitatorTweak treecapitator = new TreecapitatorTweak(this, treeEnchant, scheduler);
        tweaks.register(treecapitator);

        MobHealthTweak mobHealth = new MobHealthTweak(this);
        tweaks.register(mobHealth);
        mobHealthListener = new MobHealthListener(this, mobHealth, scheduler);

        XpTpTweak xpTp = new XpTpTweak(this);
        tweaks.register(xpTp);

        getServer().getPluginManager().registerEvents(new TweaksGuiListener(gui), this);
        getServer().getPluginManager().registerEvents(
                new VeinminerListener(veinminer, veinEnchant, scheduler), this);
        getServer().getPluginManager().registerEvents(
                new TreecapitatorListener(treecapitator, treeEnchant, scheduler), this);
        getServer().getPluginManager().registerEvents(mobHealthListener, this);
        getServer().getPluginManager().registerEvents(new XpTpListener(xpTp, scheduler), this);
        mobHealthListener.startAll();

        register("tweaks", new TweaksCommand(tweaks, gui));
        register("veinminer", new VeinminerCommand(veinminer, veinEnchant, gui, scheduler));
        register("treecapitator", new TreecapitatorCommand(treecapitator, treeEnchant, gui, scheduler));
        register("mobhealth", new MobHealthCommand(mobHealth, gui));
        register("xptp", new XpTpCommand(xpTp, gui));
    }

    @Override
    public void onDisable() {
        if (mobHealthListener != null) {
            mobHealthListener.shutdown();
        }
    }

    private void registerHelp() {
        HelpRegistry.register("Tweaks", HelpRegistry.ORDER_TWEAKS, List.of(
                new Entry("/tweaks", "Open the tweaks menu to turn tweaks on and off."),
                new Entry("/tweaks list", "List every tweak and its state in chat."),
                new Entry("/tweaks <tweak> [on|off]", "Check or set one tweak from the console."),
                new Entry("/veinminer", "Open the Veinminer chest menu (break one ore, break the vein)."),
                new Entry("/veinminer mode <shift|enchant|both>",
                        "Sneak to veinmine, need the enchant, or either."),
                new Entry("/veinminer hunger <on|off>", "Whether the extra blocks cost hunger."),
                new Entry("/veinminer durability <per-block|single>",
                        "Cost the tool one point per ore, or just one in total."),
                new Entry("/veinminer size <1-4096>", "Most extra blocks one break may take out."),
                new Entry("/veinminer grant|remove [player]",
                        "Add or remove the Veinminer enchant on a held tool."),
                new Entry("/treecapitator", "Open the Treecapitator chest menu (chop one log, fell the tree)."),
                new Entry("/treecapitator mode <shift|enchant|both>",
                        "Sneak to fell, need the enchant, or either."),
                new Entry("/treecapitator scope <whole-tree|above>",
                        "Fell every connected log, or only the logs above the break."),
                new Entry("/treecapitator animation <on|off>",
                        "Topple the logs as falling blocks that drop where they land."),
                new Entry("/treecapitator decay|replant <on|off>",
                        "Fast leaf decay, and replanting a sapling where the trunk stood."),
                new Entry("/treecapitator size <1-4096>", "Most extra logs one break may fell."),
                new Entry("/treecapitator grant|remove [player]",
                        "Add or remove the Treecapitator enchant on a held axe."),
                new Entry("/mobhealth", "Open the Mob Health Display chest menu (see mob health on look)."),
                new Entry("/mobhealth display <above-mob|action-bar>",
                        "Show health floating over the mob, or above the hotbar."),
                new Entry("/mobhealth style <hearts|numbers|both>",
                        "Draw the health as hearts, numbers, or both."),
                new Entry("/mobhealth range <1-32>", "How far away a looked-at mob still shows health."),
                new Entry("/mobhealth players <on|off>", "Also show other players' health."),
                new Entry("/xptp", "Open the XP Teleport chest menu (kill and mining XP skips the orbs)."),
                new Entry("/xptp mending <on|off>", "Repair Mending gear first, like real orbs."),
                new Entry("/xptp sound <on|off>", "Play the orb pickup sound when the XP arrives."),
                new Entry("/xptp blocks <on|off>",
                        "Also send XP from mined blocks (ores, spawners) to the miner."),
                new Entry("/xptp players <on|off>",
                        "Also send a slain player's dropped XP to their killer.")));

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
