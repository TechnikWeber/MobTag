package me.abood8001.mobtag;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class MobTagConfig {

    private final MobTag plugin;

    private double tagScale;
    private boolean damageAnimationEnabled;
    private boolean damageFlash;
    private boolean damageScaleBounce;
    private int damageAnimationDuration;
    private boolean damageIndicatorEnabled;
    private String damageIndicatorFormat;
    private String damageIndicatorCritFormat;
    private int damageIndicatorDuration;
    private boolean updateChecker;

    private int updateInterval;
    private double displayRange;
    private double lookingLen;
    private String visibilityMode;
    private boolean ignorePassive;
    private boolean ignorePlayers;
    private boolean showOnBosses;
    private String tagFormat;

    // Health bar
    private int barLength;
    private String filledChar;
    private String emptyChar;
    private String filledColor;
    private String gradientHigh;
    private String gradientMedium;
    private String gradientLow;
    private String emptyColor;

    // MythicMobs
    private boolean mythicMobsEnabled;
    private boolean showMythicName;
    private String mythicCustomFormat;

    // PAPI
    private boolean papiEnabled;

    // Messages
    private String prefix;
    private String msgReloadSuccess;
    private String msgNoPermission;
    private String msgUnknownCommand;

    private List<String> blacklistedWorlds;
    private Map<String, String> entityFormats;
    private String displayMode;

    private double damageIndicatorHeight;
    private String damageIndicatorMotion;
    private double damageIndicatorMotionDistance;
    private boolean damageIndicatorFadeOut;

    private boolean playerTagsEnabled;
    private String playerTagFormat;
    private double playerTagRange;
    private boolean damageIndicatorShowOnPlayers;

    private double tagHeightJava;
    private double tagHeightBedrock;
    private double playerTagHeightJava;
    private double playerTagHeightBedrock;
    private boolean bedrockUseFloodgate;
    private String bedrockPrefix;

    public MobTagConfig(MobTag plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        FileConfiguration cfg = plugin.getConfig();

        entityFormats = new HashMap<>();
        if (cfg.isConfigurationSection("entity-formats")) {
            for (String key : cfg.getConfigurationSection("entity-formats").getKeys(false)) {
                entityFormats.put(key.toUpperCase(), cfg.getString("entity-formats." + key));
            }
        }
        blacklistedWorlds = cfg.getStringList("blacklisted-worlds");
        updateInterval    = cfg.getInt("update-interval", 4);
        displayRange      = cfg.getDouble("display-range", 16.0);
        lookingLen        = cfg.getDouble("looking-len", 5.0);
        visibilityMode    = cfg.getString("visibility-mode", "ALL").toUpperCase();
        ignorePassive     = cfg.getBoolean("ignore-passive", false);
        ignorePlayers     = cfg.getBoolean("ignore-players", true);
        showOnBosses      = cfg.getBoolean("show-on-bosses", true);
        tagFormat         = cfg.getString("tag-format", "&c❤ &f{current}&7/&f{max} {bar}");

        barLength         = cfg.getInt("health-bar.length", 10);
        filledChar        = cfg.getString("health-bar.filled-char", "█");
        emptyChar         = cfg.getString("health-bar.empty-char", "░");
        filledColor       = cfg.getString("health-bar.filled-color", "gradient");
        gradientHigh      = cfg.getString("health-bar.gradient-high", "&a");
        gradientMedium    = cfg.getString("health-bar.gradient-medium", "&e");
        gradientLow       = cfg.getString("health-bar.gradient-low", "&c");
        emptyColor        = cfg.getString("health-bar.empty-color", "&8");

        mythicMobsEnabled = cfg.getBoolean("mythicmobs.enabled", true);
        showMythicName    = cfg.getBoolean("mythicmobs.show-mythic-name", true);
        mythicCustomFormat= cfg.getString("mythicmobs.custom-format", "");

        papiEnabled       = cfg.getBoolean("placeholderapi.enabled", true);

        prefix            = cfg.getString("messages.prefix", "&8[&bMobTag&8] ");
        msgReloadSuccess  = cfg.getString("messages.reload-success", "&aConfiguration reloaded successfully!");
        msgNoPermission   = cfg.getString("messages.no-permission", "&cYou don't have permission to do that.");
        msgUnknownCommand = cfg.getString("messages.unknown-command", "&cUnknown sub-command. Usage: /mobtag reload");
        displayMode = cfg.getString("display-mode", "AUTO").toUpperCase();

        tagScale = cfg.getDouble("tag-scale", 1.0);
        damageAnimationEnabled = cfg.getBoolean("damage-animation.enabled", true);
        damageFlash = cfg.getBoolean("damage-animation.flash", true);
        damageScaleBounce = cfg.getBoolean("damage-animation.scale-bounce", true);
        damageAnimationDuration = cfg.getInt("damage-animation.duration", 8);
        damageIndicatorEnabled = cfg.getBoolean("damage-indicator.enabled", true);
        damageIndicatorFormat = cfg.getString("damage-indicator.format", "&c-{damage}");
        damageIndicatorCritFormat = cfg.getString("damage-indicator.crit-format", "&6&l-{damage} ✸");
        damageIndicatorDuration = cfg.getInt("damage-indicator.duration", 20);
        updateChecker = cfg.getBoolean("update-checker", true);
        damageIndicatorHeight = cfg.getDouble("damage-indicator.height-offset", 0.5);
        damageIndicatorMotion = cfg.getString("damage-indicator.motion", "UP").toUpperCase();
        damageIndicatorMotionDistance = cfg.getDouble("damage-indicator.motion-distance", 1.0);
        damageIndicatorFadeOut = cfg.getBoolean("damage-indicator.fade-out", true);

        playerTagsEnabled = cfg.getBoolean("player-tags.enabled", false);
        playerTagFormat = cfg.getString("player-tags.format", "&c❤ &f{current}&7/&f{max} &7| {bar}");
        playerTagRange = cfg.getDouble("player-tags.display-range", 16.0);
        damageIndicatorShowOnPlayers = cfg.getBoolean("damage-indicator.show-on-players", true);

        tagHeightJava = cfg.getDouble("tag-height-offset.java", 0.3);
        tagHeightBedrock = cfg.getDouble("tag-height-offset.bedrock", 0.0);
        playerTagHeightJava = cfg.getDouble("player-tags.height-offset.java", 0.3);
        playerTagHeightBedrock = cfg.getDouble("player-tags.height-offset.bedrock", 0.0);
        bedrockUseFloodgate = cfg.getBoolean("bedrock.use-floodgate", true);
        bedrockPrefix = cfg.getString("bedrock.prefix", ".");
    }

    // ── Getters ──────────────────────────────────────────────

    public Map<String, String> getEntityFormats() { return entityFormats; }
    public List<String> getBlacklistedWorlds() { return blacklistedWorlds; }
    public int getUpdateInterval() { return updateInterval; }
    public double getDisplayRange() { return displayRange; }
    public double getLookingLen() { return lookingLen; }
    public String getVisibilityMode() { return visibilityMode; }
    public boolean isIgnorePassive() { return ignorePassive; }
    public boolean isIgnorePlayers() { return ignorePlayers; }
    public boolean isShowOnBosses() { return showOnBosses; }
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

    public String getDisplayMode() { return displayMode; }

    public double getTagScale() { return tagScale; }
    public boolean isDamageAnimationEnabled() { return damageAnimationEnabled; }
    public boolean isDamageFlash() { return damageFlash; }
    public boolean isDamageScaleBounce() { return damageScaleBounce; }
    public int getDamageAnimationDuration() { return damageAnimationDuration; }
    public boolean isDamageIndicatorEnabled() { return damageIndicatorEnabled; }
    public String getDamageIndicatorFormat() { return damageIndicatorFormat; }
    public String getDamageIndicatorCritFormat() { return damageIndicatorCritFormat; }
    public int getDamageIndicatorDuration() { return damageIndicatorDuration; }
    public boolean isUpdateChecker() { return updateChecker; }
    public double getDamageIndicatorHeight() { return damageIndicatorHeight; }
    public String getDamageIndicatorMotion() { return damageIndicatorMotion; }
    public double getDamageIndicatorMotionDistance() { return damageIndicatorMotionDistance; }
    public boolean isDamageIndicatorFadeOut() { return damageIndicatorFadeOut; }

    public boolean isPlayerTagsEnabled() { return playerTagsEnabled; }
    public String getPlayerTagFormat() { return playerTagFormat; }
    public double getPlayerTagRange() { return playerTagRange; }
    public boolean isDamageIndicatorShowOnPlayers() { return damageIndicatorShowOnPlayers; }

    public double getTagHeightJava() { return tagHeightJava; }
    public double getTagHeightBedrock() { return tagHeightBedrock; }
    public double getPlayerTagHeightJava() { return playerTagHeightJava; }
    public double getPlayerTagHeightBedrock() { return playerTagHeightBedrock; }
    public boolean isBedrockUseFloodgate() { return bedrockUseFloodgate; }
    public String getBedrockPrefix() { return bedrockPrefix; }

    // Helper: get the right height for a viewer
    public double getTagHeight(me.abood8001.mobtag.MobTag plugin, org.bukkit.entity.Player viewer, boolean isPlayerTag) {
        boolean bedrock = me.abood8001.mobtag.util.BedrockUtil.isBedrock(plugin, viewer);
        if (isPlayerTag) {
            return bedrock ? playerTagHeightBedrock : playerTagHeightJava;
        }
        return bedrock ? tagHeightBedrock : tagHeightJava;
    }
}
