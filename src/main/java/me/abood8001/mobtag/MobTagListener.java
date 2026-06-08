package me.abood8001.mobtag;


import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;


public class MobTagListener implements Listener {

    private final MobTag plugin;

    public MobTagListener(MobTag plugin) {
        this.plugin = plugin;
    }

    /**
     * Track who last damaged the mob (used for DAMAGER visibility mode).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof ArmorStand) return;

        if (event.getDamager() instanceof Player player) {
            plugin.getTagManager().setLastDamager(entity.getUniqueId(), player.getUniqueId());
            var cfg = plugin.getMobTagConfig();

            boolean targetIsPlayer = entity instanceof Player;
            boolean dmgIndicatorAllowed = cfg.isDamageIndicatorEnabled()
                    && plugin.isPacketEventsEnabled()
                    && !"ARMORSTAND".equals(cfg.getDisplayMode())
                    && (!targetIsPlayer || cfg.isDamageIndicatorShowOnPlayers())
                    && !plugin.getToggleManager().isDamageIndicatorHidden(player.getUniqueId());
            if (dmgIndicatorAllowed) {
                double damage = Math.round(event.getFinalDamage() * 10.0) / 10.0;
                boolean crit = player.getFallDistance() > 0 && !player.isOnGround();
                String fmt = crit ? cfg.getDamageIndicatorCritFormat() : cfg.getDamageIndicatorFormat();
                String text = me.abood8001.mobtag.MobTagManager.colorize(fmt.replace("{damage}", String.valueOf(damage)));
                // Spawn at the entity's surface facing the player (not inside the body)
                org.bukkit.util.Vector toPlayer = player.getLocation().toVector()
                        .subtract(entity.getLocation().toVector());
                toPlayer.setY(0);
                if (toPlayer.lengthSquared() > 0) toPlayer.normalize();
                double surfaceOffset = entity.getWidth() / 2.0 + 0.2;
                org.bukkit.Location loc = entity.getLocation().clone()
                        .add(toPlayer.multiply(surfaceOffset))
                        .add(0, entity.getHeight() + cfg.getDamageIndicatorHeight(), 0);

                new me.abood8001.mobtag.display.DamageIndicator(
                        loc, text, player, plugin,
                        cfg.getDamageIndicatorMotion(),
                        cfg.getDamageIndicatorMotionDistance(),
                        cfg.getDamageIndicatorDuration(),
                        cfg.isDamageIndicatorFadeOut(),
                        entity
                );
            }

            if (cfg.isDamageAnimationEnabled() && plugin.isPacketEventsEnabled()
                    && !"ARMORSTAND".equals(cfg.getDisplayMode())) {
                var display = plugin.getTagManager().getTags().get(entity.getUniqueId());
                if (display instanceof me.abood8001.mobtag.display.PacketDisplay pd) {
                    pd.playDamageAnimation(cfg.isDamageFlash(), cfg.isDamageScaleBounce(),
                            cfg.getDamageAnimationDuration(), plugin);
                }
            }
        }
    }

    /**
     * Remove the tag when the mob dies.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        plugin.getTagManager().removeTag(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (var display : plugin.getTagManager().getTags().values()) {
            if (display instanceof me.abood8001.mobtag.display.PacketDisplay pd) {
                pd.removeFor(player);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (var display : plugin.getTagManager().getTags().values()) {
                if (display instanceof me.abood8001.mobtag.display.PacketDisplay pd) {
                    pd.removeFor(player);
                }
            }
            var uc = plugin.getUpdateChecker();
            if (uc != null && uc.isUpdateAvailable() && player.hasPermission("mobtag.admin")) {
                player.sendMessage(me.abood8001.mobtag.MobTagManager.colorize(
                        "&e[MobTag] &fA new update is available! &7Current: &f"
                                + plugin.getDescription().getVersion() + " &7Latest: &a" + uc.getLatestVersion()));
                player.sendMessage(me.abood8001.mobtag.MobTagManager.colorize(
                        "&7Download: &bhttps://www.spigotmc.org/resources/135699"));
            }
        }, 20L); // 1-second delay
    }



    /**
     * Clean up tags for mobs in unloaded chunks.
     */
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (var entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                plugin.getTagManager().removeTag(entity.getUniqueId());
            }
        }
    }
}
