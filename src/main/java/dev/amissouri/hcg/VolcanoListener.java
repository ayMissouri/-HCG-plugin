package dev.amissouri.hcg;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/** Volcano debris splashes into lava particles on landing instead of placing blocks. */
public final class VolcanoListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onDebrisLand(EntityChangeBlockEvent event) {
        if (!event.getEntity().hasMetadata(VolcanoManager.DEBRIS_METADATA)) {
            return;
        }
        event.setCancelled(true);
        Location loc = event.getEntity().getLocation();
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 8, 0.4, 0.3, 0.4, 0.0, null, true);
        loc.getWorld().playSound(loc, Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.0f);
        event.getEntity().remove();
    }
}
