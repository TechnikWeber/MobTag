package me.abood8001.mobtag.util;

import me.abood8001.mobtag.MobTag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BedrockUtil {

    private static Boolean floodgatePresent = null;

    public static boolean isBedrock(MobTag plugin, Player player) {
        // Method 1: Floodgate API (if installed and enabled in config)
        if (plugin.getMobTagConfig().isBedrockUseFloodgate() && isFloodgateAvailable()) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                UUID uuid = player.getUniqueId();
                boolean isFloodgate = (boolean) apiClass.getMethod("isFloodgatePlayer", UUID.class)
                        .invoke(api, uuid);
                if (isFloodgate) return true;
            } catch (Exception ignored) {}
        }

        // Method 2: prefix fallback
        String prefix = plugin.getMobTagConfig().getBedrockPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return player.getName().startsWith(prefix);
        }
        return false;
    }

    private static boolean isFloodgateAvailable() {
        if (floodgatePresent == null) {
            floodgatePresent = Bukkit.getPluginManager().getPlugin("floodgate") != null;
        }
        return floodgatePresent;
    }
}