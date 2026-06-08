package me.abood8001.mobtag.display;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import me.abood8001.mobtag.MobTag;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DamageIndicator {

    private final int entityId;
    private final UUID uuid;
    private final Player viewer;
    private final String text;
    private Location currentLoc;
    private final Vector motionPerTick;
    private final int duration;
    private final boolean fadeOut;
    private int ticksLived = 0;

    public DamageIndicator(Location loc, String text, Player viewer, MobTag plugin,
                           String motion, double motionDistance, int duration, boolean fadeOut,
                           org.bukkit.entity.LivingEntity mob) {
        this.entityId = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE / 2, Integer.MAX_VALUE);
        this.uuid = UUID.randomUUID();
        this.viewer = viewer;
        this.text = text;
        this.duration = duration;
        this.fadeOut = fadeOut;

        // slight random horizontal spawn offset
        double offsetX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
        double offsetZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
        this.currentLoc = loc.clone().add(offsetX, 0, offsetZ);

        // Calculate motion vector per tick
        Vector dir;
        switch (motion) {
            case "DOWN":
                dir = new Vector(0, -1, 0);
                break;
            case "FORWARD":
                // toward the player who hit the mob
                dir = viewer.getLocation().toVector().subtract(mob.getLocation().toVector());
                dir.setY(0);
                if (dir.lengthSquared() > 0) dir.normalize();
                break;
            case "RANDOM":
                dir = new Vector(
                        ThreadLocalRandom.current().nextDouble() - 0.5,
                        ThreadLocalRandom.current().nextDouble(),
                        ThreadLocalRandom.current().nextDouble() - 0.5).normalize();
                break;
            case "NONE":
                dir = new Vector(0, 0, 0);
                break;
            case "UP":
            default:
                dir = new Vector(0, 1, 0);
                break;
        }
        // total distance spread over duration
        this.motionPerTick = dir.multiply(motionDistance / Math.max(1, duration));

        spawn();
        startMotion(plugin);
    }

    private void spawn() {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        if (user == null) return;

        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(uuid),
                EntityTypes.TEXT_DISPLAY,
                new Vector3d(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ()),
                0f, 0f, 0f, 0,
                Optional.empty()
        );

        user.sendPacket(spawnPacket);
        user.sendPacket(buildMeta(255));
    }

    private void startMotion(MobTag plugin) {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            ticksLived++;
            if (ticksLived >= duration) {
                remove();
                task.cancel();
                return;
            }

            currentLoc.add(motionPerTick);

            var user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
            if (user == null || !viewer.isOnline()) {
                task.cancel();
                return;
            }

            // Teleport packet to move it
            user.sendPacket(new WrapperPlayServerEntityTeleport(
                    entityId,
                    new Vector3d(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ()),
                    0f, 0f, false
            ));

            // Fade out via text opacity
            if (fadeOut) {
                int alpha = (int) (255 * (1.0 - ((double) ticksLived / duration)));
                alpha = Math.max(0, Math.min(255, alpha));
                user.sendPacket(buildMeta(alpha));
            }
        }, 1L, 1L);
    }

    private WrapperPlayServerEntityMetadata buildMeta(int alpha) {
        net.kyori.adventure.text.Component component =
                LegacyComponentSerializer.legacySection().deserialize(text);

        List<EntityData<?>> data = List.of(
                new EntityData<>(15, EntityDataTypes.BYTE, (byte) 3),
                new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, component),
                // text opacity (index 26) - byte, -1 = opaque, lower = transparent
                new EntityData<>(26, EntityDataTypes.BYTE, (byte) (alpha - 128)),
                new EntityData<>(27, EntityDataTypes.BYTE, (byte) 1)
        );
        return new WrapperPlayServerEntityMetadata(entityId, data);
    }

    public void remove() {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        if (user != null) {
            user.sendPacket(new WrapperPlayServerDestroyEntities(entityId));
        }
    }
}