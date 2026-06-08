package me.abood8001.mobtag.gui;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.MobTagManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.UUID;

public class MobTagGUI implements Listener {

    private final MobTag plugin;
    private static final String TITLE = MobTagManager.colorize("&8MobTag Settings");

    public MobTagGUI(MobTag plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        UUID uid = player.getUniqueId();

        boolean mobHidden = plugin.getToggleManager().isHidden(uid);
        boolean playerHidden = plugin.getToggleManager().isPlayerTagsHidden(uid);
        boolean dmgHidden = plugin.getToggleManager().isDamageIndicatorHidden(uid);

        inv.setItem(11, toggleItem("&aMob Health Tags", !mobHidden));
       // inv.setItem(13, toggleItem("&bPlayer Health Tags", !playerHidden));
        inv.setItem(15, toggleItem("&cDamage Indicators", !dmgHidden));

        player.openInventory(inv);
    }

    private ItemStack toggleItem(String name, boolean enabled) {
        Material mat = enabled ? Material.LIME_WOOL : Material.RED_WOOL;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MobTagManager.colorize(name));
        meta.setLore(Arrays.asList(
                MobTagManager.colorize(enabled ? "&aEnabled" : "&cDisabled"),
                MobTagManager.colorize("&7Click to toggle")
        ));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        UUID uid = player.getUniqueId();

        switch (slot) {
            case 11:
                plugin.getToggleManager().toggleMobTags(uid);
                break;
            //case 13:
            //    plugin.getToggleManager().togglePlayerTags(uid);
            //    break;
            case 15:
                plugin.getToggleManager().toggleDamageIndicator(uid);
                break;
            default:
                return;
        }
        // Refresh GUI
        open(player);
    }
}