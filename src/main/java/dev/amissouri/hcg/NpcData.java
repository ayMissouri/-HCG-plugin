package dev.amissouri.hcg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public final class NpcData {

    public static final List<String> SLOTS = List.of(
            "mainhand", "offhand", "head", "chest", "legs", "feet");
    public static final List<String> TRIGGERS = List.of(
            "left_click", "right_click", "any_click");

    private final String name;
    private final UUID uuid;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String displayName;
    private String skinValue;
    private String skinSignature;
    private String skinSource;
    private String glowColor = "off";
    private boolean collidable = false;
    private boolean showInTab = false;
    private boolean turnToPlayer = false;
    private double turnDistance = 5.0;
    private long cooldownMillis = 0;
    private final Map<String, ItemStack> equipment = new LinkedHashMap<>();
    private final Map<String, List<String>> actions = new LinkedHashMap<>();

    public NpcData(String name, UUID uuid, Location location) {
        this(name, uuid);
        setLocation(location);
    }

    private NpcData(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
        this.displayName = name;
    }

    /** The 16-char-max hidden profile name clients see internally (nametag is hidden). */
    public String profileName() {
        return "NPC" + uuid.toString().replace("-", "").substring(0, 13);
    }

    public String teamName() {
        return "npc_" + uuid.toString().replace("-", "").substring(0, 12);
    }

    public String name() {
        return name;
    }

    public UUID uuid() {
        return uuid;
    }

    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public String worldName() {
        return world;
    }

    public void setLocation(Location location) {
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean hasHologram() {
        return displayName != null && !displayName.equalsIgnoreCase("none");
    }

    public String skinValue() {
        return skinValue;
    }

    public String skinSignature() {
        return skinSignature;
    }

    public String skinSource() {
        return skinSource;
    }

    public void setSkin(String value, String signature, String source) {
        this.skinValue = value;
        this.skinSignature = signature;
        this.skinSource = source;
    }

    public String glowColor() {
        return glowColor;
    }

    public boolean isGlowing() {
        return !"off".equalsIgnoreCase(glowColor);
    }

    public void setGlowColor(String glowColor) {
        this.glowColor = glowColor.toLowerCase(Locale.ROOT);
    }

    public boolean isCollidable() {
        return collidable;
    }

    public void setCollidable(boolean collidable) {
        this.collidable = collidable;
    }

    public boolean isShowInTab() {
        return showInTab;
    }

    public void setShowInTab(boolean showInTab) {
        this.showInTab = showInTab;
    }

    public boolean isTurnToPlayer() {
        return turnToPlayer;
    }

    public void setTurnToPlayer(boolean turnToPlayer) {
        this.turnToPlayer = turnToPlayer;
    }

    public double turnDistance() {
        return turnDistance;
    }

    public void setTurnDistance(double turnDistance) {
        this.turnDistance = turnDistance;
    }

    public long cooldownMillis() {
        return cooldownMillis;
    }

    public void setCooldownMillis(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    public Map<String, ItemStack> equipment() {
        return equipment;
    }

    /** Actions per trigger, each entry is "type args...". */
    public List<String> actions(String trigger) {
        return actions.computeIfAbsent(trigger, k -> new ArrayList<>());
    }

    public Map<String, List<String>> allActions() {
        return actions;
    }

    public void save(ConfigurationSection section) {
        section.set("uuid", uuid.toString());
        section.set("world", world);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", yaw);
        section.set("pitch", pitch);
        section.set("display-name", displayName);
        section.set("skin.value", skinValue);
        section.set("skin.signature", skinSignature);
        section.set("skin.source", skinSource);
        section.set("glowing", glowColor);
        section.set("collidable", collidable);
        section.set("show-in-tab", showInTab);
        section.set("turn-to-player", turnToPlayer);
        section.set("turn-distance", turnDistance);
        section.set("cooldown-millis", cooldownMillis);
        section.set("equipment", null);
        for (Map.Entry<String, ItemStack> entry : equipment.entrySet()) {
            section.set("equipment." + entry.getKey(), entry.getValue());
        }
        section.set("actions", null);
        for (Map.Entry<String, List<String>> entry : actions.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                section.set("actions." + entry.getKey(), entry.getValue());
            }
        }
    }

    public static NpcData load(String name, ConfigurationSection section) {
        String uuidText = section.getString("uuid");
        UUID uuid = uuidText == null ? UUID.randomUUID() : UUID.fromString(uuidText);
        NpcData data = new NpcData(name, uuid);
        data.world = section.getString("world", "world");
        data.x = section.getDouble("x");
        data.y = section.getDouble("y");
        data.z = section.getDouble("z");
        data.yaw = (float) section.getDouble("yaw");
        data.pitch = (float) section.getDouble("pitch");
        data.displayName = section.getString("display-name", name);
        data.skinValue = section.getString("skin.value");
        data.skinSignature = section.getString("skin.signature");
        data.skinSource = section.getString("skin.source");
        data.glowColor = section.getString("glowing", "off");
        data.collidable = section.getBoolean("collidable", false);
        data.showInTab = section.getBoolean("show-in-tab", false);
        data.turnToPlayer = section.getBoolean("turn-to-player", false);
        data.turnDistance = section.getDouble("turn-distance", 5.0);
        data.cooldownMillis = section.getLong("cooldown-millis", 0);
        ConfigurationSection equip = section.getConfigurationSection("equipment");
        if (equip != null) {
            for (String slot : equip.getKeys(false)) {
                ItemStack item = equip.getItemStack(slot);
                if (item != null && SLOTS.contains(slot)) {
                    data.equipment.put(slot, item);
                }
            }
        }
        ConfigurationSection acts = section.getConfigurationSection("actions");
        if (acts != null) {
            for (String trigger : acts.getKeys(false)) {
                if (TRIGGERS.contains(trigger)) {
                    data.actions.put(trigger, new ArrayList<>(acts.getStringList(trigger)));
                }
            }
        }
        return data;
    }
}
