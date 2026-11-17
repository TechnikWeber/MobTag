package me.abood8001.mobtag.managers;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.MobTagConfig;
import me.abood8001.mobtag.scheduler.SchedulerAdapter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * Per-player look preferences (style, size, when to show, target bar). A missing entry or a
     * null field means "use the server default", so changing a default in config.yml still
     * reaches everyone who never touched that setting.
     */
    private final Map<UUID, Prefs> prefs = new HashMap<>();

    /** Mutable holder for one player's preferences. Null fields fall back to the config. */
    public static final class Prefs {
        public String style;
        public String size;
        public String show;
        public Boolean targetBar;

        boolean isEmpty() {
            return style == null && size == null && show == null && targetBar == null;
        }

        Prefs copy() {
            Prefs p = new Prefs();
            p.style = style;
            p.size = size;
            p.show = show;
            p.targetBar = targetBar;
            return p;
        }
    }

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

        prefs.clear();
        ConfigurationSection section = data.getConfigurationSection("prefs");
        if (section != null) {
            for (String raw : section.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(raw);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Skipping malformed UUID in toggles.yml under prefs: " + raw);
                    continue;
                }
                ConfigurationSection entry = section.getConfigurationSection(raw);
                if (entry == null) continue;
                Prefs p = new Prefs();
                p.style = entry.getString("style");
                p.size = entry.getString("size");
                p.show = entry.getString("show");
                p.targetBar = entry.contains("target-bar") ? entry.getBoolean("target-bar") : null;
                if (!p.isEmpty()) prefs.put(uuid, p);
            }
        }
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
            Map<UUID, Prefs> prefsCopy = copyPrefs();
            SchedulerAdapter.runAsync(plugin, () -> write(mobs, players, indicators, prefsCopy));
        }, SAVE_DELAY_TICKS);
    }

    /** Blocking save, used on shutdown where the scheduler is no longer available. */
    public void saveNow() {
        write(toStrings(mobTagsHidden), toStrings(playerTagsHidden), toStrings(damageIndicatorHidden),
                copyPrefs());
    }

    private Map<UUID, Prefs> copyPrefs() {
        Map<UUID, Prefs> out = new HashMap<>(prefs.size());
        for (Map.Entry<UUID, Prefs> e : prefs.entrySet()) out.put(e.getKey(), e.getValue().copy());
        return out;
    }

    private void write(List<String> mobs, List<String> players, List<String> indicators,
                       Map<UUID, Prefs> prefsSnapshot) {
        FileConfiguration data = new YamlConfiguration();
        data.set("mob-tags-hidden", mobs);
        data.set("player-tags-hidden", players);
        data.set("damage-indicator-hidden", indicators);
        for (Map.Entry<UUID, Prefs> e : prefsSnapshot.entrySet()) {
            String base = "prefs." + e.getKey() + ".";
            Prefs p = e.getValue();
            if (p.style != null) data.set(base + "style", p.style);
            if (p.size != null) data.set(base + "size", p.size);
            if (p.show != null) data.set(base + "show", p.show);
            if (p.targetBar != null) data.set(base + "target-bar", p.targetBar);
        }
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

    // Look preferences. Getters return null when the player kept the server default.
    public String getStyle(UUID uuid) { Prefs p = prefs.get(uuid); return p == null ? null : p.style; }
    public String getSize(UUID uuid) { Prefs p = prefs.get(uuid); return p == null ? null : p.size; }
    public String getShow(UUID uuid) { Prefs p = prefs.get(uuid); return p == null ? null : p.show; }
    public Boolean getTargetBar(UUID uuid) { Prefs p = prefs.get(uuid); return p == null ? null : p.targetBar; }

    public void setStyle(UUID uuid, String value) { edit(uuid).style = value; tidy(uuid); }
    public void setSize(UUID uuid, String value) { edit(uuid).size = value; tidy(uuid); }
    public void setShow(UUID uuid, String value) { edit(uuid).show = value; tidy(uuid); }
    public void setTargetBar(UUID uuid, Boolean value) { edit(uuid).targetBar = value; tidy(uuid); }

    // Effective values: the player's choice if it still exists in the config, else the default.

    public MobTagConfig.Style effectiveStyle(UUID uuid) {
        MobTagConfig cfg = plugin.getMobTagConfig();
        String id = getStyle(uuid);
        MobTagConfig.Style style = id == null ? null : cfg.getStyles().get(id);
        return style != null ? style : cfg.getStyles().get(cfg.getDefaultStyle());
    }

    public MobTagConfig.Size effectiveSize(UUID uuid) {
        MobTagConfig cfg = plugin.getMobTagConfig();
        String id = getSize(uuid);
        MobTagConfig.Size size = id == null ? null : cfg.getSizes().get(id);
        return size != null ? size : cfg.getSizes().get(cfg.getDefaultSize());
    }

    /** ALL, LOOKING_AT or DAMAGER. */
    public String effectiveShow(UUID uuid) {
        String show = getShow(uuid);
        if (show != null && SHOW_MODES.contains(show)) return show;
        return plugin.getMobTagConfig().getVisibilityMode();
    }

    public boolean isTargetBarOn(UUID uuid) {
        MobTagConfig cfg = plugin.getMobTagConfig();
        if (!cfg.isTargetBarEnabled()) return false;
        Boolean on = getTargetBar(uuid);
        return on != null ? on : cfg.isTargetBarDefaultOn();
    }

    public static final List<String> SHOW_MODES = List.of("ALL", "LOOKING_AT", "DAMAGER");

    /** Drops every look preference of a player, so they follow the server defaults again. */
    public void resetLook(UUID uuid) {
        if (prefs.remove(uuid) != null) scheduleSave();
    }

    private Prefs edit(UUID uuid) {
        return prefs.computeIfAbsent(uuid, k -> new Prefs());
    }

    private void tidy(UUID uuid) {
        Prefs p = prefs.get(uuid);
        if (p != null && p.isEmpty()) prefs.remove(uuid);
        scheduleSave();
    }

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
