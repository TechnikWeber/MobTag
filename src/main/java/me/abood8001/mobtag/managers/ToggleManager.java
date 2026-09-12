package me.abood8001.mobtag.managers;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.scheduler.SchedulerAdapter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Per-player tag preferences, persisted to toggles.yml.
 *
 * <p>Writes are debounced and pushed off the main thread, so a player spamming the settings GUI
 * no longer performs one synchronous disk write per click.</p>
 */
public class ToggleManager {

    private static final long SAVE_DELAY_TICKS = 40L;

    private final MobTag plugin;
    private final File file;

    private final Set<UUID> mobTagsHidden = new HashSet<>();
    private final Set<UUID> playerTagsHidden = new HashSet<>();
    private final Set<UUID> damageIndicatorHidden = new HashSet<>();

    private boolean saveQueued = false;

    public ToggleManager(MobTag plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "toggles.yml");
        load();
    }

    public void load() {
        // YamlConfiguration handles a missing file, so there is no need to create an empty one.
        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        loadSet(data, "mob-tags-hidden", mobTagsHidden);
        loadSet(data, "player-tags-hidden", playerTagsHidden);
        loadSet(data, "damage-indicator-hidden", damageIndicatorHidden);
    }

    private void loadSet(FileConfiguration data, String key, Set<UUID> set) {
        set.clear();
        for (String raw : data.getStringList(key)) {
            try {
                set.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed UUID in toggles.yml under " + key + ": " + raw);
            }
        }
    }

    /** Queues a debounced background save. Repeated toggles collapse into one write. */
    private void scheduleSave() {
        if (saveQueued) return;
        saveQueued = true;
        SchedulerAdapter.runTaskLater(plugin, () -> {
            saveQueued = false;
            // Snapshot on the main thread, then hand the plain strings to the IO thread.
            List<String> mobs = toStrings(mobTagsHidden);
            List<String> players = toStrings(playerTagsHidden);
            List<String> indicators = toStrings(damageIndicatorHidden);
            SchedulerAdapter.runAsync(plugin, () -> write(mobs, players, indicators));
        }, SAVE_DELAY_TICKS);
    }

    /** Blocking save, used on shutdown where the scheduler is no longer available. */
    public void saveNow() {
        write(toStrings(mobTagsHidden), toStrings(playerTagsHidden), toStrings(damageIndicatorHidden));
    }

    private void write(List<String> mobs, List<String> players, List<String> indicators) {
        FileConfiguration data = new YamlConfiguration();
        data.set("mob-tags-hidden", mobs);
        data.set("player-tags-hidden", players);
        data.set("damage-indicator-hidden", indicators);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the MobTag data folder; toggles were not saved.");
                return;
            }
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save toggles.yml", ex);
        }
    }

    private static List<String> toStrings(Set<UUID> set) {
        List<String> out = new ArrayList<>(set.size());
        for (UUID uuid : set) out.add(uuid.toString());
        return out;
    }

    // Mob tags
    public boolean isHidden(UUID uuid) { return mobTagsHidden.contains(uuid); }
    public boolean toggleMobTags(UUID uuid) { return toggle(mobTagsHidden, uuid); }

    // Player tags
    public boolean isPlayerTagsHidden(UUID uuid) { return playerTagsHidden.contains(uuid); }
    public boolean togglePlayerTags(UUID uuid) { return toggle(playerTagsHidden, uuid); }

    // Damage indicators
    public boolean isDamageIndicatorHidden(UUID uuid) { return damageIndicatorHidden.contains(uuid); }
    public boolean toggleDamageIndicator(UUID uuid) { return toggle(damageIndicatorHidden, uuid); }

    /** @return true if the feature is now hidden. */
    private boolean toggle(Set<UUID> set, UUID uuid) {
        boolean nowHidden;
        if (set.contains(uuid)) {
            set.remove(uuid);
            nowHidden = false;
        } else {
            set.add(uuid);
            nowHidden = true;
        }
        scheduleSave();
        return nowHidden;
    }
}
