package me.abood8001.mobtag.display;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import me.abood8001.mobtag.MobTag;
import me.abood8001.mobtag.util.BedrockUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side TEXT_DISPLAY ridden by the tagged mob, so the tag tracks the mob perfectly with
 * no server-side entity and no teleport packets.
 */
public class PacketDisplay implements TagDisplay {

    /**
     * Fake entity IDs count down from Integer.MAX_VALUE. The server hands out real IDs counting
     * up from 1, so this can never collide - unlike the previous random allocation.
     */
    private static final AtomicInteger ID_POOL = new AtomicInteger(Integer.MAX_VALUE);

    /** Vanilla re-sends the real passenger list when a client re-tracks the mob, which detaches
     *  our tag. Re-asserting it on this cadence puts it back without flooding the connection. */
    private static final long PASSENGER_REFRESH_MS = 1000L;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final int entityId;
    private final UUID uuid;
    private final int mobEntityId;
    private final WeakReference<LivingEntity> mobRef;
    private final float heightOffsetJava;
    private final float heightOffsetBedrock;
    private final double scale;
    private final MobTag plugin;

    private Location location;
    private boolean dead = false;

    private final Set<UUID> viewers = new HashSet<>();
    /** Text last sent to each viewer, so per-viewer PlaceholderAPI output stays correct. */
    private final Map<UUID, String> textByViewer = new HashMap<>();

    private long lastPassengerRefresh = 0L;
    private int lastPassengerHash = Integer.MIN_VALUE;

    public PacketDisplay(Location loc, LivingEntity mob, float heightJava, float heightBedrock,
                         double scale, MobTag plugin) {
        this.entityId = ID_POOL.decrementAndGet();
        this.uuid = UUID.randomUUID();
        this.location = loc;
        this.mobEntityId = mob.getEntityId();
        this.mobRef = new WeakReference<>(mob);
        this.heightOffsetJava = heightJava;
        this.heightOffsetBedrock = heightBedrock;
        this.scale = scale;
        this.plugin = plugin;
    }

    @Override
    public void update(Location newLoc, String newText, Player viewer) {
        if (dead || viewer == null) return;
        this.location = newLoc;

        UUID viewerId = viewer.getUniqueId();
        boolean isNew = viewers.add(viewerId);

        if (isNew) {
            spawnFor(viewer, newText);
            textByViewer.put(viewerId, newText);
            return;
        }

        if (!newText.equals(textByViewer.get(viewerId))) {
            textByViewer.put(viewerId, newText);
            sendMeta(viewer, newText);
        }

        refreshPassengersIfNeeded();
    }

