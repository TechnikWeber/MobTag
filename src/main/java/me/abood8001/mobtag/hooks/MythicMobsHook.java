package me.abood8001.mobtag.hooks;

import io.lumine.mythic.bukkit.MythicBukkit;
import me.abood8001.mobtag.MobTag;
import org.bukkit.entity.LivingEntity;

import java.util.logging.Level;

public class MythicMobsHook {

    private final MobTag plugin;

    public MythicMobsHook(MobTag plugin) {
        this.plugin = plugin;
    }

    /** True if the given entity is a MythicMob. */
    public boolean isMythicMob(LivingEntity entity) {
        try {
            return MythicBukkit.inst().getMobManager().isActiveMob(entity.getUniqueId());
        } catch (Throwable ex) {
            debug("MythicMobs lookup failed", ex);
            return false;
        }
    }

    /**
     * The MythicMob's internal name, falling back to the entity type when it cannot be resolved.
     */
    public String getMythicMobName(LivingEntity entity) {
        try {
            var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
            if (activeMob.isPresent()) {
                return activeMob.get().getType().getInternalName();
            }
        } catch (Throwable ex) {
            debug("MythicMobs name lookup failed", ex);
        }
        return entity.getType().name();
    }

    private void debug(String message, Throwable error) {
        if (plugin.getMobTagConfig().isDebug()) {
            plugin.getLogger().log(Level.WARNING, "[debug] " + message, error);
        }
    }
}
