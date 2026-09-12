package me.abood8001.mobtag.util;

import org.bukkit.ChatColor;

/**
 * Colour handling for every string MobTag renders.
 *
 * <p>Supports legacy {@code &a} codes, {@code &#RRGGBB} hex, and optionally MiniMessage
 * when the server provides it. MiniMessage lives behind {@link MiniMessageSupport} so this
 * class never resolves an Adventure class on servers that do not ship one.</p>
 */
public final class TextUtil {

    private TextUtil() {}

    private static Boolean miniMessageAvailable = null;
    private static volatile boolean miniMessageEnabled = false;

    public static void setMiniMessageEnabled(boolean enabled) {
        miniMessageEnabled = enabled;
    }

    /** True only if MiniMessage is actually on the server classpath. Cached after the first check. */
    public static boolean isMiniMessageAvailable() {
        if (miniMessageAvailable == null) {
            try {
                Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
                Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
                miniMessageAvailable = Boolean.TRUE;
            } catch (Throwable ignored) {
                miniMessageAvailable = Boolean.FALSE;
            }
        }
        return miniMessageAvailable;
    }

    /**
     * Translates a raw config string into a legacy section-sign string ready for display.
     * MiniMessage is only attempted when enabled, available, and the input actually looks
     * like MiniMessage — so legacy formats keep working untouched.
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return "";
        if (miniMessageEnabled && looksLikeMiniMessage(text) && isMiniMessageAvailable()) {
            String rendered = MiniMessageSupport.toLegacy(text);
            if (rendered != null) return rendered;
        }
        return legacy(text);
    }

    private static boolean looksLikeMiniMessage(String s) {
        int open = s.indexOf('<');
        return open >= 0 && s.indexOf('>', open + 1) > open;
    }

    /**
     * Legacy translation: {@code &#RRGGBB} first, then the usual {@code &} codes.
     */
    public static String legacy(String text) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            if (i + 7 < text.length() && text.charAt(i) == '&' && text.charAt(i + 1) == '#'
                    && isHex(text, i + 2)) {
                sb.append('\u00A7').append('x');
                for (int h = i + 2; h < i + 8; h++) {
                    sb.append('\u00A7').append(text.charAt(h));
                }
                i += 8;
                continue;
            }
            sb.append(text.charAt(i));
            i++;
        }
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private static boolean isHex(String s, int from) {
        for (int i = from; i < from + 6; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    /** Strips every colour code, legacy and hex alike, from an already-colorized string. */
    public static String strip(String text) {
        return text == null ? "" : ChatColor.stripColor(text);
    }
}
