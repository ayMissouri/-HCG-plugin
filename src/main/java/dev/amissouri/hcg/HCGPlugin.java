package dev.amissouri.hcg;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class HCGPlugin extends JavaPlugin {

    private DecayManager decayManager;
    private FreezeManager freezeManager;
    private RandomDropsManager randomDropsManager;
    private LavaRaiseManager lavaRaiseManager;
    private VolcanoManager volcanoManager;
    private HungerGamesManager hungerGamesManager;
    private NpcManager npcManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.init(this);

        decayManager = new DecayManager(this);
        freezeManager = new FreezeManager();
        randomDropsManager = new RandomDropsManager(this);
        lavaRaiseManager = new LavaRaiseManager(this);
        volcanoManager = new VolcanoManager(this);
        hungerGamesManager = new HungerGamesManager(this);
        npcManager = new NpcManager(this);

        getServer().getPluginManager().registerEvents(new KillListener(this, decayManager), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(freezeManager), this);
        getServer().getPluginManager().registerEvents(new RandomDropsListener(randomDropsManager), this);
        getServer().getPluginManager().registerEvents(lavaRaiseManager.burnTracker(), this);
        getServer().getPluginManager().registerEvents(new VolcanoListener(), this);
        if (npcManager.isAvailable()) {
            getServer().getPluginManager().registerEvents(new NpcListener(npcManager), this);
        }

        register("hcg", new HcgCommand());
        register("healthdecay", new DecayCommand(this, decayManager));
        register("randomdrops", new RandomDropsCommand(this, randomDropsManager));
        register("lavaraise", new LavaRaiseCommand(lavaRaiseManager));
        register("volcano", new VolcanoCommand(volcanoManager));
        register("hungergames", new HungerGamesCommand(hungerGamesManager));
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
        register("npc", new NpcCommand(this, npcManager));

        npcManager.load();

        if (getConfig().getBoolean("enabled", true)) {
            decayManager.start();
        }
    }

    @Override
    public void onDisable() {
        if (decayManager != null) {
            decayManager.shutdown();
        }
        if (lavaRaiseManager != null) {
            lavaRaiseManager.shutdown();
        }
        if (volcanoManager != null) {
            volcanoManager.shutdown();
        }
        if (hungerGamesManager != null) {
            hungerGamesManager.shutdown();
        }
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

    public DecayManager getDecayManager() {
        return decayManager;
    }

    public FreezeManager getFreezeManager() {
        return freezeManager;
    }

    public RandomDropsManager getRandomDropsManager() {
        return randomDropsManager;
    }
}
