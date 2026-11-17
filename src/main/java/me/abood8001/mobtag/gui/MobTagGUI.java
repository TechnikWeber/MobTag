package me.abood8001.mobtag.gui;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.MobTagConfig;
import me.abood8001.mobtag.managers.ToggleManager;
import me.abood8001.mobtag.util.BedrockUtil;
import me.abood8001.mobtag.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MobTagGUI implements Listener {

    private static final String TITLE = TextUtil.colorize("&8MobTag Settings");

    // Row 2: on/off switches
    private static final int SLOT_MOB_TAGS = 10;
    private static final int SLOT_PLAYER_TAGS = 12;
    private static final int SLOT_DAMAGE = 14;
    private static final int SLOT_TARGET_BAR = 16;
    // Row 4: how the tags look
    private static final int SLOT_STYLE = 28;
    private static final int SLOT_SIZE = 30;
    private static final int SLOT_SHOW = 32;
    private static final int SLOT_RESET = 34;
    private static final int SLOT_CLOSE = 40;

    private final MobTag plugin;

    public MobTagGUI(MobTag plugin) {
        this.plugin = plugin;
    }

    /**
     * Identifies MobTag's own inventory. Matching on the holder instead of the title means a
     * translated or recoloured title can never make the GUI stop responding - or, worse, make an
     * unrelated inventory with the same name start responding.
     */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 45, TITLE);
        holder.inventory = inv;

        UUID uid = player.getUniqueId();
        ToggleManager toggles = plugin.getToggleManager();
        MobTagConfig cfg = plugin.getMobTagConfig();
        boolean packets = packetMode();
        boolean bedrock = BedrockUtil.isBedrock(plugin, player);

        inv.setItem(SLOT_MOB_TAGS, toggleItem("&aMob Health Tags", !toggles.isHidden(uid),
                "&7Health tags above mobs."));

        if (cfg.isPlayerTagsEnabled()) {
            inv.setItem(SLOT_PLAYER_TAGS, toggleItem("&bPlayer Health Tags", !toggles.isPlayerTagsHidden(uid),
                    "&7Health tags above players."));
        } else {
            inv.setItem(SLOT_PLAYER_TAGS, infoItem(Material.BARRIER, "&8Player Health Tags",
                    "&7Disabled by the server."));
        }

        if (packets) {
            inv.setItem(SLOT_DAMAGE, toggleItem("&cDamage Indicators", !toggles.isDamageIndicatorHidden(uid),
                    "&7Floating damage numbers."));
        } else {
            inv.setItem(SLOT_DAMAGE, infoItem(Material.BARRIER, "&8Damage Indicators",
                    "&7Not available on this server", "&7(needs PacketEvents)."));
        }

        if (cfg.isTargetBarEnabled()) {
            inv.setItem(SLOT_TARGET_BAR, toggleItem("&dTarget Bar", toggles.isTargetBarOn(uid),
                    "&7Bar at the top of the screen with", "&7the mob you look at or fight."));
        } else {
            inv.setItem(SLOT_TARGET_BAR, infoItem(Material.BARRIER, "&8Target Bar", "&7Disabled by the server."));
        }

        inv.setItem(SLOT_STYLE, styleItem(uid, cfg, packets));
        inv.setItem(SLOT_SIZE, sizeItem(uid, cfg, packets, bedrock));
        inv.setItem(SLOT_SHOW, showItem(uid));
        inv.setItem(SLOT_RESET, infoItem(Material.WATER_BUCKET, "&fReset Look",
                "&7Design, size, when to show and", "&7target bar back to server defaults.", "",
                "&eClick to reset"));
        inv.setItem(SLOT_CLOSE, infoItem(Material.BARRIER, "&cClose", "&7Click to close this menu."));

        player.openInventory(inv);
    }

    // ── Items ────────────────────────────────────────────────────────────────

    private ItemStack styleItem(UUID uid, MobTagConfig cfg, boolean packets) {
        MobTagConfig.Style current = plugin.getToggleManager().effectiveStyle(uid);
        List<String> lore = new ArrayList<>();
        lore.add("&7How the tag above a mob looks.");
        lore.add("");
        for (MobTagConfig.Style style : cfg.getStyles().values()) {
            boolean selected = style.id().equals(current.id());
            lore.add((selected ? "&a▶ " : "&8  ") + style.name() + "&7: &r" + preview(style, cfg));
        }
        lore.add("");
        if (!packets) {
            lore.add("&cEveryone sees the server default here");
            lore.add("&c(per-player designs need PacketEvents).");
        }
        lore.add("&eClick: next  &7|  &eRight-click: previous");
        return infoItem(Material.NAME_TAG, "&eDesign: &f" + current.name(), lore.toArray(new String[0]));
    }

    private ItemStack sizeItem(UUID uid, MobTagConfig cfg, boolean packets, boolean bedrock) {
        MobTagConfig.Size current = plugin.getToggleManager().effectiveSize(uid);
        List<String> lore = new ArrayList<>();
        lore.add("&7Size of the tags above mobs.");
        lore.add("");
        for (MobTagConfig.Size size : cfg.getSizes().values()) {
            boolean selected = size.id().equals(current.id());
            lore.add((selected ? "&a▶ " : "&8  ") + size.name());
        }
        lore.add("");
        if (!packets) {
            lore.add("&cNot available on this server");
            lore.add("&c(needs PacketEvents).");
        } else if (bedrock) {
            lore.add("&cJava only - Bedrock always shows");
            lore.add("&cthe normal size.");
        }
        lore.add("&eClick: next  &7|  &eRight-click: previous");
        return infoItem(Material.SPYGLASS, "&bSize: &f" + current.name(), lore.toArray(new String[0]));
    }

    private ItemStack showItem(UUID uid) {
        String current = plugin.getToggleManager().effectiveShow(uid);
        List<String> lore = new ArrayList<>();
        lore.add("&7When a mob's tag is shown to you.");
        lore.add("");
        for (String mode : ToggleManager.SHOW_MODES) {
            boolean selected = mode.equals(current);
            lore.add((selected ? "&a▶ " : "&8  ") + "&f" + showName(mode) + " &7- " + showHint(mode));
        }
        lore.add("");
        lore.add("&eClick: next  &7|  &eRight-click: previous");
        return infoItem(Material.ENDER_EYE, "&6When: &f" + showName(current), lore.toArray(new String[0]));
    }

    public static String showName(String mode) {
        return switch (mode) {
            case "LOOKING_AT" -> "Looking at it";
            case "DAMAGER" -> "In combat";
            default -> "Always";
        };
    }

    private static String showHint(String mode) {
        return switch (mode) {
            case "LOOKING_AT" -> "only the mob in your crosshair";
            case "DAMAGER" -> "only mobs you recently hit";
            default -> "every mob nearby";
        };
    }

    /** The design rendered for a sample mob at 70% health, so players see what they pick. */
    private static String preview(MobTagConfig.Style style, MobTagConfig cfg) {
        String format = style.format() == null || style.format().isEmpty() ? cfg.getTagFormat() : style.format();
        return TextUtil.colorize(format
                .replace("{name}", "Zombie")
                .replace("{type}", "ZOMBIE")
                .replace("{current}", "14")
                .replace("{max}", "20")
                .replace("{bar}", cfg.getBar(0.7))
                .replace("{dot}", cfg.getDot(0.7))
                .replace("{percent}", "70.0"));
    }

    private ItemStack toggleItem(String name, boolean enabled, String... extra) {
        ItemStack item = new ItemStack(enabled ? Material.LIME_WOOL : Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(TextUtil.colorize(name));
        List<String> lore = new ArrayList<>();
        for (String line : extra) lore.add(TextUtil.colorize(line));
        lore.add(TextUtil.colorize(enabled ? "&aEnabled" : "&cDisabled"));
        lore.add(TextUtil.colorize("&7Click to toggle"));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack infoItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(TextUtil.colorize(name));
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(TextUtil.colorize(line));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    // ── Clicks ───────────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof Holder)) {
            return; // click landed in the player's own inventory
        }

        UUID uid = player.getUniqueId();
        ToggleManager toggles = plugin.getToggleManager();
        MobTagConfig cfg = plugin.getMobTagConfig();
        int step = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT ? -1 : 1;

        switch (event.getSlot()) {
            case SLOT_MOB_TAGS -> toggles.toggleMobTags(uid);
            case SLOT_PLAYER_TAGS -> {
                if (!cfg.isPlayerTagsEnabled()) return;
                toggles.togglePlayerTags(uid);
            }
            case SLOT_DAMAGE -> {
                if (!packetMode()) return;
                toggles.toggleDamageIndicator(uid);
            }
            case SLOT_TARGET_BAR -> {
                if (!cfg.isTargetBarEnabled()) return;
                toggles.setTargetBar(uid, !toggles.isTargetBarOn(uid));
            }
            case SLOT_STYLE -> toggles.setStyle(uid,
                    cycle(new ArrayList<>(cfg.getStyles().keySet()), toggles.effectiveStyle(uid).id(), step));
            case SLOT_SIZE -> {
                if (!packetMode()) return;
                toggles.setSize(uid,
                        cycle(new ArrayList<>(cfg.getSizes().keySet()), toggles.effectiveSize(uid).id(), step));
            }
            case SLOT_SHOW -> toggles.setShow(uid, cycle(ToggleManager.SHOW_MODES, toggles.effectiveShow(uid), step));
            case SLOT_RESET -> toggles.resetLook(uid);
            case SLOT_CLOSE -> {
                player.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        plugin.refreshViewer(player);
        open(player);
    }

    private static String cycle(List<String> options, String current, int step) {
        if (options.isEmpty()) return current;
        int index = options.indexOf(current.toUpperCase(Locale.ROOT));
        if (index < 0) index = options.indexOf(current);
        if (index < 0) index = 0;
        int next = Math.floorMod(index + step, options.size());
        return options.get(next);
    }

    private boolean packetMode() {
        return plugin.isPacketEventsEnabled() && !"ARMORSTAND".equals(plugin.getMobTagConfig().getDisplayMode());
    }

    /** Without this a player could drag items into the menu and lose them when it closes. */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
    }
}
