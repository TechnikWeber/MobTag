package me.abood8001.mobtag;

import me.abood8001.mobtag.display.ArmorStandDisplay;
import me.abood8001.mobtag.display.PacketDisplay;
import me.abood8001.mobtag.display.TagDisplay;
import me.abood8001.mobtag.scheduler.SchedulerAdapter;
import me.abood8001.mobtag.util.EntityUtil;
import me.abood8001.mobtag.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class MobTagManager {

    private final MobTag plugin;

    /** Mob UUID -> its display. */
    private final Map<UUID, TagDisplay> tags = new HashMap<>();
    /** Mob UUID -> UUID of the player who last damaged it (DAMAGER mode). */
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    /** Mob UUID -> millis of the last damage taken (DAMAGER timeout and out-of-combat hiding). */
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();

    private BukkitTask task;

    /** Per-tick memo of shouldTag(), so a mob seen by ten players is only filtered once. */
    private final Map<UUID, Boolean> filterCache = new HashMap<>();
    private int loopCounter = 0;

    // Debug counters, surfaced by /mobtag debug.
    private long lastLoopNanos = 0L;
    private int lastScanned = 0;

    public MobTagManager(MobTag plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        SchedulerAdapter.cancel(task);
        int interval = plugin.getMobTagConfig().getUpdateInterval();
        task = SchedulerAdapter.runTaskTimer(plugin, this::updateTags, interval, interval);
    }

    public void reload() {
        startTask();
    }

    public void stopTask() {
        SchedulerAdapter.cancel(task);
        task = null;
    }

    // ── Main loop ────────────────────────────────────────────────────────────

    /**
     * Driven from the players outward rather than over every living entity on the server: each
     * player runs one chunk-bounded nearby-entity query, so cost scales with players times local
     * mob density instead of players times world population.
     */
    private void updateTags() {
        long start = System.nanoTime();
        MobTagConfig cfg = plugin.getMobTagConfig();

        // "When to show" is a per-player choice in this fork, falling back to visibility-mode.
        Map<UUID, String> showModes = new HashMap<>();
        boolean anyLooking = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String m = plugin.getToggleManager().effectiveShow(p.getUniqueId());
            showModes.put(p.getUniqueId(), m);
            if ("LOOKING_AT".equals(m)) anyLooking = true;
        }

        // One ray trace per player per tick, not one per (player, mob) pair - the result is the
        // same whichever mob you were about to test it against.
        Map<UUID, Entity> lookTargets = anyLooking ? computeLookTargets(cfg) : Map.of();

        Map<UUID, List<Player>> wanted = new HashMap<>();
        Map<UUID, LivingEntity> byId = new HashMap<>();
        // Entities that qualify for a tag but may not currently be visible to anyone, e.g. in
        // LOOKING_AT mode between glances. Keeping their display alive avoids spawning and
        // despawning an armor stand every time a player looks away.
        Set<UUID> keepAlive = new HashSet<>();
        filterCache.clear();
        int scanned = 0;

        double range = cfg.getMaxRange();
        double mobRangeSq = cfg.getDisplayRange() * cfg.getDisplayRange();
        double playerRangeSq = cfg.getPlayerTagRange() * cfg.getPlayerTagRange();
        long now = System.currentTimeMillis();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getGameMode() == GameMode.SPECTATOR) continue;
            if (cfg.getBlacklistedWorlds().contains(viewer.getWorld().getName())) continue;

            Location viewerLoc = viewer.getLocation();
            String viewerMode = showModes.getOrDefault(viewer.getUniqueId(), "ALL");
            boolean lookingMode = "LOOKING_AT".equals(viewerMode);
            boolean damagerMode = "DAMAGER".equals(viewerMode);
            for (Entity entity : viewer.getWorld().getNearbyEntities(viewerLoc, range, range, range,
                    e -> e instanceof LivingEntity)) {
                scanned++;
                LivingEntity target = (LivingEntity) entity;
                if (target.equals(viewer)) continue; // nobody needs a tag on their own head

                try {
                    Boolean allowed = filterCache.get(target.getUniqueId());
                    if (allowed == null) {
                        allowed = shouldTag(target, cfg, now);
                        filterCache.put(target.getUniqueId(), allowed);
                    }
                    if (!allowed) continue;

                    double limitSq = (target instanceof Player) ? playerRangeSq : mobRangeSq;
                    if (viewerLoc.distanceSquared(target.getLocation()) > limitSq) continue;
                    if (isToggledOff(viewer, target)) continue;

                    keepAlive.add(target.getUniqueId());
                    byId.putIfAbsent(target.getUniqueId(), target);

                    if (lookingMode && !target.equals(lookTargets.get(viewer.getUniqueId()))) continue;
                    if (damagerMode && !isCurrentDamager(target, viewer, cfg, now)) continue;

                    wanted.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>(2)).add(viewer);
                } catch (Exception ex) {
                    debug("Failed while evaluating " + target.getType() + " for " + viewer.getName(), ex);
                }
            }
        }

        for (Map.Entry<UUID, List<Player>> entry : wanted.entrySet()) {
            LivingEntity target = byId.get(entry.getKey());
            if (target == null) continue;
            try {
                showTag(target, entry.getValue(), cfg);
            } catch (Exception ex) {
                debug("Failed to render a tag for " + target.getType(), ex);
            }
        }

        pruneTags(wanted, keepAlive);
        // The history map only needs trimming occasionally, not on every single pass.
        if (++loopCounter % 100 == 0) {
            pruneDamageHistory(cfg, now);
        }

        lastScanned = scanned;
        lastLoopNanos = System.nanoTime() - start;
    }

    /** Drops displays nobody needs any more, and hides the rest from viewers who moved on. */
    private void pruneTags(Map<UUID, List<Player>> wanted, Set<UUID> keepAlive) {
        Iterator<Map.Entry<UUID, TagDisplay>> it = tags.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TagDisplay> entry = it.next();
            TagDisplay display = entry.getValue();

            if (display.isDead() || !keepAlive.contains(entry.getKey())) {
                display.remove();
                it.remove();
                continue;
            }

            List<Player> viewers = wanted.get(entry.getKey());
            Set<UUID> keep;
            if (viewers == null || viewers.isEmpty()) {
                keep = Set.of();
            } else {
                keep = new HashSet<>(viewers.size());
                for (Player p : viewers) keep.add(p.getUniqueId());
            }

            for (UUID current : new ArrayList<>(display.getViewers())) {
                if (!keep.contains(current)) display.hideFrom(current);
            }
        }
    }

    /**
     * Keeps the damage history bounded. Without this, every mob a player ever hit stayed in
     * memory for the lifetime of the server.
     */
    private void pruneDamageHistory(MobTagConfig cfg, long now) {
        long ttlSeconds = Math.max(cfg.getDamagerTimeoutSeconds(), cfg.getHideAfterSecondsOutOfCombat());
        long ttl = Math.max(60L, ttlSeconds) * 1000L;
        lastDamageTime.entrySet().removeIf(entry -> {
            if (now - entry.getValue() <= ttl) return false;
            lastDamager.remove(entry.getKey());
            return true;
        });
    }

    private Map<UUID, Entity> computeLookTargets(MobTagConfig cfg) {
        Map<UUID, Entity> targets = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) continue;
            try {
                RayTraceResult result = player.getWorld().rayTraceEntities(
                        player.getEyeLocation(),
                        player.getEyeLocation().getDirection(),
                        cfg.getLookingLen(),
                        e -> !e.equals(player)
                );
                if (result == null || result.getHitEntity() == null) continue;
                Entity hit = result.getHitEntity();
                // Without this you can "look at" a mob straight through a wall.
                if (cfg.isLookingRequiresLineOfSight() && !player.hasLineOfSight(hit)) continue;
                targets.put(player.getUniqueId(), hit);
            } catch (Exception ex) {
                debug("Ray trace failed for " + player.getName(), ex);
            }
        }
        return targets;
    }

    private boolean isCurrentDamager(LivingEntity target, Player viewer, MobTagConfig cfg, long now) {
        UUID damager = lastDamager.get(target.getUniqueId());
        if (damager == null || !damager.equals(viewer.getUniqueId())) return false;
        int timeout = plugin.getToggleManager().effectiveDuration(viewer.getUniqueId());
        if (timeout <= 0) return true;
        Long when = lastDamageTime.get(target.getUniqueId());
        return when != null && now - when <= timeout * 1000L;
    }

    private boolean isToggledOff(Player viewer, LivingEntity target) {
        if (target instanceof Player) {
            return plugin.getToggleManager().isPlayerTagsHidden(viewer.getUniqueId());
        }
        return plugin.getToggleManager().isHidden(viewer.getUniqueId());
    }

    // ── Filtering ────────────────────────────────────────────────────────────

    private boolean shouldTag(LivingEntity entity, MobTagConfig cfg, long now) {
        if (entity instanceof Player) {
            // ignore-players is now actually honoured, matching what the config always claimed.
            if (!cfg.isPlayerTagsEnabled() || cfg.isIgnorePlayers()) return false;
            if (((Player) entity).getGameMode() == GameMode.SPECTATOR) return false;
        }

        if (entity instanceof ArmorStand) return false;

        for (String key : cfg.getIgnoreMetadata()) {
            if (entity.hasMetadata(key)) return false;
        }
        if (cfg.isHideOnVanished() && EntityUtil.isVanished(entity)) return false;
        if (cfg.isHideOnInvisible() && entity.isInvisible()) return false;

        boolean boss = EntityUtil.isBoss(entity)
                || cfg.getExtraBossTypes().contains(entity.getType().name());
        if (boss && !cfg.isShowOnBosses()) return false;

        if (cfg.isIgnorePassive() && EntityUtil.isPassive(entity)) return false;

        double max = EntityUtil.getMaxHealth(entity);
        if (max <= 0) return false; // placeholder entities would otherwise render NaN

        if (cfg.isHideAtFullHealth() && entity.getHealth() >= max) return false;

        int outOfCombat = cfg.getHideAfterSecondsOutOfCombat();
        if (outOfCombat > 0) {
            Long when = lastDamageTime.get(entity.getUniqueId());
            if (when == null || now - when > outOfCombat * 1000L) return false;
        }

        return true;
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    private void showTag(LivingEntity entity, List<Player> viewers, MobTagConfig cfg) {
        UUID uid = entity.getUniqueId();
        Location tagLoc = getTagLocation(entity, cfg);

        TagDisplay display = tags.get(uid);
        if (display == null || display.isDead()) {
            if (display != null) display.remove();
            display = createDisplay(tagLoc, entity, cfg);
            tags.put(uid, display);
        }

        // Armor stands carry one shared name, so per-player designs need the packet backend.
        boolean perPlayerStyle = display instanceof PacketDisplay;
        MobTagConfig.Style fallback = cfg.getStyles().get(cfg.getDefaultStyle());

        // Text is built once per design in use, and per viewer only when PlaceholderAPI
        // placeholders can actually differ between them.
        Map<String, String> byStyle = new HashMap<>(4);
        for (Player viewer : viewers) {
            MobTagConfig.Style style = perPlayerStyle
                    ? plugin.getToggleManager().effectiveStyle(viewer.getUniqueId())
                    : fallback;
            String format = formatFor(entity, cfg, style);
            String text;
            if (plugin.isPapiEnabled() && format.indexOf('%') >= 0) {
                text = buildTagText(entity, viewer, cfg, format);
            } else {
                text = byStyle.computeIfAbsent(style.id(), k -> buildTagText(entity, null, cfg, format));
            }
            display.update(tagLoc, text, viewer);
        }
    }

    private TagDisplay createDisplay(Location loc, LivingEntity mob, MobTagConfig cfg) {
        String mode = cfg.getDisplayMode();
        boolean usePackets = !"ARMORSTAND".equals(mode) && plugin.isPacketEventsEnabled();

        if (usePackets) {
            double[] heights = cfg.getHeightOffsets(mob);
            return new PacketDisplay(loc, mob, (float) heights[0], (float) heights[1],
                    cfg.getTagScale(), plugin);
        }
        MobTagConfig.Style style = cfg.getStyles().get(cfg.getDefaultStyle());
        return new ArmorStandDisplay(loc, buildTagText(mob, null, cfg, formatFor(mob, cfg, style)), plugin, mob);
    }

    private Location getTagLocation(LivingEntity entity, MobTagConfig cfg) {
        double offset = cfg.getHeightOffset(entity, false);
        return entity.getLocation().clone().add(0, entity.getHeight() + offset, 0);
    }

    /**
     * The raw, uninterpolated format string that applies to this entity for the given design.
     * A design with an empty format is the original behaviour: tag-format, entity-formats and
     * MythicMobs formats exactly as configured.
     */
    private String formatFor(LivingEntity entity, MobTagConfig cfg, MobTagConfig.Style style) {
        if (entity instanceof Player) return cfg.getPlayerTagFormat();

        if (plugin.isMythicMobsEnabled() && plugin.getMythicMobsHook().isMythicMob(entity)) {
            String mythic = cfg.getMythicCustomFormat();
            if (mythic != null && !mythic.isEmpty()) return mythic;
        }
        if (style != null && style.format() != null && !style.format().isEmpty()) {
            return style.format();
        }
        return cfg.getEntityFormats().getOrDefault(entity.getType().name(), cfg.getTagFormat());
    }

    /** Applies the shared placeholders. Used by the tags and by the target bar. */
    public String applyPlaceholders(LivingEntity entity, String format, MobTagConfig cfg) {
        double max = EntityUtil.getMaxHealth(entity);
        double health = entity.getHealth();
        double ratio = max > 0 ? Math.min(1.0, health / max) : 0.0;
        return format
                .replace("{name}", getMobName(entity, cfg))
                .replace("{type}", entity.getType().name())
                .replace("{current}", trim(health))
                .replace("{max}", trim(max))
                .replace("{bar}", cfg.getBar(ratio))
                .replace("{dot}", cfg.getDot(ratio))
                .replace("{percent}", String.format(Locale.US, "%.1f", ratio * 100.0));
    }

    /** Whether this entity qualifies for a tag at all, ignoring range and viewer choices. */
    public boolean isTaggable(LivingEntity entity) {
        return shouldTag(entity, plugin.getMobTagConfig(), System.currentTimeMillis());
    }

    private String buildTagText(LivingEntity entity, Player viewer, MobTagConfig cfg, String format) {

        if (plugin.isMythicMobsEnabled() && plugin.getMythicMobsHook().isMythicMob(entity)
                && format.contains("{mythic_name}")) {
            format = format.replace("{mythic_name}", plugin.getMythicMobsHook().getMythicMobName(entity));
        }

        // A packet tag rides the mob and covers its nametag, so re-attach the custom name -
        // unless the format already prints it via {name}.
        if (plugin.isPacketEventsEnabled() && !"ARMORSTAND".equals(cfg.getDisplayMode())
                && !format.contains("{name}")) {
            String customName = entity.getCustomName();
            if (customName != null && !customName.isEmpty()) {
                format = format + "\n" + customName;
            }
        }

        format = applyPlaceholders(entity, format, cfg);

        if (plugin.isPapiEnabled()) {
            try {
                format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(viewer, format);
            } catch (Exception ex) {
                debug("PlaceholderAPI failed to resolve a tag format", ex);
            }
        }

        return TextUtil.colorize(format);
    }

    /** One decimal place, but without a trailing ".0" on whole numbers. */
    private static String trim(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) return String.valueOf((long) rounded);
        return String.format(Locale.US, "%.1f", rounded);
    }

    private String getMobName(LivingEntity entity, MobTagConfig cfg) {
        String customName = entity.getCustomName();
        if (customName != null && !customName.isEmpty()) return customName;

        if (plugin.isMythicMobsEnabled() && cfg.isShowMythicName()
                && plugin.getMythicMobsHook().isMythicMob(entity)) {
            return plugin.getMythicMobsHook().getMythicMobName(entity);
        }
        return formatTypeName(entity.getType().name());
    }

    private static String formatTypeName(String type) {
        String[] words = type.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder(type.length());
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    /** @deprecated kept so older integrations keep compiling; use {@link TextUtil#colorize}. */
    @Deprecated
    public static String colorize(String text) {
        return TextUtil.colorize(text);
    }

    // ── Lifecycle / API ──────────────────────────────────────────────────────

    /**
     * Drops the display only. Used when a mob merely stops being visible - a chunk unload, for
     * instance - so its combat history survives until the mob itself is gone.
     */
    public void removeTag(UUID uid) {
        TagDisplay display = tags.remove(uid);
        if (display != null) display.remove();
    }

    /**
     * Drops the display and the combat history. Without clearing the history here, every mob a
     * player ever hit stayed in memory for the lifetime of the server.
     */
    public void forgetEntity(UUID uid) {
        removeTag(uid);
        lastDamager.remove(uid);
        lastDamageTime.remove(uid);
    }

    public void removeAllTags() {
        for (TagDisplay display : tags.values()) {
            display.remove();
        }
        tags.clear();
        lastDamager.clear();
        lastDamageTime.clear();
    }

    /** Hides every tag from a player who is leaving, without disturbing anyone else. */
    public void removeViewer(Player player) {
        for (TagDisplay display : tags.values()) {
            display.hideFrom(player);
        }
    }

    /** Armor stands spawn hidden, so a joining player must be told about the ones already alive. */
    public void hideExistingFrom(Player joiner) {
        for (TagDisplay display : tags.values()) {
            if (display instanceof ArmorStandDisplay stand) {
                stand.hideOnJoin(joiner);
            } else {
                display.hideFrom(joiner);
            }
        }
    }

    public void setLastDamager(UUID mobUID, UUID playerUID) {
        lastDamager.put(mobUID, playerUID);
        lastDamageTime.put(mobUID, System.currentTimeMillis());
    }

    /** Records combat even when no player was involved, for out-of-combat hiding. */
    public void markDamaged(UUID mobUID) {
        lastDamageTime.put(mobUID, System.currentTimeMillis());
    }

    public Map<UUID, TagDisplay> getTags() {
        return tags;
    }

    public int getTagCount() {
        return tags.size();
    }

    public int getLastScanned() {
        return lastScanned;
    }

    public double getLastLoopMillis() {
        return lastLoopNanos / 1_000_000.0D;
    }

    /**
     * Removes armor stands MobTag left behind if the server died without a clean shutdown.
     */
    public int sweepOrphanedStands() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.getScoreboardTags().contains(ArmorStandDisplay.MARKER_TAG)) {
                    stand.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    private void debug(String message, Throwable error) {
        if (plugin.getMobTagConfig().isDebug()) {
            plugin.getLogger().log(Level.WARNING, "[debug] " + message, error);
        }
    }

    /** Ray trace helper retained for API compatibility with earlier versions. */
    public boolean isLookingAt(Player player, LivingEntity entity, MobTagConfig cfg) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                cfg.getLookingLen(),
                e -> !e.equals(player)
        );
        return result != null && entity.equals(result.getHitEntity());
    }
}
