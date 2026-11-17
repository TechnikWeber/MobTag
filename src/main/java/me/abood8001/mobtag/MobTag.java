package me.abood8001.mobtag;

import me.abood8001.mobtag.commands.MobTagCommand;
import me.abood8001.mobtag.display.IndicatorManager;
import me.abood8001.mobtag.display.TargetBarManager;
import me.abood8001.mobtag.gui.MobTagGUI;
import me.abood8001.mobtag.hooks.MythicMobsHook;
import me.abood8001.mobtag.hooks.PAPIHook;
import me.abood8001.mobtag.managers.ToggleManager;
import me.abood8001.mobtag.util.BedrockUtil;
import me.abood8001.mobtag.util.TextUtil;
import me.abood8001.mobtag.util.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class MobTag extends JavaPlugin {

    private static final int SPIGOT_RESOURCE_ID = 135699;
    private static final int BSTATS_ID = 25516;

    private static MobTag instance;

    private MobTagManager tagManager;
    private MobTagConfig mobTagConfig;
    private MythicMobsHook mythicMobsHook;
    private ToggleManager toggleManager;
    private IndicatorManager indicatorManager;
    private MobTagGUI gui;
    private TargetBarManager targetBarManager;
    private UpdateChecker updateChecker;

    private PAPIHook papiHook;
    private boolean papiEnabled = false;
    private boolean mythicMobsEnabled = false;
    private boolean packetEventsEnabled = false;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.mobTagConfig = new MobTagConfig(this);
        this.toggleManager = new ToggleManager(this);

        setupHooks();

        this.tagManager = new MobTagManager(this);
        this.indicatorManager = new IndicatorManager(this);

        // A crash can leave marker stands behind; clear them before spawning any new ones.
        int swept = tagManager.sweepOrphanedStands();
        if (swept > 0) {
            getLogger().info("Removed " + swept + " leftover tag armor stand(s) from a previous session.");
        }

        getServer().getPluginManager().registerEvents(new MobTagListener(this), this);

        this.gui = new MobTagGUI(this);
        getServer().getPluginManager().registerEvents(gui, this);

        PluginCommand command = getCommand("mobtag");
        if (command != null) {
            MobTagCommand executor = new MobTagCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'mobtag' is missing from plugin.yml - commands are disabled.");
        }

        tagManager.startTask();
        indicatorManager.start();

        this.targetBarManager = new TargetBarManager(this);
        targetBarManager.start();

        Metrics metrics = new Metrics(this, BSTATS_ID);
        metrics.addCustomChart(new SimplePie("display_mode", () -> mobTagConfig.getDisplayMode()));
        metrics.addCustomChart(new SimplePie("visibility_mode", () -> mobTagConfig.getVisibilityMode()));
        metrics.addCustomChart(new SimplePie("packetevents_installed", () -> packetEventsEnabled ? "Yes" : "No"));
        metrics.addCustomChart(new SimplePie("minimessage", () -> TextUtil.isMiniMessageAvailable() ? "Available" : "Unavailable"));

        if (mobTagConfig.isUpdateChecker()) {
            updateChecker = new UpdateChecker(this, SPIGOT_RESOURCE_ID);
            updateChecker.check();
        }

        getLogger().info("MobTag v" + getPluginVersion() + " enabled! by Abood_8001");
    }

    @Override
    public void onDisable() {
        if (indicatorManager != null) indicatorManager.stop();
        if (targetBarManager != null) targetBarManager.stop();
        if (tagManager != null) {
            tagManager.stopTask();
            tagManager.removeAllTags();
        }
        if (toggleManager != null) toggleManager.saveNow();
        instance = null;
        getLogger().info("MobTag disabled. All tags removed.");
    }

    private void setupHooks() {
        Plugin packetEvents = getServer().getPluginManager().getPlugin("packetevents");
        // Presence is not enough - the API is only usable once the plugin is actually enabled.
        packetEventsEnabled = packetEvents != null && packetEvents.isEnabled();
        if (packetEventsEnabled) {
            getLogger().info("PacketEvents hook enabled - smooth tag movement active.");
        }

        Plugin papi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        papiEnabled = papi != null && papi.isEnabled() && mobTagConfig.isPapiEnabled();
        if (papiEnabled && papiHook == null) {
            // Registered exactly once; the expansion persists across reloads by design.
            papiHook = new PAPIHook(this);
            papiHook.register();
            getLogger().info("PlaceholderAPI hook enabled.");
        }

        Plugin mythic = getServer().getPluginManager().getPlugin("MythicMobs");
        mythicMobsEnabled = mythic != null && mythic.isEnabled() && mobTagConfig.isMythicMobsEnabled();
        if (mythicMobsEnabled) {
            if (mythicMobsHook == null) mythicMobsHook = new MythicMobsHook(this);
            getLogger().info("MythicMobs hook enabled.");
        }

        if (mobTagConfig.isUseMiniMessage() && !TextUtil.isMiniMessageAvailable()) {
            getLogger().info("MiniMessage is not available on this server - using legacy colour codes.");
        }
    }

    public void reload() {
        reloadConfig();
        this.mobTagConfig = new MobTagConfig(this);
        this.toggleManager.load();
        BedrockUtil.reset();

        setupHooks();

        if (tagManager != null) {
            tagManager.removeAllTags();
            tagManager.reload();
        }
        if (indicatorManager != null) {
            indicatorManager.start();
        }
        if (targetBarManager != null) {
            targetBarManager.start();
        }
    }

    /** Plugin version without going through the deprecated description accessor on Paper. */
    public String getPluginVersion() {
        return getDescription().getVersion();
    }

    public static MobTag getInstance() { return instance; }
    public MobTagManager getTagManager() { return tagManager; }
    public MobTagConfig getMobTagConfig() { return mobTagConfig; }
    public MythicMobsHook getMythicMobsHook() { return mythicMobsHook; }
    public ToggleManager getToggleManager() { return toggleManager; }
    public IndicatorManager getIndicatorManager() { return indicatorManager; }
    public boolean isPapiEnabled() { return papiEnabled; }
    public boolean isMythicMobsEnabled() { return mythicMobsEnabled && mythicMobsHook != null; }
    public boolean isPacketEventsEnabled() { return packetEventsEnabled; }
    public MobTagGUI getGUI() { return gui; }
    public TargetBarManager getTargetBarManager() { return targetBarManager; }

    /** Re-renders every tag for this player on the next pass, after they changed their look. */
    public void refreshViewer(org.bukkit.entity.Player player) {
        if (tagManager != null) tagManager.removeViewer(player);
        if (targetBarManager != null && !toggleManager.isTargetBarOn(player.getUniqueId())) {
            targetBarManager.hide(player);
        }
    }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
}
