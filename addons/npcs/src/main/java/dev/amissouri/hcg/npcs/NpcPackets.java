package dev.amissouri.hcg.npcs;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
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

    enum ClickType { LEFT, RIGHT }

    interface ClickHandler {
        boolean onClick(Player player, int entityId, ClickType type);
    }

    // authlib
    private final Constructor<?> gameProfileCtor;
    private final Constructor<?> propertyCtor;
    private final Method getProperties;
    private final Method propertyMapPut;

    // server / entity
    private final Constructor<?> serverPlayerCtor;
    private final Method clientInfoDefault;
    private final Method craftServerGetServer;
    private final Method getId;
    private final Method moveTo;
    private final Method setYHeadRot;
    private final Field playerConnectionField;
    private final Method sendPacket;
    private final Field nettyConnectionField;
    private final Field channelField;
    private Method craftPlayerGetHandle;
    private Method craftWorldGetHandle;

    // packets
    private final Constructor<?> infoUpdateCtor;
    private final Class<?> infoActionClass;
    private final Field infoEntriesField;
    private final Class<?> infoEntryClass;
    private final Constructor<?> infoRemoveCtor;
    private final Constructor<?> addEntityCtor;
    private final Constructor<?> removeEntitiesCtor;
    private final Constructor<?> setDataCtor;
    private final Constructor<?> dataValueCtor;
    private final Object byteSerializer;
    private final Constructor<?> rotateHeadCtor;
    private final Constructor<?> moveRotCtor;
    private final Constructor<?> teleportCtor;
    private final Constructor<?> equipmentCtor;
    private final Method pairOf;
    private final Class<?> nmsEquipmentSlotClass;
    private final Method asNmsCopy;
    private final Object playerEntityType;
    private final Object vec3Zero;
    private final Object survivalGameType;

    // serverbound interact packet
    private final Class<?> interactPacketClass;
    private final Field interactEntityIdField;
    private final Field interactActionField;
    private final Method actionGetType;
    private final Class<?> interactionHandClass;

    static NpcPackets createOrNull(Logger logger) {
        try {
            return new NpcPackets();
        } catch (Throwable t) {
            logger.warning("NPC support disabled, server internals not recognized ("
                    + t.getClass().getSimpleName() + ": " + t.getMessage() + ")."
                    + " NPCs need a Mojang-mapped Paper 1.21.x server.");
            return null;
        }
    }

    private NpcPackets() throws ReflectiveOperationException {
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
        gameProfileCtor = gameProfileClass.getConstructor(UUID.class, String.class);
        propertyCtor = propertyClass.getConstructor(String.class, String.class, String.class);
        getProperties = gameProfileClass.getMethod("getProperties");
        propertyMapPut = Class.forName("com.google.common.collect.Multimap")
                .getMethod("put", Object.class, Object.class);

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
        moveTo = entityClass.getMethod("moveTo",
                double.class, double.class, double.class, float.class, float.class);
        setYHeadRot = entityClass.getMethod("setYHeadRot", float.class);

        playerConnectionField = serverPlayerClass.getField("connection");
        sendPacket = findMethod(playerConnectionField.getType(), "send", packetClass);
        nettyConnectionField = findFieldByType(playerConnectionField.getType(), connectionClass);
        channelField = findFieldByType(connectionClass, Channel.class);

        String proto = "net.minecraft.network.protocol.game.";
        Class<?> infoUpdateClass = Class.forName(proto + "ClientboundPlayerInfoUpdatePacket");
        infoActionClass = Class.forName(proto + "ClientboundPlayerInfoUpdatePacket$Action");
        infoEntryClass = Class.forName(proto + "ClientboundPlayerInfoUpdatePacket$Entry");
        infoUpdateCtor = infoUpdateClass.getConstructor(EnumSet.class, Collection.class);
        infoEntriesField = findFieldByType(infoUpdateClass, List.class);
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
        Class<?> dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
        dataValueCtor = dataValueClass.getDeclaredConstructors()[0];
        dataValueCtor.setAccessible(true);
        byteSerializer = Class.forName("net.minecraft.network.syncher.EntityDataSerializers")
                .getField("BYTE").get(null);
        rotateHeadCtor = Class.forName(proto + "ClientboundRotateHeadPacket")
                .getConstructor(entityClass, byte.class);
        moveRotCtor = Class.forName(proto + "ClientboundMoveEntityPacket$Rot")
                .getConstructor(int.class, byte.class, byte.class, boolean.class);
        Constructor<?> teleport = null;
        try {
            teleport = Class.forName(proto + "ClientboundTeleportEntityPacket")
                    .getConstructor(entityClass);
        } catch (ReflectiveOperationException ignored) {
        }
        teleportCtor = teleport;
        equipmentCtor = Class.forName(proto + "ClientboundSetEquipmentPacket")
                .getConstructor(int.class, List.class);
        pairOf = Class.forName("com.mojang.datafixers.util.Pair")
                .getMethod("of", Object.class, Object.class);
        nmsEquipmentSlotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot");
        String craftPackage = Bukkit.getServer().getClass().getPackageName();
        asNmsCopy = Class.forName(craftPackage + ".inventory.CraftItemStack")
                .getMethod("asNMSCopy", ItemStack.class);
        playerEntityType = Class.forName("net.minecraft.world.entity.EntityType")
                .getField("PLAYER").get(null);
        vec3Zero = Class.forName("net.minecraft.world.phys.Vec3").getField("ZERO").get(null);
        survivalGameType = enumConstant(Class.forName("net.minecraft.world.level.GameType"), "SURVIVAL");

        interactPacketClass = Class.forName(proto + "ServerboundInteractPacket");
        interactEntityIdField = findFieldByType(interactPacketClass, int.class);
        interactActionField = findFieldByType(interactPacketClass,
                Class.forName(proto + "ServerboundInteractPacket$Action"));
        Class<?> actionInterface = Class.forName(proto + "ServerboundInteractPacket$Action");
        actionGetType = actionInterface.getDeclaredMethod("getType");
        actionGetType.setAccessible(true);
        interactionHandClass = Class.forName("net.minecraft.world.InteractionHand");
    }

    Object createEntity(World world, UUID uuid, String profileName,
                        String skinValue, String skinSignature, Location location) throws Exception {
        Object profile = gameProfileCtor.newInstance(uuid, profileName);
        if (skinValue != null && skinSignature != null) {
            Object property = propertyCtor.newInstance("textures", skinValue, skinSignature);
            propertyMapPut.invoke(getProperties.invoke(profile), "textures", property);
        }
        if (craftWorldGetHandle == null) {
            craftWorldGetHandle = world.getClass().getMethod("getHandle");
        }
        Object level = craftWorldGetHandle.invoke(world);
        Object server = craftServerGetServer.invoke(Bukkit.getServer());
        Object entity = serverPlayerCtor.newInstance(server, level, profile, clientInfoDefault.invoke(null));
        position(entity, location);
        return entity;
    }

    int entityId(Object entity) throws Exception {
        return (int) getId.invoke(entity);
    }

    void position(Object entity, Location location) throws Exception {
        moveTo.invoke(entity, location.getX(), location.getY(), location.getZ(),
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
        List<Object> values = new ArrayList<>();
        values.add(dataValueCtor.newInstance(0, byteSerializer, (byte) (glowing ? 0x40 : 0x00)));
        values.add(dataValueCtor.newInstance(17, byteSerializer, (byte) 0x7E));
        sendPacket(viewer, setDataCtor.newInstance(entityId, values));
    }

    void sendRotation(Player viewer, Object entity, int entityId, float yaw, float pitch) throws Exception {
        sendPacket(viewer, moveRotCtor.newInstance(entityId, rotByte(yaw), rotByte(pitch), true));
        sendPacket(viewer, rotateHeadCtor.newInstance(entity, rotByte(yaw)));
    }

    boolean canTeleport() {
        return teleportCtor != null;
    }

    void sendTeleport(Player viewer, Object entity, Location location) throws Exception {
        sendPacket(viewer, teleportCtor.newInstance(entity));
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
        Object packet = infoUpdateCtor.newInstance(actions, List.of());

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
                default -> defaultValue(types[i]);
            };
        }
        Constructor<?> entryCtor = infoEntryClass.getDeclaredConstructor(types);
        entryCtor.setAccessible(true);
        infoEntriesField.set(packet, List.of(entryCtor.newInstance(args)));
        return packet;
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
                    if (interactPacketClass.isInstance(msg)) {
                        ClickType click = parseClick(msg);
                        int entityId = interactEntityIdField.getInt(msg);
                        if (click != null && handler.onClick(player, entityId, click)) {
                            return;
                        }
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

    private ClickType parseClick(Object packet) throws Exception {
        Object action = interactActionField.get(packet);
        String type = ((Enum<?>) actionGetType.invoke(action)).name();
        if (type.equals("ATTACK")) {
            return ClickType.LEFT;
        }
        if (!type.equals("INTERACT")) {
            return null;
        }
        Field handField = findFieldByTypeOrNull(action.getClass(), interactionHandClass);
        if (handField != null && !((Enum<?>) handField.get(action)).name().equals("MAIN_HAND")) {
            return null;
        }
        return ClickType.RIGHT;
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        for (Object constant : enumClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(name + " not in " + enumClass.getName());
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
