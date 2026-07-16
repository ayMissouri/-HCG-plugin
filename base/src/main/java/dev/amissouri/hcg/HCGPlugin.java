package dev.amissouri.hcg;

import java.util.List;

import dev.amissouri.hcg.HelpRegistry.Entry;
import dev.amissouri.hcg.menu.MenuListener;
import dev.amissouri.hcg.tweaks.FarmingCommand;
import dev.amissouri.hcg.tweaks.FarmingListener;
import dev.amissouri.hcg.tweaks.FarmingTweak;
import dev.amissouri.hcg.tweaks.MobHealthCommand;
import dev.amissouri.hcg.tweaks.MobHealthListener;
import dev.amissouri.hcg.tweaks.MobHealthTweak;
import dev.amissouri.hcg.tweaks.PathSprintCommand;
import dev.amissouri.hcg.tweaks.PathSprintListener;
import dev.amissouri.hcg.tweaks.PathSprintTweak;
import dev.amissouri.hcg.tweaks.RecoveryCompassCommand;
import dev.amissouri.hcg.tweaks.RecoveryCompassListener;
import dev.amissouri.hcg.tweaks.RecoveryCompassTweak;
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
import dev.amissouri.hcg.tweaks.VoidTotemCommand;
import dev.amissouri.hcg.tweaks.VoidTotemListener;
import dev.amissouri.hcg.tweaks.VoidTotemTweak;
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

        FarmingTweak farming = new FarmingTweak(this);
        tweaks.register(farming);

        MobHealthTweak mobHealth = new MobHealthTweak(this);
        tweaks.register(mobHealth);
        mobHealthListener = new MobHealthListener(this, mobHealth, scheduler);

        XpTpTweak xpTp = new XpTpTweak(this);
        tweaks.register(xpTp);

        VoidTotemTweak voidTotem = new VoidTotemTweak(this);
        tweaks.register(voidTotem);

        PathSprintTweak pathSprint = new PathSprintTweak(this);
        tweaks.register(pathSprint);
        PathSprintListener pathSprintListener = new PathSprintListener(pathSprint, scheduler);

        RecoveryCompassTweak recoveryCompass = new RecoveryCompassTweak(this);
        tweaks.register(recoveryCompass);

        getServer().getPluginManager().registerEvents(new TweaksGuiListener(gui), this);
        getServer().getPluginManager().registerEvents(
                new VeinminerListener(veinminer, veinEnchant, scheduler), this);
        getServer().getPluginManager().registerEvents(
                new TreecapitatorListener(treecapitator, treeEnchant, scheduler), this);
        getServer().getPluginManager().registerEvents(new FarmingListener(farming), this);
        getServer().getPluginManager().registerEvents(mobHealthListener, this);
        getServer().getPluginManager().registerEvents(new XpTpListener(xpTp, scheduler), this);
        getServer().getPluginManager().registerEvents(new VoidTotemListener(voidTotem, scheduler), this);
        getServer().getPluginManager().registerEvents(pathSprintListener, this);
        getServer().getPluginManager().registerEvents(
                new RecoveryCompassListener(this, recoveryCompass, scheduler), this);
        mobHealthListener.startAll();
        pathSprintListener.startAll();

        register("tweaks", new TweaksCommand(tweaks, gui));
        register("veinminer", new VeinminerCommand(veinminer, veinEnchant, gui, scheduler));
        register("treecapitator", new TreecapitatorCommand(treecapitator, treeEnchant, gui, scheduler));
        register("farming", new FarmingCommand(farming, gui));
        register("mobhealth", new MobHealthCommand(mobHealth, gui));
        register("xptp", new XpTpCommand(xpTp, gui));
        register("voidtotem", new VoidTotemCommand(voidTotem, gui));
        register("pathsprint", new PathSprintCommand(pathSprint, gui));
        register("recoverycompass", new RecoveryCompassCommand(recoveryCompass, gui));
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
                new Entry("/farming", "Open the Farming chest menu (right-click to harvest and replant crops)."),
                new Entry("/farming harvest <on|off>",
                        "Right-click a grown crop to harvest and replant it in place."),
                new Entry("/farming replant <on|off>",
                        "Replant a grown crop when it's broken, paid for from its own drops."),
                new Entry("/farming xp <on|off>", "Grant experience when a crop is harvested."),
                new Entry("/farming radius <0-8>",
                        "Right-click harvest every grown crop within this many blocks."),
                new Entry("/farming hoe|durability <on|off>",
                        "Require a hoe to right-click harvest, and whether it wears the hoe down."),
                new Entry("/farming twerk <on|off>",
                        "TwerkGrow: spam-sneak to nudge nearby crops up a growth stage now and then."),
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
                        "Also send a slain player's dropped XP to their killer."),
                new Entry("/voidtotem", "Open the Void Totem chest menu (a held totem saves you from the void)."),
                new Entry("/voidtotem level <1-10>",
                        "How strongly the levitation floats you up out of the void."),
                new Entry("/voidtotem consume <on|off>",
                        "Use up the totem on each save, or make a held totem unlimited protection."),
                new Entry("/voidtotem effect <on|off>",
                        "Play the totem's pop animation and sound when it saves you."),
                new Entry("/pathsprint", "Open the Path Sprint chest menu (paths give a speed boost)."),
                new Entry("/pathsprint speed <1-3>", "How much faster you run on a path."),
                new Entry("/pathsprint sprint <on|off>",
                        "Only boost while sprinting, or while walking on a path too."),
                new Entry("/pathsprint particles <on|off>",
                        "Show the speed particle trail while the boost is active."),
                new Entry("/recoverycompass",
                        "Open the Recovery Compass chest menu (respawn with a compass to your death)."),
                new Entry("/recoverycompass coordinates <on|off>",
                        "Write the death coordinates into the compass lore."),
                new Entry("/recoverycompass glow <on|off>",
                        "Give the compass an enchant glint so it stands out.")));

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