    private void spawnFor(Player p, String text) {
        User user = user(p);
        if (user == null) return;

        user.sendPacket(new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(uuid),
                EntityTypes.TEXT_DISPLAY,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                0f, 0f, 0f, 0,
                Optional.empty()
        ));
        user.sendPacket(buildMetaPacket(p, text));
        user.sendPacket(buildPassengersPacket());
    }

    /**
     * Re-asserts the passenger list when the mob's real passengers change, and periodically so a
     * client that re-tracked the mob does not end up with an orphaned tag floating in place.
     */
    private void refreshPassengersIfNeeded() {
        long now = System.currentTimeMillis();
        int hash = passengerHash();
        if (hash == lastPassengerHash && now - lastPassengerRefresh < PASSENGER_REFRESH_MS) return;

        lastPassengerHash = hash;
        lastPassengerRefresh = now;

        WrapperPlayServerSetPassengers packet = buildPassengersPacket();
        for (UUID viewerId : viewers) {
            Player p = Bukkit.getPlayer(viewerId);
            if (p == null || !p.isOnline()) continue;
            User user = user(p);
            if (user != null) user.sendPacket(packet);
        }
    }

    private int passengerHash() {
        LivingEntity mob = mobRef.get();
        if (mob == null) return 0;
        int hash = 1;
        for (Entity passenger : mob.getPassengers()) {
            hash = hash * 31 + passenger.getEntityId();
        }
        return hash;
    }

    /**
     * Includes the mob's real passengers. Sending only our tag would client-side dismount
     * anything actually riding the mob - a player on a horse, a chicken jockey, a boat rider.
     */
    private WrapperPlayServerSetPassengers buildPassengersPacket() {
        LivingEntity mob = mobRef.get();
        List<Integer> ids = new ArrayList<>();
        if (mob != null) {
            for (Entity passenger : mob.getPassengers()) {
                ids.add(passenger.getEntityId());
            }
        }
        ids.add(entityId);

        int[] array = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) array[i] = ids.get(i);
        return new WrapperPlayServerSetPassengers(mobEntityId, array);
    }

    private void sendMeta(Player p, String text) {
        User user = user(p);
        if (user != null) user.sendPacket(buildMetaPacket(p, text));
    }

    @Override
    public void hideFrom(Player p) {
        if (p == null) return;
        if (!viewers.remove(p.getUniqueId())) return;
        textByViewer.remove(p.getUniqueId());
        User user = user(p);
        if (user != null) {
            user.sendPacket(new WrapperPlayServerDestroyEntities(entityId));
        }
    }

    @Override
    public void hideFrom(UUID viewerId) {
        if (viewerId == null) return;
        // Always drop the bookkeeping, even when the player is gone, so the sets cannot leak.
        boolean wasViewer = viewers.remove(viewerId);
        textByViewer.remove(viewerId);
        if (!wasViewer) return;
        Player p = Bukkit.getPlayer(viewerId);
        if (p == null || !p.isOnline()) return;
        User user = user(p);
        if (user != null) {
            user.sendPacket(new WrapperPlayServerDestroyEntities(entityId));
        }
    }

    @Override
    public void remove() {
        if (dead) return;
        dead = true;
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(entityId);
        for (UUID viewerId : viewers) {
            Player p = Bukkit.getPlayer(viewerId);
            if (p == null || !p.isOnline()) continue;
            User user = user(p);
            if (user != null) user.sendPacketSilently(destroy);
        }
        viewers.clear();
        textByViewer.clear();
    }

    @Override
    public boolean isDead() {
        return dead;
    }

    @Override
    public Set<UUID> getViewers() {
        return viewers;
    }

    private WrapperPlayServerEntityMetadata buildMetaPacket(Player viewer, String text) {
        Component component = LEGACY.deserialize(text);
        float height = BedrockUtil.isBedrock(plugin, viewer) ? heightOffsetBedrock : heightOffsetJava;
        float s = viewerScale(viewer);

        List<EntityData<?>> data = List.of(
                new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, height, 0f)),
                new EntityData<>(12, EntityDataTypes.VECTOR3F, new Vector3f(s, s, s)),
                new EntityData<>(15, EntityDataTypes.BYTE, (byte) 3),   // billboard: CENTER
                new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, component),
                new EntityData<>(25, EntityDataTypes.INT, plugin.getMobTagConfig().getBackgroundArgb()),
                new EntityData<>(26, EntityDataTypes.BYTE, plugin.getMobTagConfig().getTextOpacityByte()),
                new EntityData<>(27, EntityDataTypes.BYTE, (byte) 3)    // shadow + see-through
        );
        return new WrapperPlayServerEntityMetadata(entityId, data);
    }

    public void playDamageAnimation(boolean flash, boolean scaleBounce, int duration, MobTag plugin) {
        if (dead || viewers.isEmpty()) return;

        for (UUID viewerId : new HashSet<>(viewers)) {
            Player p = Bukkit.getPlayer(viewerId);
            if (p == null || !p.isOnline()) continue;
            User user = user(p);
            if (user == null) continue;
            float base = viewerScale(p);
            float animScale = scaleBounce ? base * 1.4f : base;

            String viewerText = textByViewer.getOrDefault(viewerId, "");
            String animText = flash
                    ? ChatColor.RED + ChatColor.stripColor(viewerText)
                    : viewerText;

            float height = BedrockUtil.isBedrock(plugin, p) ? heightOffsetBedrock : heightOffsetJava;
            List<EntityData<?>> animData = List.of(
                    new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, height, 0f)),
                    new EntityData<>(12, EntityDataTypes.VECTOR3F, new Vector3f(animScale, animScale, animScale)),
                    new EntityData<>(15, EntityDataTypes.BYTE, (byte) 3),
                    new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, LEGACY.deserialize(animText)),
                    new EntityData<>(25, EntityDataTypes.INT, plugin.getMobTagConfig().getBackgroundArgb()),
                    new EntityData<>(26, EntityDataTypes.BYTE, plugin.getMobTagConfig().getTextOpacityByte()),
                    new EntityData<>(27, EntityDataTypes.BYTE, (byte) 3)
            );
            user.sendPacket(new WrapperPlayServerEntityMetadata(entityId, animData));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (dead) return;
            for (UUID viewerId : new HashSet<>(viewers)) {
                Player p = Bukkit.getPlayer(viewerId);
                if (p == null || !p.isOnline()) continue;
                // Restore this viewer's own text, not whichever viewer updated last.
                sendMeta(p, textByViewer.getOrDefault(viewerId, ""));
            }
        }, duration);
    }

    /** Config tag-scale times the size this viewer picked in the menu (small/medium/large). */
    private float viewerScale(Player viewer) {
        double size = 1.0;
        // Geyser renders these as plain name tags, so Bedrock always keeps the base size.
        if (BedrockUtil.isBedrock(plugin, viewer)) return (float) scale;
        try {
            size = plugin.getToggleManager().effectiveSize(viewer.getUniqueId()).scale();
        } catch (Exception ignored) {
            // fall back to the plain config scale
        }
        return (float) (scale * size);
    }

    private static User user(Player p) {
        try {
            return PacketEvents.getAPI().getPlayerManager().getUser(p);
        } catch (Exception ignored) {
            return null;
        }
    }
}
