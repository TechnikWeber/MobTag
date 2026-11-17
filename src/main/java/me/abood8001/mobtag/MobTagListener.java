package me.abood8001.mobtag;

import me.abood8001.mobtag.display.PacketDisplay;
import me.abood8001.mobtag.display.TagDisplay;
import me.abood8001.mobtag.scheduler.SchedulerAdapter;
import me.abood8001.mobtag.util.EntityUtil;
import me.abood8001.mobtag.util.TextUtil;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class MobTagListener implements Listener {

    private final MobTag plugin;

    public MobTagListener(MobTag plugin) {
        this.plugin = plugin;
    }

    /**
     * Tracks who last damaged a mob and fires the damage indicator / tag animation.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof ArmorStand) return;

        plugin.getTagManager().markDamaged(entity.getUniqueId());

        // Unwraps projectiles, so bows, crossbows, tridents, snowballs and splash potions all
        // count as player damage instead of being ignored entirely.
        Player player = EntityUtil.resolveDamager(event.getDamager());
        if (player == null) return;

        plugin.getTagManager().setLastDamager(entity.getUniqueId(), player.getUniqueId());
        if (plugin.getTargetBarManager() != null) {
            plugin.getTargetBarManager().recordHit(player, entity);
        }

        MobTagConfig cfg = plugin.getMobTagConfig();
        boolean packetMode = plugin.isPacketEventsEnabled() && !"ARMORSTAND".equals(cfg.getDisplayMode());
        if (!packetMode) return;

        boolean targetIsPlayer = entity instanceof Player;
        boolean indicatorAllowed = cfg.isDamageIndicatorEnabled()
                && (!targetIsPlayer || cfg.isDamageIndicatorShowOnPlayers())
                && !plugin.getToggleManager().isDamageIndicatorHidden(player.getUniqueId());

        if (indicatorAllowed && plugin.getIndicatorManager() != null) {
            double damage = Math.round(event.getFinalDamage() * 10.0) / 10.0;
            plugin.getIndicatorManager().spawn(player, entity, damage, isCritical(player, event.getDamager()));
        }

        if (cfg.isDamageAnimationEnabled()) {
            TagDisplay display = plugin.getTagManager().getTags().get(entity.getUniqueId());
            if (display instanceof PacketDisplay pd) {
                pd.playDamageAnimation(cfg.isDamageFlash(), cfg.isDamageScaleBounce(),
                        cfg.getDamageAnimationDuration(), plugin);
            }
        }
    }

    /**
     * Approximates vanilla's critical-hit rule. Bukkit exposes no crit flag, so this mirrors the
     * conditions the client uses: a falling melee hit that is not a sprint attack and not
     * assisted by a vehicle or a status effect. Projectiles are never melee crits.
     */
    private boolean isCritical(Player player, Entity damager) {
        if (!player.equals(damager)) return false; // arrows have their own crit visuals
        return player.getFallDistance() > 0.0F
                && !player.isSprinting()
                && !player.isInsideVehicle()
                && !player.isClimbing()
                && !player.isInWater()
                && player.getPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS) == null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        plugin.getTagManager().forgetEntity(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getTagManager().removeViewer(player);
        if (plugin.getTargetBarManager() != null) {
            plugin.getTargetBarManager().removePlayer(player);
        }
        if (plugin.getIndicatorManager() != null) {
            plugin.getIndicatorManager().removeFor(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Immediately, not on a delay: armor stands spawned before this player joined are
        // visible to them by default, and a one second window of wrong tags is a visible bug.
        plugin.getTagManager().hideExistingFrom(player);

        SchedulerAdapter.runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            var checker = plugin.getUpdateChecker();
            if (checker != null && checker.isUpdateAvailable() && player.hasPermission("mobtag.admin")) {
                player.sendMessage(TextUtil.colorize(
                        "&e[MobTag] &fA new update is available! &7Current: &f"
                                + plugin.getPluginVersion() + " &7Latest: &a" + checker.getLatestVersion()));
                player.sendMessage(TextUtil.colorize(
                        "&7Download: &bhttps://www.spigotmc.org/resources/135699"));
            }
        }, 20L);
    }

    /** A world change invalidates every tag the player was being shown. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.getTagManager().removeViewer(event.getPlayer());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                plugin.getTagManager().removeTag(entity.getUniqueId());
            }
        }
    }
}
