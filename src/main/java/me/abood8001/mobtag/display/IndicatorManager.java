package me.abood8001.mobtag.display;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.MobTagConfig;
import me.abood8001.mobtag.scheduler.SchedulerAdapter;
import me.abood8001.mobtag.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns every floating damage number on the server.
 *
 * <p>One repeating task drives all of them, hits on the same mob inside the merge window are
 * folded into a single running total, and each player has a hard cap on concurrent indicators -
 * so an AoE sweep or a mob grinder can no longer spawn hundreds of schedulers and packet storms.</p>
 */
public class IndicatorManager {

    private final MobTag plugin;
    private final List<DamageIndicator> active = new ArrayList<>();
    private final Map<String, DamageIndicator> mergeIndex = new HashMap<>();

    private BukkitTask task;
    private long tickCounter = 0L;

    public IndicatorManager(MobTag plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = SchedulerAdapter.runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        SchedulerAdapter.cancel(task);
        task = null;
        for (DamageIndicator indicator : active) {
            indicator.remove();
        }
        active.clear();
        mergeIndex.clear();
    }

    public int getActiveCount() {
        return active.size();
    }

    private void tick() {
        tickCounter++;
        Iterator<DamageIndicator> it = active.iterator();
        while (it.hasNext()) {
            DamageIndicator indicator = it.next();
            if (!indicator.tick()) {
                it.remove();
                mergeIndex.remove(key(indicator.getViewerId(), indicator.getMobId()));
            }
        }
    }

    /**
     * Shows a damage number to one player, merging into an existing one where appropriate.
     */
    public void spawn(Player viewer, LivingEntity mob, double damage, boolean crit) {
        MobTagConfig cfg = plugin.getMobTagConfig();
        String mergeKey = key(viewer.getUniqueId(), mob.getUniqueId());

        DamageIndicator existing = mergeIndex.get(mergeKey);
        if (existing != null && !existing.isRemoved()
                && cfg.getDamageIndicatorMergeTicks() > 0
                && tickCounter - existing.getLastHitTick() <= cfg.getDamageIndicatorMergeTicks()) {
            existing.setLastHitTick(tickCounter);
            existing.merge(damage, crit, this::render);
            return;
        }

        if (countFor(viewer.getUniqueId()) >= cfg.getDamageIndicatorMaxPerPlayer()) return;

        Location loc = spawnLocation(viewer, mob, cfg);
        Vector direction = direction(viewer, mob, cfg.getDamageIndicatorMotion());

        DamageIndicator indicator = new DamageIndicator(
                loc,
                render(damage, crit),
                damage,
                crit,
                viewer,
                mob.getUniqueId(),
                direction,
                cfg.getDamageIndicatorMotionDistance(),
                cfg.getDamageIndicatorDuration(),
                cfg.isDamageIndicatorFadeOut()
        );
        indicator.setLastHitTick(tickCounter);

        active.add(indicator);
        if (cfg.getDamageIndicatorMergeTicks() > 0) {
            mergeIndex.put(mergeKey, indicator);
        }
    }

    /** Drops every indicator belonging to a player who just logged out. */
    public void removeFor(UUID viewerId) {
        Iterator<DamageIndicator> it = active.iterator();
        while (it.hasNext()) {
            DamageIndicator indicator = it.next();
            if (indicator.getViewerId().equals(viewerId)) {
                indicator.remove();
                it.remove();
                mergeIndex.remove(key(viewerId, indicator.getMobId()));
            }
        }
    }

    private String render(double damage, boolean crit) {
        MobTagConfig cfg = plugin.getMobTagConfig();
        String format = crit ? cfg.getDamageIndicatorCritFormat() : cfg.getDamageIndicatorFormat();
        String rounded = damage == Math.rint(damage)
                ? String.valueOf((long) damage)
                : String.format(Locale.US, "%.1f", damage);
        return TextUtil.colorize(format.replace("{damage}", rounded));
    }

    /** Spawns on the mob's surface facing the attacker, not buried inside the model. */
    private Location spawnLocation(Player viewer, LivingEntity mob, MobTagConfig cfg) {
        Vector toPlayer = viewer.getLocation().toVector().subtract(mob.getLocation().toVector());
        toPlayer.setY(0);
        if (toPlayer.lengthSquared() > 0) toPlayer.normalize();
        double surfaceOffset = mob.getWidth() / 2.0 + 0.2;
        return mob.getLocation().clone()
                .add(toPlayer.multiply(surfaceOffset))
                .add(0, mob.getHeight() + cfg.getDamageIndicatorHeight(), 0);
    }

    private Vector direction(Player viewer, LivingEntity mob, String motion) {
        switch (motion) {
            case "DOWN":
                return new Vector(0, -1, 0);
            case "FORWARD": {
                Vector dir = viewer.getLocation().toVector().subtract(mob.getLocation().toVector());
                dir.setY(0);
                if (dir.lengthSquared() > 0) dir.normalize();
                return dir;
            }
            case "RANDOM":
                return new Vector(
                        ThreadLocalRandom.current().nextDouble() - 0.5,
                        ThreadLocalRandom.current().nextDouble(),
                        ThreadLocalRandom.current().nextDouble() - 0.5).normalize();
            case "NONE":
                return new Vector(0, 0, 0);
            case "UP":
            default:
                return new Vector(0, 1, 0);
        }
    }

    private int countFor(UUID viewerId) {
        int count = 0;
        for (DamageIndicator indicator : active) {
            if (indicator.getViewerId().equals(viewerId)) count++;
        }
        return count;
    }

    private static String key(UUID viewerId, UUID mobId) {
        return viewerId + ":" + mobId;
    }
}
