package me.abood8001.mobtag.managers;

import me.abood8001.mobtag.MobTag;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ToggleManager {

    private final MobTag plugin;
    private final File file;
    private FileConfiguration data;

    private final Set<UUID> mobTagsHidden = new HashSet<>();
    private final Set<UUID> playerTagsHidden = new HashSet<>();
    private final Set<UUID> damageIndicatorHidden = new HashSet<>();

    public ToggleManager(MobTag plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "toggles.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(file);
        loadSet("mob-tags-hidden", mobTagsHidden);
        loadSet("player-tags-hidden", playerTagsHidden);
        loadSet("damage-indicator-hidden", damageIndicatorHidden);
    }

    private void loadSet(String key, Set<UUID> set) {
        set.clear();
        if (data.isList(key)) {
            for (String uuid : data.getStringList(key)) {
                try { set.add(UUID.fromString(uuid)); } catch (Exception ignored) {}
            }
        }
    }

    public void save() {
        data.set("mob-tags-hidden", mobTagsHidden.stream().map(UUID::toString).toList());
        data.set("player-tags-hidden", playerTagsHidden.stream().map(UUID::toString).toList());
        data.set("damage-indicator-hidden", damageIndicatorHidden.stream().map(UUID::toString).toList());
        try { data.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    // Mob tags
    public boolean isHidden(UUID uuid) { return mobTagsHidden.contains(uuid); }
    public boolean toggleMobTags(UUID uuid) { return toggle(mobTagsHidden, uuid); }

    // Player tags
    public boolean isPlayerTagsHidden(UUID uuid) { return playerTagsHidden.contains(uuid); }
    public boolean togglePlayerTags(UUID uuid) { return toggle(playerTagsHidden, uuid); }

    // Damage indicator
    public boolean isDamageIndicatorHidden(UUID uuid) { return damageIndicatorHidden.contains(uuid); }
    public boolean toggleDamageIndicator(UUID uuid) { return toggle(damageIndicatorHidden, uuid); }

    private boolean toggle(Set<UUID> set, UUID uuid) {
        if (set.contains(uuid)) {
            set.remove(uuid);
            save();
            return false;
        } else {
            set.add(uuid);
            save();
            return true;
        }
    }
}