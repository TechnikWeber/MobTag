package me.abood8001.mobtag.hooks;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.util.EntityUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Registers %mobtag_&lt;placeholder&gt;% via PlaceholderAPI.
 *
 * <pre>
 *   %mobtag_nearby_mob_health%      health of the nearest tagged mob
 *   %mobtag_nearby_mob_maxhealth%   max health of the nearest tagged mob
 *   %mobtag_nearby_mob_percent%     health percentage of the nearest tagged mob
 *   %mobtag_nearby_mob_bar%         rendered health bar of the nearest tagged mob
 *   %mobtag_nearby_mob_name%        name of the nearest tagged mob
 *   %mobtag_tags_hidden%            true/false for this player's mob tag toggle
 *   %mobtag_indicators_hidden%      true/false for this player's damage indicator toggle
 * </pre>
 */
public class PAPIHook extends PlaceholderExpansion {

    private final MobTag plugin;

    public PAPIHook(MobTag plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mobtag";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Abood_8001";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginVersion();
    }

    @Override
    public boolean persist() {
        return true; // survives a PlaceholderAPI reload
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        switch (params.toLowerCase(Locale.ROOT)) {
            case "tags_hidden":
                return String.valueOf(plugin.getToggleManager().isHidden(player.getUniqueId()));
            case "indicators_hidden":
                return String.valueOf(plugin.getToggleManager().isDamageIndicatorHidden(player.getUniqueId()));
            default:
                break;
        }

        LivingEntity nearest = getNearestTaggedMob(player);

        switch (params.toLowerCase(Locale.ROOT)) {
            case "nearby_mob_health":
                return nearest == null ? "0" : format(nearest.getHealth());

            case "nearby_mob_maxhealth":
                return nearest == null ? "0" : format(EntityUtil.getMaxHealth(nearest));

            case "nearby_mob_percent": {
                if (nearest == null) return "0";
                double max = EntityUtil.getMaxHealth(nearest);
                if (max <= 0) return "0";
                return String.format(Locale.US, "%.1f", (nearest.getHealth() / max) * 100.0);
            }

            case "nearby_mob_bar": {
                if (nearest == null) return "";
                double max = EntityUtil.getMaxHealth(nearest);
                return plugin.getMobTagConfig().getBar(max > 0 ? nearest.getHealth() / max : 0);
            }

            case "nearby_mob_name":
                if (nearest == null) return "None";
                return nearest.getCustomName() != null ? nearest.getCustomName() : nearest.getType().name();

            default:
                return null;
        }
    }

    private static String format(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) return String.valueOf((long) rounded);
        return String.format(Locale.US, "%.1f", rounded);
    }

    /**
     * Searches outward from the player rather than walking every tracked tag and resolving each
     * one by UUID - the same inversion the main update loop uses.
     */
    private LivingEntity getNearestTaggedMob(Player player) {
        double range = plugin.getMobTagConfig().getDisplayRange();
        LivingEntity nearest = null;
        double closest = range * range;

        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!plugin.getTagManager().getTags().containsKey(entity.getUniqueId())) continue;

            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < closest) {
                closest = distance;
                nearest = living;
            }
        }
        return nearest;
    }
}
