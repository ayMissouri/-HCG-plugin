package dev.amissouri.hcg;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class HcgPlatform {

    private static final boolean FOLIA = detectFolia();

    private HcgPlatform() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static String describe() {
        return Bukkit.getName() + " " + Bukkit.getMinecraftVersion() + (FOLIA ? " (regionised)" : "");
    }

    public static void assertOwns(Location location) {
        if (FOLIA && location != null && !Bukkit.isOwnedByCurrentRegion(location)) {
            throw new IllegalStateException("Thread " + Thread.currentThread().getName()
                    + " does not own region at " + location.getWorld().getName()
                    + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        }
    }

    public static void assertOwns(Entity entity) {
        if (FOLIA && entity != null && !Bukkit.isOwnedByCurrentRegion(entity)) {
            throw new IllegalStateException("Thread " + Thread.currentThread().getName()
                    + " does not own region of entity " + entity.getUniqueId());
        }
    }
}
