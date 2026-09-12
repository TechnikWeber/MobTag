package me.abood8001.mobtag.display;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single floating damage number. Ticked centrally by {@link IndicatorManager} rather than
 * owning a Bukkit task of its own, so a busy grinder cannot spawn hundreds of schedulers.
 */
public class DamageIndicator {

    private static final AtomicInteger ID_POOL = new AtomicInteger(Integer.MAX_VALUE / 2);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final int entityId;
    private final UUID uuid;
    private final Player viewer;
    private final UUID viewerId;
    private final UUID mobId;
    private final int duration;
    private final boolean fadeOut;
    private final Location currentLoc;
    private final Vector motionPerTick;

    private String text;
    private double totalDamage;
    private boolean crit;
    private int ticksLived = 0;
    private boolean removed = false;
    /** Manager tick of the most recent hit folded into this indicator, for the merge window. */
    private long lastHitTick;

    DamageIndicator(Location loc, String text, double damage, boolean crit, Player viewer, UUID mobId,
                    Vector direction, double motionDistance, int duration, boolean fadeOut) {
        this.entityId = ID_POOL.decrementAndGet();
        this.uuid = UUID.randomUUID();
        this.viewer = viewer;
        this.viewerId = viewer.getUniqueId();
        this.mobId = mobId;
        this.text = text;
        this.totalDamage = damage;
        this.crit = crit;
        this.duration = duration;
        this.fadeOut = fadeOut;

        // Slight horizontal jitter so stacked hits do not perfectly overlap.
        double offsetX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
        double offsetZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
        this.currentLoc = loc.clone().add(offsetX, 0, offsetZ);
        this.motionPerTick = direction.clone().multiply(motionDistance / Math.max(1, duration));

        spawn();
    }

    UUID getViewerId() { return viewerId; }

    UUID getMobId() { return mobId; }

    double getTotalDamage() { return totalDamage; }

    boolean isCrit() { return crit; }

    boolean isRemoved() { return removed; }

    long getLastHitTick() { return lastHitTick; }

    void setLastHitTick(long tick) { this.lastHitTick = tick; }

    /** Folds another hit into this indicator instead of spawning a second one on the same mob. */
    void merge(double extraDamage, boolean extraCrit,
               java.util.function.BiFunction<Double, Boolean, String> renderer) {
        this.totalDamage = Math.round((this.totalDamage + extraDamage) * 10.0) / 10.0;
        this.crit = this.crit || extraCrit;
        this.text = renderer.apply(this.totalDamage, this.crit);
        this.ticksLived = 0; // restart the lifetime so the running total stays readable
        User user = user();
        if (user != null) user.sendPacket(buildMeta(255));
    }

    private void spawn() {
        User user = user();
        if (user == null) return;

        user.sendPacket(new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(uuid),
                EntityTypes.TEXT_DISPLAY,
                new Vector3d(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ()),
                0f, 0f, 0f, 0,
                Optional.empty()
        ));
        user.sendPacket(buildMeta(255));
    }

    /** Advances one tick. Returns false once this indicator is finished and should be dropped. */
    boolean tick() {
        if (removed) return false;
        if (!viewer.isOnline()) {
            removed = true;
            return false;
        }

        ticksLived++;
        if (ticksLived >= duration) {
            remove();
            return false;
        }

        User user = user();
        if (user == null) {
            removed = true;
            return false;
        }

        currentLoc.add(motionPerTick);
        user.sendPacket(new WrapperPlayServerEntityTeleport(
                entityId,
                new Vector3d(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ()),
                0f, 0f, false
        ));

        if (fadeOut) {
            int alpha = (int) (255 * (1.0 - ((double) ticksLived / duration)));
            user.sendPacket(buildMeta(alpha));
        }
        return true;
    }

    private WrapperPlayServerEntityMetadata buildMeta(int alpha) {
        // Index 26 is an unsigned alpha byte: -1 (0xFF) is fully opaque, 0 fully transparent.
        // A plain cast maps 255 -> -1 correctly; the old "alpha - 128" wrapped mid-fade and made
        // the number blink back to full brightness halfway through.
        int clamped = Math.max(0, Math.min(255, alpha));

        List<EntityData<?>> data = List.of(
                new EntityData<>(15, EntityDataTypes.BYTE, (byte) 3),   // billboard: CENTER
                new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, LEGACY.deserialize(text)),
                new EntityData<>(26, EntityDataTypes.BYTE, (byte) clamped),
                new EntityData<>(27, EntityDataTypes.BYTE, (byte) 1)    // shadow
        );
        return new WrapperPlayServerEntityMetadata(entityId, data);
    }

    void remove() {
        if (removed) return;
        removed = true;
        User user = user();
        if (user != null) {
            user.sendPacket(new WrapperPlayServerDestroyEntities(entityId));
        }
    }

    private User user() {
        try {
            return PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        } catch (Exception ignored) {
            return null;
        }
    }
}
