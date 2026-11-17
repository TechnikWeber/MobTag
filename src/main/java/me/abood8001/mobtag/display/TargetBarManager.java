package me.abood8001.mobtag.display;

import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.MobTagConfig;
import me.abood8001.mobtag.scheduler.SchedulerAdapter;
import me.abood8001.mobtag.util.EntityUtil;
import me.abood8001.mobtag.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Target bar": a boss bar at the top of the screen showing the mob a player is looking at, or
 * the one they are fighting. Boss bars are a vanilla feature that Geyser translates for Bedrock,
 * so unlike the floating tags this looks the same on both editions.
 */
public class TargetBarManager {

    /** How often the bar is refreshed, in ticks. */
    private static final long INTERVAL_TICKS = 2L;

    /** Keep the bar up this long after the target is lost, so it does not flicker on a miss. */
    private static final long GRACE_MS = 1000L;

    private final MobTag plugin;

    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** Player -> mob they last hit, and when. */
    private final Map<UUID, UUID> lastHit = new HashMap<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();
    /** Player -> last time a valid target was shown. */
    private final Map<UUID, Long> lastShown = new HashMap<>();

    private BukkitTask task;

    public TargetBarManager(MobTag plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getMobTagConfig().isTargetBarEnabled()) return;
        task = SchedulerAdapter.runTaskTimer(plugin, this::update, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    public void stop() {
        SchedulerAdapter.cancel(task);
        task = null;
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
        lastShown.clear();
    }

    public void recordHit(Player player, LivingEntity mob) {
        lastHit.put(player.getUniqueId(), mob.getUniqueId());
        lastHitTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void removePlayer(Player player) {
        UUID id = player.getUniqueId();
        BossBar bar = bars.remove(id);
        if (bar != null) bar.removeAll();
        lastHit.remove(id);
        lastHitTime.remove(id);
        lastShown.remove(id);
    }

    /** Hides the bar right away, e.g. after the player switched it off in the menu. */
    public void hide(Player player) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar != null) bar.removeAll();
        lastShown.remove(player.getUniqueId());
    }

    private void update() {
        MobTagConfig cfg = plugin.getMobTagConfig();
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (player.getGameMode() == GameMode.SPECTATOR
                    || !plugin.getToggleManager().isTargetBarOn(id)
                    || cfg.getBlacklistedWorlds().contains(player.getWorld().getName())) {
                hide(player);
                continue;
            }

            LivingEntity target = null;
            try {
                target = findTarget(player, cfg, now);
            } catch (Exception ignored) {
                // a failed ray trace just means no bar this tick
            }

            if (target == null) {
                Long shown = lastShown.get(id);
                if (shown == null || now - shown > GRACE_MS) hide(player);
                continue;
            }

            lastShown.put(id, now);
            BossBar bar = bars.computeIfAbsent(id, k -> Bukkit.createBossBar("", BarColor.GREEN, style(cfg)));

            double max = EntityUtil.getMaxHealth(target);
            double ratio = max > 0 ? Math.max(0.0, Math.min(1.0, target.getHealth() / max)) : 0.0;

            String title = TextUtil.colorize(
                    plugin.getTagManager().applyPlaceholders(target, cfg.getTargetBarFormat(), cfg));
            if (!title.equals(bar.getTitle())) bar.setTitle(title);
            if (Math.abs(bar.getProgress() - ratio) > 0.0001) bar.setProgress(ratio);

            BarColor color = switch (MobTagConfig.tier(ratio)) {
                case 0 -> BarColor.GREEN;
                case 1 -> BarColor.YELLOW;
                default -> BarColor.RED;
            };
            if (bar.getColor() != color) bar.setColor(color);

            if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        }
    }

    /** The mob in the crosshair, otherwise the mob this player is currently fighting. */
    private LivingEntity findTarget(Player player, MobTagConfig cfg, long now) {
        double range = cfg.getTargetBarRange();

        // Blocks and entities in one trace, so you cannot target a mob through a wall.
        RayTraceResult result = player.getWorld().rayTrace(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                FluidCollisionMode.NEVER,
                true,
                0.2,
                e -> e != player && e instanceof LivingEntity && !(e instanceof ArmorStand));
        if (result != null && result.getHitEntity() instanceof LivingEntity hit && qualifies(hit)) {
            return hit;
        }

        int combat = cfg.getTargetBarCombatSeconds();
        if (combat <= 0) return null;
        Long when = lastHitTime.get(player.getUniqueId());
        if (when == null || now - when > combat * 1000L) return null;
        UUID mobId = lastHit.get(player.getUniqueId());
        Entity mob = mobId == null ? null : Bukkit.getEntity(mobId);
        if (!(mob instanceof LivingEntity living) || !qualifies(living)) return null;
        if (!living.getWorld().equals(player.getWorld())) return null;
        if (living.getLocation().distanceSquared(player.getLocation()) > range * range) return null;
        return living;
    }

    private boolean qualifies(LivingEntity entity) {
        return entity.isValid() && !entity.isDead() && plugin.getTagManager().isTaggable(entity);
    }

    private static BarStyle style(MobTagConfig cfg) {
        try {
            return BarStyle.valueOf(cfg.getTargetBarStyle());
        } catch (IllegalArgumentException ex) {
            return BarStyle.SEGMENTED_10;
        }
    }
}
