package me.abood8001.mobtag.util;

import me.abood8001.mobtag.MobTag;
import org.bukkit.Bukkit;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class UpdateChecker {

    private final MobTag plugin;
    private final int resourceId;
    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = null;

    public boolean isUpdateAvailable() { return updateAvailable; }
    public String getLatestVersion() { return latestVersion; }

    public UpdateChecker(MobTag plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public void check() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(
                        "https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openConnection();
                con.setRequestMethod("GET");
                try (Scanner scanner = new Scanner(new InputStreamReader(con.getInputStream()))) {
                    if (scanner.hasNext()) {
                        String latest = scanner.next().trim();
                        String current = plugin.getDescription().getVersion();
                        if (!latest.equalsIgnoreCase(current)) {
                            updateAvailable = true;
                            latestVersion = latest;
                            plugin.getLogger().info("A new update is available! Current: "
                                    + current + " | Latest: " + latest);
                            plugin.getLogger().info("Download: https://www.spigotmc.org/resources/" + resourceId);
                        } else {
                            plugin.getLogger().info("You are running the latest version.");
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
            }
        });
    }
}