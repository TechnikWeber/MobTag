package me.abood8001.mobtag.display;

import me.abood8001.mobtag.MobTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Fallback display backed by a real invisible marker ArmorStand.
 *
 * <p>Visibility is per viewer via {@link Player#hideEntity}/{@link Player#showEntity}: the stand
 * is hidden from everyone the moment it spawns and only revealed to players who actually qualify,
 * so per-player toggles and LOOKING_AT work here exactly as they do in packet mode.</p>
 */
public class ArmorStandDisplay implements TagDisplay {

    /** Lets admins and cleanup sweeps identify stands MobTag owns. */
    public static final String MARKER_TAG = "mobtag_display";

    private final MobTag plugin;
    private final ArmorStand stand;
    private final boolean riding;
    private final Set<UUID> viewers = new HashSet<>();
    private String lastText;

    public ArmorStandDisplay(Location loc, String text, MobTag plugin, LivingEntity mob) {
        this.plugin = plugin;
        this.lastText = text;

        this.stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setCanPickupItems(false);
            as.setCustomNameVisible(true);
            as.setCustomName(text);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setSmall(true);
            as.setMarker(true);
            as.setPersistent(false);
            as.addScoreboardTag(MARKER_TAG);
        });

        // Hidden by default so nobody sees a tag until the manager says they should.
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hideEntity(plugin, stand);
        }

        boolean mounted = false;
        if (plugin.getMobTagConfig().isArmorStandRideMob() && mob != null && !mob.isDead()) {
            try {
                mounted = mob.addPassenger(stand);
            } catch (Exception ignored) {
                mounted = false;
            }
        }
        this.riding = mounted;
    }

    @Override
    public void update(Location location, String text, Player viewer) {
        if (isDead()) return;

        // A mounted stand is carried by the mob, so teleporting it every tick is both
        // unnecessary and the source of the old one-tick stutter.
        if (!riding) {
            stand.teleport(location);
        }
        if (!text.equals(lastText)) {
            lastText = text;
            stand.setCustomName(text);
        }
        if (viewer != null && viewers.add(viewer.getUniqueId())) {
            viewer.showEntity(plugin, stand);
        }
    }

    @Override
    public void hideFrom(Player viewer) {
        if (viewer == null) return;
        if (viewers.remove(viewer.getUniqueId()) && !isDead()) {
            viewer.hideEntity(plugin, stand);
        }
    }

    @Override
    public void hideFrom(UUID viewerId) {
        if (viewerId == null) return;
        viewers.remove(viewerId);
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer != null && !isDead()) {
            viewer.hideEntity(plugin, stand);
        }
    }

    /** Keeps a freshly joined player from seeing stands that were spawned before they logged in. */
    public void hideOnJoin(Player joiner) {
        if (!isDead() && !viewers.contains(joiner.getUniqueId())) {
            joiner.hideEntity(plugin, stand);
        }
    }

    @Override
    public void remove() {
        viewers.clear();
        if (stand != null && !stand.isDead()) {
            if (riding) stand.leaveVehicle();
            stand.remove();
        }
    }

    @Override
    public boolean isDead() {
        return stand == null || stand.isDead() || !stand.isValid();
    }

    @Override
    public Set<UUID> getViewers() {
        return viewers;
    }
}
