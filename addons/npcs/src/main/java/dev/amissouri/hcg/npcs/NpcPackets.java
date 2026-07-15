package dev.amissouri.hcg.npcs;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class NpcPackets {

    private static final String HANDLER_NAME = "hcg_npc_handler";
    private static final byte GLOWING_FLAG = 0x40;
    private static final byte SKIN_LAYERS = 0x7E;

    enum ClickType { LEFT, RIGHT }

    interface ClickHandler {
        boolean onClick(Player player, int entityId, ClickType type);
    }

    // authlib
    private final Constructor<?> gameProfileCtor;
    private final Constructor<?> gameProfileWithPropertiesCtor;
    private final Constructor<?> propertyMapCtor;
    private final Method multimapCreate;
    private final Constructor<?> propertyCtor;
    private final Method getProperties;
    private final Method propertyMapPut;

    // server / entity
    private final Constructor<?> serverPlayerCtor;
    private final Method clientInfoDefault;
    private final Method craftServerGetServer;
    private final Method getId;
    private final Method snapTo;
    private final Method setYHeadRot;
    private final Method onGround;
    private final Field playerConnectionField;
    private final Method sendPacket;
    private final Field nettyConnectionField;
    private final Field channelField;
    // Resolved lazily off a live instance, and read from whichever region a viewer is in.
    private volatile Method craftPlayerGetHandle;
    private volatile Method craftWorldGetHandle;

    // packets
    private final Constructor<?> infoUpdateCtor;
    private final Class<?> infoActionClass;
    private final Class<?> infoEntryClass;
    private final Constructor<?> infoRemoveCtor;
    private final Constructor<?> addEntityCtor;
    private final Constructor<?> removeEntitiesCtor;
    private final Constructor<?> setDataCtor;
    private final Method dataValueCreate;
    private final Object sharedFlagsAccessor;
    private final Object skinLayersAccessor;
    private final Constructor<?> rotateHeadCtor;
    private final Constructor<?> moveRotCtor;
    private final Constructor<?> teleportEntityCtor;
    private final Constructor<?> teleportMoveCtor;
    private final Method positionMoveRotationOf;
    private final Constructor<?> equipmentCtor;
    private final Method pairOf;
    private final Class<?> nmsEquipmentSlotClass;
    private final Method asNmsCopy;
    private final Object playerEntityType;
    private final Object vec3Zero;
    private final Object survivalGameType;

    // Client-side team, used only to hide the NPC's profile nametag and carry collision/glow colour.
    private final Object teamScoreboard;
    private final Constructor<?> playerTeamCtor;
    private final Method teamSetNameTagVisibility;
    private final Method teamSetCollisionRule;
    private final Method teamSetColor;
    private final Method teamGetPlayers;
    private final Method teamAddOrModifyPacket;
    private final Method teamRemovePacket;
    private final Object visibilityNever;
    private final Class<?> collisionRuleClass;
    private final Class<?> chatFormattingClass;

    // serverbound click packets
    private final Class<?> interactPacketClass;
    private final Field interactEntityIdField;
    private final Field interactActionField;
    private final Method actionGetType;
    private final Field interactHandField;
    private final Class<?> attackPacketClass;
    private final Field attackEntityIdField;
    private final Class<?> interactionHandClass;

    /** Why the client-side team is unavailable, or null when it resolved. Reported by createOrNull. */
    private String teamUnavailable;

    static NpcPackets createOrNull(Logger logger) {
        try {
            NpcPackets packets = new NpcPackets();
            if (packets.teamUnavailable != null) {
                logger.warning("NPC nametags can't be hidden and /npc glowing|collidable won't apply:"
                        + " the client-side team internals weren't recognized (" + packets.teamUnavailable
                        + "). NPCs otherwise work.");
            }
            return packets;
        } catch (Throwable t) {
            logger.warning("NPC support disabled, server internals not recognized ("
                    + t.getClass().getSimpleName() + ": " + t.getMessage() + ")."
                    + " NPCs need a Mojang-mapped Paper 1.21.x or 26.x server.");
            return null;
        }
    }

    private NpcPackets() throws ReflectiveOperationException {
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
        gameProfileCtor = gameProfileClass.getConstructor(UUID.class, String.class);
        propertyCtor = propertyClass.getConstructor(String.class, String.class, String.class);
        getProperties = findAnyMethod(gameProfileClass, new String[]{"properties", "getProperties"});
        propertyMapPut = Class.forName("com.google.common.collect.Multimap")
                .getMethod("put", Object.class, Object.class);

        Constructor<?> withProperties = null;
        Constructor<?> propertyMap = null;
        Method createMultimap = null;
        try {
            Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
            withProperties = gameProfileClass.getConstructor(UUID.class, String.class, propertyMapClass);
            propertyMap = propertyMapClass.getConstructor(Class.forName("com.google.common.collect.Multimap"));
            createMultimap = Class.forName("com.google.common.collect.LinkedHashMultimap").getMethod("create");
        } catch (ReflectiveOperationException ignored) {
            withProperties = null;
            propertyMap = null;
            createMultimap = null;
        }
        gameProfileWithPropertiesCtor = withProperties;
        propertyMapCtor = propertyMap;
        multimapCreate = createMultimap;

        Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Class<?> clientInfoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");

        clientInfoDefault = clientInfoClass.getMethod("createDefault");
        serverPlayerCtor = serverPlayerClass.getConstructor(
                minecraftServerClass, serverLevelClass, gameProfileClass, clientInfoClass);
        craftServerGetServer = Bukkit.getServer().getClass().getMethod("getServer");
        getId = entityClass.getMethod("getId");
        // Entity.moveTo is now snapTo in 26.
        snapTo = findAnyMethod(entityClass, new String[]{"snapTo", "moveTo"},
                double.class, double.class, double.class, float.class, float.class);
        setYHeadRot = entityClass.getMethod("setYHeadRot", float.class);
        onGround = entityClass.getMethod("onGround");

        playerConnectionField = serverPlayerClass.getField("connection");
        sendPacket = findMethod(playerConnectionField.getType(), "send", packetClass);
        nettyConnectionField = findFieldByType(playerConnectionField.getType(), connectionClass);
        channelField = findFieldByType(connectionClass, Channel.class);

        String proto = "net.minecraft.network.protocol.game.";
        Class<?> infoUpdateClass = Class.forName(proto + "ClientboundPlayerInfoUpdatePacket");
        infoActionClass = Class.forName(proto + "ClientboundPlayerInfoUpdatePacket$Action");
        infoEntryClass = Class.forName(proto + "ClientboundPlayerInfoUpdatePacket$Entry");
        infoUpdateCtor = infoUpdateClass.getConstructor(EnumSet.class, List.class);
        infoRemoveCtor = Class.forName(proto + "ClientboundPlayerInfoRemovePacket")
                .getConstructor(List.class);

        Class<?> addEntityClass = Class.forName(proto + "ClientboundAddEntityPacket");
        Constructor<?> found = null;
        for (Constructor<?> ctor : addEntityClass.getConstructors()) {
            if (ctor.getParameterCount() == 11) {
                found = ctor;
            }
        }
        if (found == null) {
            throw new NoSuchMethodException("ClientboundAddEntityPacket 11-arg constructor");
        }
        addEntityCtor = found;

        removeEntitiesCtor = Class.forName(proto + "ClientboundRemoveEntitiesPacket")
                .getConstructor(int[].class);
        setDataCtor = Class.forName(proto + "ClientboundSetEntityDataPacket")
                .getConstructor(int.class, List.class);

        Class<?> accessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
        Class<?> dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
        dataValueCreate = dataValueClass.getMethod("create", accessorClass, Object.class);
        sharedFlagsAccessor = staticFieldValue(entityClass, "DATA_SHARED_FLAGS_ID");
        skinLayersAccessor = staticFieldValue(serverPlayerClass, "DATA_PLAYER_MODE_CUSTOMISATION");

        Object scoreboard = null;
        Constructor<?> teamCtor = null;
        Method setVisibility = null;
        Method setCollision = null;
        Method setColour = null;
        Method players = null;
        Method addOrModify = null;
        Method removeTeam = null;
        Object never = null;
        Class<?> collisionClass = null;
        Class<?> formattingClass = null;
        try {
            Class<?> scoreboardClass = Class.forName("net.minecraft.world.scores.Scoreboard");
            Class<?> playerTeamClass = Class.forName("net.minecraft.world.scores.PlayerTeam");
            Class<?> visibilityClass = Class.forName("net.minecraft.world.scores.Team$Visibility");
            collisionClass = Class.forName("net.minecraft.world.scores.Team$CollisionRule");
            formattingClass = Class.forName("net.minecraft.ChatFormatting");
            Class<?> teamPacketClass = Class.forName(proto + "ClientboundSetPlayerTeamPacket");
            scoreboard = scoreboardClass.getConstructor().newInstance();
            teamCtor = playerTeamClass.getConstructor(scoreboardClass, String.class);
            setVisibility = playerTeamClass.getMethod("setNameTagVisibility", visibilityClass);
            setCollision = playerTeamClass.getMethod("setCollisionRule", collisionClass);
            setColour = playerTeamClass.getMethod("setColor", formattingClass);
            players = playerTeamClass.getMethod("getPlayers");
            addOrModify = teamPacketClass.getMethod("createAddOrModifyPacket", playerTeamClass, boolean.class);
            removeTeam = teamPacketClass.getMethod("createRemovePacket", playerTeamClass);
            never = visibilityClass.getField("NEVER").get(null);
        } catch (Throwable t) {
            scoreboard = null;
            teamUnavailable = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        teamScoreboard = scoreboard;
        playerTeamCtor = teamCtor;
        teamSetNameTagVisibility = setVisibility;
        teamSetCollisionRule = setCollision;
        teamSetColor = setColour;
        teamGetPlayers = players;
        teamAddOrModifyPacket = addOrModify;
        teamRemovePacket = removeTeam;
        visibilityNever = never;
        collisionRuleClass = collisionClass;
        chatFormattingClass = formattingClass;

        rotateHeadCtor = Class.forName(proto + "ClientboundRotateHeadPacket")
                .getConstructor(entityClass, byte.class);
        moveRotCtor = Class.forName(proto + "ClientboundMoveEntityPacket$Rot")
                .getConstructor(int.class, byte.class, byte.class, boolean.class);

        Class<?> teleportClass = Class.forName(proto + "ClientboundTeleportEntityPacket");
        Constructor<?> legacyTeleport = null;
        try {
            legacyTeleport = teleportClass.getConstructor(entityClass);
        } catch (NoSuchMethodException ignored) {
        }
        teleportEntityCtor = legacyTeleport;
        Constructor<?> moveTeleport = null;
        Method moveOf = null;
        try {
            Class<?> moveClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation");
            moveOf = moveClass.getMethod("of", entityClass);
            moveTeleport = teleportClass.getConstructor(int.class, moveClass, Set.class, boolean.class);
        } catch (ReflectiveOperationException ignored) {
        }
        teleportMoveCtor = moveTeleport;
        positionMoveRotationOf = moveOf;

        equipmentCtor = Class.forName(proto + "ClientboundSetEquipmentPacket")
                .getConstructor(int.class, List.class);
        pairOf = Class.forName("com.mojang.datafixers.util.Pair")
                .getMethod("of", Object.class, Object.class);
        nmsEquipmentSlotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot");
        String craftPackage = Bukkit.getServer().getClass().getPackageName();
        asNmsCopy = Class.forName(craftPackage + ".inventory.CraftItemStack")
                .getMethod("asNMSCopy", ItemStack.class);
        playerEntityType = playerEntityType();
        vec3Zero = Class.forName("net.minecraft.world.phys.Vec3").getField("ZERO").get(null);
        survivalGameType = enumConstant(Class.forName("net.minecraft.world.level.GameType"), "SURVIVAL");

        interactPacketClass = Class.forName(proto + "ServerboundInteractPacket");
        interactEntityIdField = findFieldByType(interactPacketClass, int.class);
        interactionHandClass = Class.forName("net.minecraft.world.InteractionHand");

        Class<?> actionClass = classOrNull(proto + "ServerboundInteractPacket$Action");
        if (actionClass != null) {
            interactActionField = findFieldByType(interactPacketClass, actionClass);
            actionGetType = actionClass.getDeclaredMethod("getType");
            actionGetType.setAccessible(true);
            interactHandField = null;
            attackPacketClass = null;
            attackEntityIdField = null;
        } else {
            interactActionField = null;
            actionGetType = null;
            interactHandField = findFieldByType(interactPacketClass, interactionHandClass);
            attackPacketClass = Class.forName(proto + "ServerboundAttackPacket");
            attackEntityIdField = findFieldByType(attackPacketClass, int.class);
        }
    }

    Object createEntity(World world, UUID uuid, String profileName,
                        String skinValue, String skinSignature, Location location) throws Exception {
        Object profile = profile(uuid, profileName, skinValue, skinSignature);
        if (craftWorldGetHandle == null) {
            craftWorldGetHandle = world.getClass().getMethod("getHandle");
        }
        Object level = craftWorldGetHandle.invoke(world);
        Object server = craftServerGetServer.invoke(Bukkit.getServer());
        Object entity = serverPlayerCtor.newInstance(server, level, profile, clientInfoDefault.invoke(null));
        position(entity, location);
        return entity;
    }

    private Object profile(UUID uuid, String profileName, String skinValue, String skinSignature)
            throws Exception {
        if (skinValue == null || skinSignature == null) {
            return gameProfileCtor.newInstance(uuid, profileName);
        }
        Object property = propertyCtor.newInstance("textures", skinValue, skinSignature);
        if (gameProfileWithPropertiesCtor == null) {
            Object profile = gameProfileCtor.newInstance(uuid, profileName);
            propertyMapPut.invoke(getProperties.invoke(profile), "textures", property);
            return profile;
        }
        Object properties = multimapCreate.invoke(null);
        propertyMapPut.invoke(properties, "textures", property);
        return gameProfileWithPropertiesCtor.newInstance(uuid, profileName,
                propertyMapCtor.newInstance(properties));
    }

    int entityId(Object entity) throws Exception {
        return (int) getId.invoke(entity);
    }

    void position(Object entity, Location location) throws Exception {
        snapTo.invoke(entity, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
        setYHeadRot.invoke(entity, location.getYaw());
    }

    void sendSpawn(Player viewer, Object entity, UUID uuid, Object gameProfile,
                   boolean listed, Location location, boolean glowing) throws Exception {
        sendPacket(viewer, infoUpdatePacket(uuid, gameProfile, listed));
        sendPacket(viewer, addEntityCtor.newInstance(
                entityId(entity), uuid,
                location.getX(), location.getY(), location.getZ(),
                location.getPitch(), location.getYaw(),
                playerEntityType, 0, vec3Zero, (double) location.getYaw()));
        sendMetadata(viewer, entityId(entity), glowing);
        sendPacket(viewer, rotateHeadCtor.newInstance(entity, rotByte(location.getYaw())));
    }

    Object gameProfile(Object entity) throws Exception {
        return entity.getClass().getMethod("getGameProfile").invoke(entity);
    }

    void sendDespawn(Player viewer, int entityId, UUID uuid) throws Exception {
        sendPacket(viewer, removeEntitiesCtor.newInstance((Object) new int[]{entityId}));
        sendPacket(viewer, infoRemoveCtor.newInstance(List.of(uuid)));
    }

    void sendMetadata(Player viewer, int entityId, boolean glowing) throws Exception {
        List<Object> values = List.of(
                dataValueCreate.invoke(null, sharedFlagsAccessor, (byte) (glowing ? GLOWING_FLAG : 0)),
                dataValueCreate.invoke(null, skinLayersAccessor, SKIN_LAYERS));
        sendPacket(viewer, setDataCtor.newInstance(entityId, values));
    }

    void sendRotation(Player viewer, Object entity, int entityId, float yaw, float pitch) throws Exception {
        sendPacket(viewer, moveRotCtor.newInstance(entityId, rotByte(yaw), rotByte(pitch), true));
        sendPacket(viewer, rotateHeadCtor.newInstance(entity, rotByte(yaw)));
    }

    boolean canTeleport() {
        return teleportMoveCtor != null || teleportEntityCtor != null;
    }

    /** False when the team lookups didn't resolve; NPCs then show their profile name. */
    boolean canTeam() {
        return teamScoreboard != null;
    }

    @SuppressWarnings("unchecked")
    Object createTeam(String teamName, String profileName, boolean collidable, String glowColor)
            throws Exception {
        Object team = playerTeamCtor.newInstance(teamScoreboard, teamName);
        teamSetNameTagVisibility.invoke(team, visibilityNever);
        teamSetCollisionRule.invoke(team, enumOrNull(collisionRuleClass, collidable ? "ALWAYS" : "NEVER"));
        Object colour = enumOrNull(chatFormattingClass, glowColor);
        if (colour == null) {
            colour = enumOrNull(chatFormattingClass, "WHITE");
        }
        if (colour != null) {
            teamSetColor.invoke(team, colour);
        }
        ((Collection<String>) teamGetPlayers.invoke(team)).add(profileName);
        return team;
    }

    void sendTeam(Player viewer, Object team) throws Exception {
        sendPacket(viewer, teamAddOrModifyPacket.invoke(null, team, true));
    }

    void sendTeamRemove(Player viewer, Object team) throws Exception {
        sendPacket(viewer, teamRemovePacket.invoke(null, team));
    }

    private static Object enumOrNull(Class<?> type, String name) {
        try {
            return type.getField(name.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    void sendTeleport(Player viewer, Object entity, Location location) throws Exception {
        Object packet;
        if (teleportMoveCtor != null) {
            packet = teleportMoveCtor.newInstance(entityId(entity),
                    positionMoveRotationOf.invoke(null, entity), Set.of(), onGround.invoke(entity));
        } else {
            packet = teleportEntityCtor.newInstance(entity);
        }
        sendPacket(viewer, packet);
        sendPacket(viewer, rotateHeadCtor.newInstance(entity, rotByte(location.getYaw())));
    }

    void sendEquipment(Player viewer, int entityId, java.util.Map<String, ItemStack> equipment) throws Exception {
        List<Object> pairs = new ArrayList<>();
        for (String slot : NpcData.SLOTS) {
            ItemStack item = equipment.get(slot);
            if (item == null) {
                item = new ItemStack(Material.AIR);
            }
            pairs.add(pairOf.invoke(null, nmsSlot(slot), asNmsCopy.invoke(null, item)));
        }
        sendPacket(viewer, equipmentCtor.newInstance(entityId, pairs));
    }

    private Object nmsSlot(String slot) {
        String nms = switch (slot) {
            case "mainhand" -> "MAINHAND";
            case "offhand" -> "OFFHAND";
            case "head" -> "HEAD";
            case "chest" -> "CHEST";
            case "legs" -> "LEGS";
            default -> "FEET";
        };
        return enumConstant(nmsEquipmentSlotClass, nms);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object infoUpdatePacket(UUID uuid, Object gameProfile, boolean listed) throws Exception {
        EnumSet actions = EnumSet.noneOf((Class) infoActionClass);
        actions.add(enumConstant(infoActionClass, "ADD_PLAYER"));
        actions.add(enumConstant(infoActionClass, "UPDATE_LISTED"));

        Object updateHat = enumConstantOrNull(infoActionClass, "UPDATE_HAT");
        if (updateHat != null) {
            actions.add(updateHat);
        }

        RecordComponent[] components = infoEntryClass.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            args[i] = switch (components[i].getName()) {
                case "profileId" -> uuid;
                case "profile" -> gameProfile;
                case "listed" -> listed;
                case "gameMode" -> survivalGameType;
                case "showHat" -> true;
                default -> defaultValue(types[i]);
            };
        }
        Constructor<?> entryCtor = infoEntryClass.getDeclaredConstructor(types);
        entryCtor.setAccessible(true);
        return infoUpdateCtor.newInstance(actions, List.of(entryCtor.newInstance(args)));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }

    private void sendPacket(Player viewer, Object packet) throws Exception {
        if (craftPlayerGetHandle == null) {
            craftPlayerGetHandle = viewer.getClass().getMethod("getHandle");
        }
        Object handle = craftPlayerGetHandle.invoke(viewer);
        sendPacket.invoke(playerConnectionField.get(handle), packet);
    }

    static byte rotByte(float degrees) {
        return (byte) Math.floor(degrees * 256.0f / 360.0f);
    }

    void inject(Player player, ClickHandler handler) {
        try {
            Channel channel = channel(player);
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (consumeClick(player, msg, handler)) {
                        return;
                    }
                    super.channelRead(ctx, msg);
                }
            });
        } catch (Exception ignored) {}
    }

    void uninject(Player player) {
        try {
            Channel channel = channel(player);
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        } catch (Exception ignored) {
        }
    }

    private Channel channel(Player player) throws Exception {
        if (craftPlayerGetHandle == null) {
            craftPlayerGetHandle = player.getClass().getMethod("getHandle");
        }
        Object handle = craftPlayerGetHandle.invoke(player);
        Object connection = playerConnectionField.get(handle);
        return (Channel) channelField.get(nettyConnectionField.get(connection));
    }

    private boolean consumeClick(Player player, Object msg, ClickHandler handler) throws Exception {
        if (attackPacketClass != null && attackPacketClass.isInstance(msg)) {
            return handler.onClick(player, attackEntityIdField.getInt(msg), ClickType.LEFT);
        }
        if (interactPacketClass.isInstance(msg)) {
            ClickType click = parseClick(msg);
            return click != null && handler.onClick(player, interactEntityIdField.getInt(msg), click);
        }
        return false;
    }

    private ClickType parseClick(Object packet) throws Exception {
        if (interactActionField == null) {
            return isMainHand(interactHandField.get(packet)) ? ClickType.RIGHT : null;
        }
        Object action = interactActionField.get(packet);
        String type = ((Enum<?>) actionGetType.invoke(action)).name();
        if (type.equals("ATTACK")) {
            return ClickType.LEFT;
        }
        if (!type.equals("INTERACT")) {
            return null;
        }
        Field handField = findFieldByTypeOrNull(action.getClass(), interactionHandClass);
        return handField == null || isMainHand(handField.get(action)) ? ClickType.RIGHT : null;
    }

    private static boolean isMainHand(Object hand) {
        return hand != null && ((Enum<?>) hand).name().equals("MAIN_HAND");
    }

    private static Object playerEntityType() throws ReflectiveOperationException {
        for (String owner : new String[]{"net.minecraft.world.entity.EntityTypes",
                "net.minecraft.world.entity.EntityType"}) {
            Class<?> type = classOrNull(owner);
            if (type != null) {
                try {
                    return type.getField("PLAYER").get(null);
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        throw new NoSuchFieldException("EntityType.PLAYER");
    }

    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Object staticFieldValue(Class<?> owner, String name) throws ReflectiveOperationException {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name + " on " + owner.getName());
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        Object constant = enumConstantOrNull(enumClass, name);
        if (constant == null) {
            throw new IllegalArgumentException(name + " not in " + enumClass.getName());
        }
        return constant;
    }

    private static Object enumConstantOrNull(Class<?> enumClass, String name) {
        for (Object constant : enumClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        return null;
    }

    private static Method findAnyMethod(Class<?> owner, String[] names, Class<?>... params)
            throws NoSuchMethodException {
        for (String name : names) {
            try {
                return findMethod(owner, name, params);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(String.join("/", names) + " on " + owner.getName());
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... params)
            throws NoSuchMethodException {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name, params);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name + " on " + owner.getName());
    }

    private static Field findFieldByType(Class<?> owner, Class<?> type) throws NoSuchFieldException {
        Field field = findFieldByTypeOrNull(owner, type);
        if (field == null) {
            throw new NoSuchFieldException(type.getName() + " field on " + owner.getName());
        }
        return field;
    }

    private static Field findFieldByTypeOrNull(Class<?> owner, Class<?> type) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType() == type) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }
}
