package me.abood8001.mobtag;

import me.abood8001.mobtag.util.TextUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MobTagConfig {

    /** Bumped whenever new keys are added, so existing configs get merged rather than reset. */
    public static final int CONFIG_VERSION = 3;

    private final MobTag plugin;

    // General
    private boolean debug;
    private int updateInterval;
    private double displayRange;
    private double lookingLen;
    private boolean lookingRequiresLineOfSight;
    private String visibilityMode;
    private int damagerTimeoutSeconds;
    private boolean ignorePassive;
    private boolean ignorePlayers;
    private boolean showOnBosses;
    private boolean hideAtFullHealth;
    private int hideAfterSecondsOutOfCombat;
    private boolean hideOnInvisible;
    private boolean hideOnVanished;
    private String tagFormat;
    private boolean useMiniMessage;

    // Health bar
    private int barLength;
    private String filledChar;
    private String emptyChar;
    private String filledColor;
    private String gradientHigh;
    private String gradientMedium;
    private String gradientLow;
    private String emptyColor;

    /** Pre-rendered bars: [gradient tier][filled count]. Built once per reload, never per tick. */
    private String[][] barCache;

    // MythicMobs
    private boolean mythicMobsEnabled;
    private boolean showMythicName;
    private String mythicCustomFormat;

    // PlaceholderAPI
    private boolean papiEnabled;

    // Messages
    private String prefix;
    private String msgReloadSuccess;
    private String msgNoPermission;
    private String msgUnknownCommand;
    private String msgToggleOn;
    private String msgToggleOff;
    private String msgPlayersOnly;

    private List<String> blacklistedWorlds;
    private Map<String, String> entityFormats;
    private Set<String> ignoreMetadata;
    private Set<String> extraBossTypes;
    private String displayMode;

    // Tag appearance
    private double tagScale;
    private double tagHeightJava;
    private double tagHeightBedrock;
    private Map<String, Double> perTypeHeight;
    private boolean armorStandRideMob;

    // Damage animation
    private boolean damageAnimationEnabled;
    private boolean damageFlash;
    private boolean damageScaleBounce;
    private int damageAnimationDuration;

    // Damage indicator
    private boolean damageIndicatorEnabled;
    private String damageIndicatorFormat;
    private String damageIndicatorCritFormat;
    private int damageIndicatorDuration;
    private double damageIndicatorHeight;
    private String damageIndicatorMotion;
    private double damageIndicatorMotionDistance;
    private boolean damageIndicatorFadeOut;
    private boolean damageIndicatorShowOnPlayers;
    private int damageIndicatorMergeTicks;
    private int damageIndicatorMaxPerPlayer;

    // Player tags
    private boolean playerTagsEnabled;
    private String playerTagFormat;
    private double playerTagRange;
    private double playerTagHeightJava;
    private double playerTagHeightBedrock;

    // Bedrock
    private boolean bedrockUseFloodgate;
    private String bedrockPrefix;

    // Misc
    private boolean updateChecker;

    // Player-selectable look (fork additions)
    /** A tag design players can pick in the menu. An empty format means "tag-format as before". */
    public record Style(String id, String name, String format) {}
    /** A tag size players can pick in the menu. */
    public record Size(String id, String name, double scale) {}

    private Map<String, Style> styles;
    private String defaultStyle;
    private Map<String, Size> sizes;
    private String defaultSize;
    private String dotChar;
    private String[] dotCache;

    private boolean targetBarEnabled;
    private boolean targetBarDefaultOn;
    private double targetBarRange;
    private int targetBarCombatSeconds;
    private String targetBarFormat;
    private String targetBarStyle;

    public MobTagConfig(MobTag plugin) {
        this.plugin = plugin;
        migrate();
        load();
    }

    /**
     * Merges in any keys added since the user's config was written, keeping their values.
     */
    private void migrate() {
        FileConfiguration cfg = plugin.getConfig();
        int version = cfg.getInt("config-version", 0);
        if (version >= CONFIG_VERSION) return;

        cfg.options().copyDefaults(true);
        cfg.set("config-version", CONFIG_VERSION);
        plugin.saveConfig();
        plugin.getLogger().info("Config updated from version " + version + " to " + CONFIG_VERSION
                + " - your existing settings were kept.");
    }

    public void load() {
        FileConfiguration cfg = plugin.getConfig();

        debug = cfg.getBoolean("debug", false);
        useMiniMessage = cfg.getBoolean("use-minimessage", true);
        TextUtil.setMiniMessageEnabled(useMiniMessage);

        entityFormats = new HashMap<>();
        ConfigurationSection formats = cfg.getConfigurationSection("entity-formats");
        if (formats != null) {
            for (String key : formats.getKeys(false)) {
                entityFormats.put(key.toUpperCase(Locale.ROOT), formats.getString(key));
            }
        }

        blacklistedWorlds = cfg.getStringList("blacklisted-worlds");

        ignoreMetadata = new HashSet<>(cfg.getStringList("ignore-metadata"));
        if (ignoreMetadata.isEmpty()) {
            Collections.addAll(ignoreMetadata, "NPC", "shopkeeper", "citizens-npc");
        }

        extraBossTypes = new HashSet<>();
        for (String type : cfg.getStringList("extra-boss-types")) {
            extraBossTypes.add(type.toUpperCase(Locale.ROOT));
        }

        // Clamped so a typo can never produce a zero or negative scheduler period.
        updateInterval = Math.max(1, cfg.getInt("update-interval", 4));
        displayRange = Math.max(1.0D, cfg.getDouble("display-range", 16.0));
        lookingLen = Math.max(1.0D, cfg.getDouble("looking-at-range", cfg.getDouble("looking-len", 5.0)));
        lookingRequiresLineOfSight = cfg.getBoolean("looking-at-requires-line-of-sight", true);
        visibilityMode = cfg.getString("visibility-mode", "ALL").toUpperCase(Locale.ROOT);
        damagerTimeoutSeconds = cfg.getInt("damager-timeout-seconds", 30);
        ignorePassive = cfg.getBoolean("ignore-passive", true);
        ignorePlayers = cfg.getBoolean("ignore-players", true);
        showOnBosses = cfg.getBoolean("show-on-bosses", true);
        hideAtFullHealth = cfg.getBoolean("hide-at-full-health", false);
        hideAfterSecondsOutOfCombat = cfg.getInt("hide-after-seconds-out-of-combat", 0);
        hideOnInvisible = cfg.getBoolean("hide-on-invisible", true);
        hideOnVanished = cfg.getBoolean("hide-on-vanished", true);
        tagFormat = cfg.getString("tag-format", "&c❤ &f{current}&7/&f{max}");

        barLength = Math.max(1, cfg.getInt("health-bar.length", 10));
        filledChar = cfg.getString("health-bar.filled-char", "█");
        emptyChar = cfg.getString("health-bar.empty-char", "░");
        filledColor = cfg.getString("health-bar.filled-color", "gradient");
        gradientHigh = cfg.getString("health-bar.gradient-high", "&a");
        gradientMedium = cfg.getString("health-bar.gradient-medium", "&e");
        gradientLow = cfg.getString("health-bar.gradient-low", "&c");
        emptyColor = cfg.getString("health-bar.empty-color", "&8");
        buildBarCache();

        mythicMobsEnabled = cfg.getBoolean("mythicmobs.enabled", false);
        showMythicName = cfg.getBoolean("mythicmobs.show-mythic-name", true);
        mythicCustomFormat = cfg.getString("mythicmobs.custom-format", "");

        papiEnabled = cfg.getBoolean("placeholderapi.enabled", true);

        prefix = cfg.getString("messages.prefix", "&8[&bMobTag&8] ");
        msgReloadSuccess = cfg.getString("messages.reload-success", "&aConfiguration reloaded successfully!");
        msgNoPermission = cfg.getString("messages.no-permission", "&cYou don't have permission to do that.");
        msgUnknownCommand = cfg.getString("messages.unknown-command", "&cUnknown sub-command. Usage: /mobtag admin reload");
        msgToggleOn = cfg.getString("messages.toggle-on", "&a{feature} enabled.");
        msgToggleOff = cfg.getString("messages.toggle-off", "&c{feature} disabled.");
        msgPlayersOnly = cfg.getString("messages.players-only", "&cOnly players can use that.");

        displayMode = cfg.getString("display-mode", "AUTO").toUpperCase(Locale.ROOT);

        tagScale = cfg.getDouble("tag-scale", 1.0);
        tagHeightJava = cfg.getDouble("tag-height-offset.java", 0.3);
        tagHeightBedrock = cfg.getDouble("tag-height-offset.bedrock", 0.0);
        perTypeHeight = new HashMap<>();
        ConfigurationSection perType = cfg.getConfigurationSection("tag-height-offset.per-type");
        if (perType != null) {
            for (String key : perType.getKeys(false)) {
                perTypeHeight.put(key.toUpperCase(Locale.ROOT), perType.getDouble(key));
            }
        }
        // Off by default: riding removes the stutter but makes height-offset inapplicable,
        // so existing setups keep their current look until they opt in.
        armorStandRideMob = cfg.getBoolean("armorstand.ride-mob", false);

        damageAnimationEnabled = cfg.getBoolean("damage-animation.enabled", false);
        damageFlash = cfg.getBoolean("damage-animation.flash", false);
        damageScaleBounce = cfg.getBoolean("damage-animation.scale-bounce", false);
        damageAnimationDuration = Math.max(1, cfg.getInt("damage-animation.duration", 8));

        damageIndicatorEnabled = cfg.getBoolean("damage-indicator.enabled", true);
        damageIndicatorFormat = cfg.getString("damage-indicator.format", "&c-{damage}");
        damageIndicatorCritFormat = cfg.getString("damage-indicator.crit-format", "&d-{damage} ✸");
        damageIndicatorDuration = Math.max(1, cfg.getInt("damage-indicator.duration", 20));
        damageIndicatorHeight = cfg.getDouble("damage-indicator.height-offset", -0.5);
        damageIndicatorMotion = cfg.getString("damage-indicator.motion", "UP").toUpperCase(Locale.ROOT);
        damageIndicatorMotionDistance = cfg.getDouble("damage-indicator.motion-distance", 1.0);
        damageIndicatorFadeOut = cfg.getBoolean("damage-indicator.fade-out", true);
        damageIndicatorShowOnPlayers = cfg.getBoolean("damage-indicator.show-on-players", true);
        damageIndicatorMergeTicks = Math.max(0, cfg.getInt("damage-indicator.merge-window-ticks", 10));
        damageIndicatorMaxPerPlayer = Math.max(1, cfg.getInt("damage-indicator.max-per-player", 24));

        playerTagsEnabled = cfg.getBoolean("player-tags.enabled", false);
        playerTagFormat = cfg.getString("player-tags.format", "&c❤ &f{current}&7/&f{max}");
        playerTagRange = Math.max(1.0D, cfg.getDouble("player-tags.display-range", 16.0));
        playerTagHeightJava = cfg.getDouble("player-tags.height-offset.java", 0.3);
        playerTagHeightBedrock = cfg.getDouble("player-tags.height-offset.bedrock", 0.0);

        bedrockUseFloodgate = cfg.getBoolean("bedrock.use-floodgate", true);
        bedrockPrefix = cfg.getString("bedrock.prefix", ".");

        updateChecker = cfg.getBoolean("update-checker", true);

        loadLookOptions(cfg);
    }

    private void loadLookOptions(FileConfiguration cfg) {
        styles = new LinkedHashMap<>();
        ConfigurationSection styleSection = cfg.getConfigurationSection("styles");
        if (styleSection != null) {
            for (String id : styleSection.getKeys(false)) {
                ConfigurationSection st = styleSection.getConfigurationSection(id);
                if (st == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                styles.put(key, new Style(key, st.getString("name", id), st.getString("format", "")));
            }
        }
        if (styles.isEmpty()) {
            styles.put("classic", new Style("classic", "&fClassic", ""));
        }
        defaultStyle = cfg.getString("default-style", "bar").toLowerCase(Locale.ROOT);
        if (!styles.containsKey(defaultStyle)) defaultStyle = styles.keySet().iterator().next();

        sizes = new LinkedHashMap<>();
        ConfigurationSection sizeSection = cfg.getConfigurationSection("sizes");
        if (sizeSection != null) {
            for (String id : sizeSection.getKeys(false)) {
                ConfigurationSection sz = sizeSection.getConfigurationSection(id);
                if (sz == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                double scale = Math.max(0.1, Math.min(5.0, sz.getDouble("scale", 1.0)));
                sizes.put(key, new Size(key, sz.getString("name", id), scale));
            }
        }
        if (sizes.isEmpty()) {
            sizes.put("medium", new Size("medium", "&fMedium", 1.0));
        }
        defaultSize = cfg.getString("default-size", "medium").toLowerCase(Locale.ROOT);
        if (!sizes.containsKey(defaultSize)) defaultSize = sizes.keySet().iterator().next();

        dotChar = cfg.getString("health-dot.char", "●");
        String[] tiers = {gradientHigh, gradientMedium, gradientLow};
        dotCache = new String[tiers.length];
        for (int i = 0; i < tiers.length; i++) dotCache[i] = TextUtil.colorize(tiers[i]) + dotChar;

        targetBarEnabled = cfg.getBoolean("target-bar.enabled", true);
        targetBarDefaultOn = cfg.getBoolean("target-bar.default-on", false);
        targetBarRange = Math.max(1.0, cfg.getDouble("target-bar.range", 24.0));
        targetBarCombatSeconds = Math.max(0, cfg.getInt("target-bar.combat-seconds", 5));
        targetBarFormat = cfg.getString("target-bar.format", "&f{name} &c❤ &f{current}&7/&f{max}");
        targetBarStyle = cfg.getString("target-bar.style", "SEGMENTED_10").toUpperCase(Locale.ROOT);
    }

    /**
     * Renders every possible bar up front. At runtime a bar becomes a two-index array lookup
     * instead of two colorize calls plus two repeat() allocations per tag, per viewer, per tick.
     */
    private void buildBarCache() {
        String[] tiers = {gradientHigh, gradientMedium, gradientLow};
        barCache = new String[tiers.length][barLength + 1];

        boolean gradient = "gradient".equalsIgnoreCase(filledColor);
        String empty = TextUtil.colorize(emptyColor);

        for (int tier = 0; tier < tiers.length; tier++) {
            String color = TextUtil.colorize(gradient ? tiers[tier] : filledColor);
            for (int filled = 0; filled <= barLength; filled++) {
                barCache[tier][filled] = color
                        + filledChar.repeat(filled)
                        + empty
                        + emptyChar.repeat(barLength - filled);
            }
        }
    }

    /** Health bar for the given 0..1 ratio, straight from the cache. */
    public String getBar(double ratio) {
        if (Double.isNaN(ratio) || ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        int filled = (int) Math.round(ratio * barLength);
        double pct = ratio * 100;
        int tier = pct > 60 ? 0 : (pct > 30 ? 1 : 2);
        return barCache[tier][Math.max(0, Math.min(barLength, filled))];
    }

    /** 0 = high, 1 = medium, 2 = low health - the same thresholds the bar gradient uses. */
    public static int tier(double ratio) {
        double pct = ratio * 100;
        return pct > 60 ? 0 : (pct > 30 ? 1 : 2);
    }

    /** A single coloured dot: green, yellow or red depending on health. */
    public String getDot(double ratio) {
        if (Double.isNaN(ratio) || ratio < 0) ratio = 0;
        return dotCache[tier(Math.min(1.0, ratio))];
    }

    public Map<String, Style> getStyles() { return styles; }
    public String getDefaultStyle() { return defaultStyle; }
    public Map<String, Size> getSizes() { return sizes; }
    public String getDefaultSize() { return defaultSize; }
    public boolean isTargetBarEnabled() { return targetBarEnabled; }
    public boolean isTargetBarDefaultOn() { return targetBarDefaultOn; }
    public double getTargetBarRange() { return targetBarRange; }
    public int getTargetBarCombatSeconds() { return targetBarCombatSeconds; }
    public String getTargetBarFormat() { return targetBarFormat; }
    public String getTargetBarStyle() { return targetBarStyle; }

    // Getters

    public boolean isDebug() { return debug; }
    public boolean isUseMiniMessage() { return useMiniMessage; }
    public Map<String, String> getEntityFormats() { return entityFormats; }
    public List<String> getBlacklistedWorlds() { return blacklistedWorlds; }
    public Set<String> getIgnoreMetadata() { return ignoreMetadata; }
    public Set<String> getExtraBossTypes() { return extraBossTypes; }
    public int getUpdateInterval() { return updateInterval; }
    public double getDisplayRange() { return displayRange; }
    public double getLookingLen() { return lookingLen; }
    public boolean isLookingRequiresLineOfSight() { return lookingRequiresLineOfSight; }
    public String getVisibilityMode() { return visibilityMode; }
    public int getDamagerTimeoutSeconds() { return damagerTimeoutSeconds; }
    public boolean isIgnorePassive() { return ignorePassive; }
    public boolean isIgnorePlayers() { return ignorePlayers; }
    public boolean isShowOnBosses() { return showOnBosses; }
    public boolean isHideAtFullHealth() { return hideAtFullHealth; }
    public int getHideAfterSecondsOutOfCombat() { return hideAfterSecondsOutOfCombat; }
    public boolean isHideOnInvisible() { return hideOnInvisible; }
    public boolean isHideOnVanished() { return hideOnVanished; }
    public String getTagFormat() { return tagFormat; }

    public int getBarLength() { return barLength; }
    public String getFilledChar() { return filledChar; }
    public String getEmptyChar() { return emptyChar; }
    public String getFilledColor() { return filledColor; }
    public String getGradientHigh() { return gradientHigh; }
    public String getGradientMedium() { return gradientMedium; }
    public String getGradientLow() { return gradientLow; }
    public String getEmptyColor() { return emptyColor; }

    public boolean isMythicMobsEnabled() { return mythicMobsEnabled; }
    public boolean isShowMythicName() { return showMythicName; }
    public String getMythicCustomFormat() { return mythicCustomFormat; }

    public boolean isPapiEnabled() { return papiEnabled; }

    public String getPrefix() { return prefix; }
    public String getMsgReloadSuccess() { return msgReloadSuccess; }
    public String getMsgNoPermission() { return msgNoPermission; }
    public String getMsgUnknownCommand() { return msgUnknownCommand; }
    public String getMsgToggleOn() { return msgToggleOn; }
    public String getMsgToggleOff() { return msgToggleOff; }
    public String getMsgPlayersOnly() { return msgPlayersOnly; }

    public String getDisplayMode() { return displayMode; }

    public double getTagScale() { return tagScale; }
    public boolean isArmorStandRideMob() { return armorStandRideMob; }

    public boolean isDamageAnimationEnabled() { return damageAnimationEnabled; }
    public boolean isDamageFlash() { return damageFlash; }
    public boolean isDamageScaleBounce() { return damageScaleBounce; }
    public int getDamageAnimationDuration() { return damageAnimationDuration; }

    public boolean isDamageIndicatorEnabled() { return damageIndicatorEnabled; }
    public String getDamageIndicatorFormat() { return damageIndicatorFormat; }
    public String getDamageIndicatorCritFormat() { return damageIndicatorCritFormat; }
    public int getDamageIndicatorDuration() { return damageIndicatorDuration; }
    public double getDamageIndicatorHeight() { return damageIndicatorHeight; }
    public String getDamageIndicatorMotion() { return damageIndicatorMotion; }
    public double getDamageIndicatorMotionDistance() { return damageIndicatorMotionDistance; }
    public boolean isDamageIndicatorFadeOut() { return damageIndicatorFadeOut; }
    public boolean isDamageIndicatorShowOnPlayers() { return damageIndicatorShowOnPlayers; }
    public int getDamageIndicatorMergeTicks() { return damageIndicatorMergeTicks; }
    public int getDamageIndicatorMaxPerPlayer() { return damageIndicatorMaxPerPlayer; }

    public boolean isPlayerTagsEnabled() { return playerTagsEnabled; }
    public String getPlayerTagFormat() { return playerTagFormat; }
    public double getPlayerTagRange() { return playerTagRange; }

    public double getTagHeightJava() { return tagHeightJava; }
    public double getTagHeightBedrock() { return tagHeightBedrock; }
    public double getPlayerTagHeightJava() { return playerTagHeightJava; }
    public double getPlayerTagHeightBedrock() { return playerTagHeightBedrock; }

    public boolean isBedrockUseFloodgate() { return bedrockUseFloodgate; }
    public String getBedrockPrefix() { return bedrockPrefix; }

    public boolean isUpdateChecker() { return updateChecker; }

    /** The larger of the two ranges - sizes the single nearby-entity query per player. */
    public double getMaxRange() {
        return playerTagsEnabled ? Math.max(displayRange, playerTagRange) : displayRange;
    }

    /** Height offset for an entity, honouring a per-entity-type override when one is set. */
    public double getHeightOffset(LivingEntity entity, boolean bedrock) {
        if (entity instanceof Player) {
            return bedrock ? playerTagHeightBedrock : playerTagHeightJava;
        }
        Double override = perTypeHeight.get(entity.getType().name());
        if (override != null) return override;
        return bedrock ? tagHeightBedrock : tagHeightJava;
    }

    /** Both height variants for an entity as {java, bedrock}. */
    public double[] getHeightOffsets(LivingEntity entity) {
        if (entity instanceof Player) {
            return new double[]{playerTagHeightJava, playerTagHeightBedrock};
        }
        Double override = perTypeHeight.get(entity.getType().name());
        if (override != null) {
            return new double[]{override, override};
        }
        return new double[]{tagHeightJava, tagHeightBedrock};
    }
}
