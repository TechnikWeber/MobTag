package me.abood8001.mobtag.gui;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MobTagGUI implements Listener {

    private static final String TITLE = TextUtil.colorize("&8MobTag Settings");

    private static final int SLOT_MOB_TAGS = 11;
    private static final int SLOT_PLAYER_TAGS = 13;
    private static final int SLOT_DAMAGE = 15;
    private static final int SLOT_CLOSE = 22;

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
        Inventory inv = Bukkit.createInventory(holder, 27, TITLE);
        holder.inventory = inv;

        UUID uid = player.getUniqueId();

        inv.setItem(SLOT_MOB_TAGS, toggleItem(Material.LIME_WOOL, Material.RED_WOOL,
                "&aMob Health Tags", !plugin.getToggleManager().isHidden(uid),
                "&7Health tags above mobs."));

        if (plugin.getMobTagConfig().isPlayerTagsEnabled()) {
            inv.setItem(SLOT_PLAYER_TAGS, toggleItem(Material.LIME_WOOL, Material.RED_WOOL,
                    "&bPlayer Health Tags", !plugin.getToggleManager().isPlayerTagsHidden(uid),
                    "&7Health tags above players."));
        } else {
            inv.setItem(SLOT_PLAYER_TAGS, infoItem("&8Player Health Tags",
                    "&7Disabled by the server.", "&8Set player-tags.enabled in config.yml"));
        }

        inv.setItem(SLOT_DAMAGE, toggleItem(Material.LIME_WOOL, Material.RED_WOOL,
                "&cDamage Indicators", !plugin.getToggleManager().isDamageIndicatorHidden(uid),
                "&7Floating damage numbers."));

        inv.setItem(SLOT_CLOSE, infoItem("&cClose", "&7Click to close this menu."));

        player.openInventory(inv);
    }

    private ItemStack toggleItem(Material on, Material off, String name, boolean enabled, String... extra) {
        ItemStack item = new ItemStack(enabled ? on : off);
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

    private ItemStack infoItem(String name, String... loreLines) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(TextUtil.colorize(name));
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(TextUtil.colorize(line));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

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
        switch (event.getSlot()) {
            case SLOT_MOB_TAGS -> plugin.getToggleManager().toggleMobTags(uid);
            case SLOT_PLAYER_TAGS -> {
                if (!plugin.getMobTagConfig().isPlayerTagsEnabled()) return;
                plugin.getToggleManager().togglePlayerTags(uid);
            }
            case SLOT_DAMAGE -> plugin.getToggleManager().toggleDamageIndicator(uid);
            case SLOT_CLOSE -> {
                player.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        open(player);
    }

    /** Without this a player could drag items into the menu and lose them when it closes. */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
    }
}
