package me.abood8001.mobtag.util;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.ProjectileSource;

public final class EntityUtil {

    private EntityUtil() {}

    /**
     * Max health via the attribute rather than the long-deprecated {@code getMaxHealth()}.
     * Falls back to the vanilla default when an entity somehow has no attribute instance.
     */
    public static double getMaxHealth(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attribute.MAX_HEALTH);
        return instance != null ? instance.getValue() : 20.0D;
    }

    /**
     * Resolves the player ultimately responsible for damage, unwrapping projectiles so that
     * bows, crossbows, tridents, snowballs and splash potions count as player hits.
     * Returns null when no player was involved.
     */
    public static Player resolveDamager(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    /** A mob that is not hostile — animals, ambient and water creatures. */
    public static boolean isPassive(LivingEntity entity) {
        return entity instanceof Mob && !(entity instanceof Enemy);
    }

    /** Bukkit models the Ender Dragon and Wither as {@link Boss}; anything else is opt-in via config. */
    public static boolean isBoss(LivingEntity entity) {
        return entity instanceof Boss;
    }

    /**
     * Honours the de-facto standard vanish convention shared by Essentials, CMI,
     * SuperVanish and PremiumVanish: a {@code vanished} metadata value set to true.
     */
    public static boolean isVanished(Entity entity) {
        if (!entity.hasMetadata("vanished")) return false;
        for (MetadataValue value : entity.getMetadata("vanished")) {
            if (value.asBoolean()) return true;
        }
        return false;
    }
}
