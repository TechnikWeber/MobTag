package me.abood8001.mobtag.util;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.scheduler.SchedulerAdapter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class UpdateChecker {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final MobTag plugin;
    private final int resourceId;

    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = null;

    public UpdateChecker(MobTag plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public boolean isUpdateAvailable() { return updateAvailable; }

    public String getLatestVersion() { return latestVersion; }

    public void check() {
        SchedulerAdapter.runAsync(plugin, () -> {
            HttpURLConnection connection = null;
            try {
                // URI.toURL() - new URL(String) has been deprecated since Java 20.
                connection = (HttpURLConnection) URI.create(
                        "https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).toURL().openConnection();
                connection.setRequestMethod("GET");
                // Without these a stalled endpoint would pin an async thread indefinitely.
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", "MobTag/" + plugin.getPluginVersion());

                String latest;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    latest = reader.readLine();
                }
                if (latest == null || latest.isBlank()) return;
                latest = latest.trim();

                String current = plugin.getPluginVersion();
                if (isNewerThanCurrent(latest, current)) {
                    updateAvailable = true;
                    latestVersion = latest;
                    plugin.getLogger().info("A new update is available! Current: "
                            + current + " | Latest: " + latest);
                    plugin.getLogger().info("Download: https://www.spigotmc.org/resources/" + resourceId);
                } else {
                    plugin.getLogger().info("You are running the latest version.");
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not check for updates: " + ex.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    /**
     * Compares versions numerically so a local dev build that is ahead of the published one is
     * not reported as "an update is available".
     */
    static boolean isNewerThanCurrent(String latest, String current) {
        if (latest.equalsIgnoreCase(current)) return false;

        String[] a = latest.split("[^0-9]+");
        String[] b = current.split("[^0-9]+");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int left = parse(a, i);
            int right = parse(b, i);
            if (left != right) return left > right;
        }
        return false;
    }

    private static int parse(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) return 0;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
