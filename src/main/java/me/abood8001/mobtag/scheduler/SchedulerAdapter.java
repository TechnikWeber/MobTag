package me.abood8001.mobtag.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Thin scheduler wrapper. Every method hands back the {@link BukkitTask} so callers can cancel
 * exactly what they started — MobTag never cancels the plugin's whole task list any more.
 */
public final class SchedulerAdapter {

    private SchedulerAdapter() {}

    public static BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
    }

    public static BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public static BukkitTask runAsync(Plugin plugin, Runnable task) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) task.cancel();
    }
}
