package me.abood8001.mobtag.util;

import me.abood8001.mobtag.MobTag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects Bedrock players so their tags can use a different height offset.
 *
 * <p>Floodgate is accessed reflectively - the reflection handles are resolved once and cached,
 * rather than re-resolved on every tag render.</p>
 */
public final class BedrockUtil {

    private BedrockUtil() {}

    private static Boolean floodgatePresent = null;
    private static Object floodgateApi = null;
    private static Method isFloodgatePlayer = null;
    private static boolean floodgateResolved = false;

    public static boolean isBedrock(MobTag plugin, Player player) {
        if (player == null) return false;

        if (plugin.getMobTagConfig().isBedrockUseFloodgate() && isFloodgateAvailable()) {
            Boolean result = queryFloodgate(player.getUniqueId());
            if (result != null && result) return true;
        }

        String prefix = plugin.getMobTagConfig().getBedrockPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return player.getName().startsWith(prefix);
        }
        return false;
    }

    private static Boolean queryFloodgate(UUID uuid) {
        if (!floodgateResolved) {
            floodgateResolved = true;
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = apiClass.getMethod("getInstance").invoke(null);
                isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            } catch (Throwable ignored) {
                floodgateApi = null;
                isFloodgatePlayer = null;
            }
        }
        if (floodgateApi == null || isFloodgatePlayer == null) return null;
        try {
            return (Boolean) isFloodgatePlayer.invoke(floodgateApi, uuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isFloodgateAvailable() {
        if (floodgatePresent == null) {
            floodgatePresent = Bukkit.getPluginManager().getPlugin("floodgate") != null;
        }
        return floodgatePresent;
    }

    /** Clears the cached lookups so /mobtag admin reload re-detects Floodgate. */
    public static void reset() {
        floodgatePresent = null;
        floodgateApi = null;
        isFloodgatePlayer = null;
        floodgateResolved = false;
    }
}
