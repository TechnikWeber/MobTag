package me.abood8001.mobtag.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Every MiniMessage/Adventure reference in MobTag's text pipeline lives here.
 *
 * <p>This class is only ever touched after {@link TextUtil#isMiniMessageAvailable()} returns
 * true, so servers without Adventure never trigger class resolution and never see a
 * {@code NoClassDefFoundError}.</p>
 */
final class MiniMessageSupport {

    private MiniMessageSupport() {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // §x§R§R§G§G§B§B is the hex form Spigot's legacy renderer understands.
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    static String toLegacy(String input) {
        try {
            return LEGACY.serialize(MM.deserialize(input));
        } catch (Throwable ignored) {
            return null; // malformed tags fall back to the legacy parser
        }
    }
}
