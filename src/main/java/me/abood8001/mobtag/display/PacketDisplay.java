package me.abood8001.mobtag.display;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import me.abood8001.mobtag.MobTag;

public class PacketDisplay implements TagDisplay {

    private final int entityId;
    private final UUID uuid;
    private final int mobEntityId;
    private Location location;
    private String text;
    private boolean dead = false;
    private final double scale;
    private final float heightOffsetJava;
    private final float heightOffsetBedrock;
    private final boolean isPlayerTag;
    private final MobTag plugin;

    // Track which players currently see this display
    private final Set<UUID> viewers = new HashSet<>();

    public PacketDisplay(Location loc, String text, int mobEntityId, float heightJava, float heightBedrock, double scale, boolean isPlayerTag, MobTag plugin) {
        this.entityId = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE / 2, Integer.MAX_VALUE);
        this.uuid = UUID.randomUUID();
        this.location = loc;
        this.text = text;
        this.mobEntityId = mobEntityId;
        this.heightOffsetJava = heightJava;
        this.heightOffsetBedrock = heightBedrock;
        this.scale = scale;
        this.isPlayerTag = isPlayerTag;
        this.plugin = plugin;
    }

    @Override
    public void update(Location newLoc, String newText, Player viewer) {
        if (dead || viewer == null) return;
        this.location = newLoc;

        UUID viewerUUID = viewer.getUniqueId();

        if (!viewers.contains(viewerUUID)) {
            // First time this viewer sees the tag — spawn it
            spawnFor(viewer);
            viewers.add(viewerUUID);
        }

        // Update text if changed
        if (!newText.equals(this.text)) {
            this.text = newText;
            sendMetaTo(viewer);
        }
    }

    private void spawnFor(Player p) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(p);
        if (user == null) return;

        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(uuid),
                EntityTypes.TEXT_DISPLAY,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                0f, 0f, 0f, 0,
                Optional.empty()
        );

        WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(
                mobEntityId, new int[]{entityId}
        );

        user.sendPacket(spawnPacket);
        user.sendPacket(buildMetaPacket(p));
        user.sendPacket(passengersPacket);
    }

    private void sendMetaTo(Player p) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(p);
        if (user != null) {
            user.sendPacket(buildMetaPacket(p));
        }
    }

    public void removeFor(Player p) {
        if (p == null) return;
        if (!viewers.remove(p.getUniqueId())) return;
        var user = PacketEvents.getAPI().getPlayerManager().getUser(p);
        if (user != null) {
            user.sendPacket(new WrapperPlayServerDestroyEntities(entityId));
        }
    }

    @Override
    public void remove() {
        if (dead) return;
        dead = true;
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
        for (UUID viewerId : viewers) {
            Player p = org.bukkit.Bukkit.getPlayer(viewerId);
            if (p != null && p.isOnline()) {
                var user = PacketEvents.getAPI().getPlayerManager().getUser(p);
                if (user != null) {
                    user.sendPacketSilently(destroyPacket);
                }
            }
        }
        viewers.clear();
    }

    @Override
    public boolean isDead() {
        return dead;
    }

    public Set<UUID> getViewers() {
        return viewers;
    }

    private WrapperPlayServerEntityMetadata buildMetaPacket(Player viewer) {
        net.kyori.adventure.text.Component component = LegacyComponentSerializer.legacySection().deserialize(text);

        boolean bedrock = me.abood8001.mobtag.util.BedrockUtil.isBedrock(plugin, viewer);
        float height = bedrock ? heightOffsetBedrock : heightOffsetJava;

        List<EntityData<?>> data = List.of(
                new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, height, 0f)),
                new EntityData<>(12, EntityDataTypes.VECTOR3F, new Vector3f((float) scale, (float) scale, (float) scale)),
                new EntityData<>(15, EntityDataTypes.BYTE, (byte) 3),
                new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, component),
                new EntityData<>(27, EntityDataTypes.BYTE, (byte) 3)
        );
        return new WrapperPlayServerEntityMetadata(entityId, data);
    }

    public void playDamageAnimation(boolean flash, boolean scaleBounce, int duration, MobTag plugin) {
        if (dead || viewers.isEmpty()) return;

        for (UUID viewerId : viewers) {
            Player p = org.bukkit.Bukkit.getPlayer(viewerId);
            if (p == null || !p.isOnline()) continue;
            var user = PacketEvents.getAPI().getPlayerManager().getUser(p);
            if (user == null) continue;

            java.util.List<EntityData<?>> animData = new java.util.ArrayList<>();
            boolean bedrock = me.abood8001.mobtag.util.BedrockUtil.isBedrock(plugin, p);
            float h = bedrock ? heightOffsetBedrock : heightOffsetJava;
            animData.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, h, 0f)));
            float animScale = scaleBounce ? (float) (scale * 1.4) : (float) scale;
            animData.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, new Vector3f(animScale, animScale, animScale)));
            animData.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 3));
            net.kyori.adventure.text.Component comp;
            if (flash) {
                comp = LegacyComponentSerializer.legacySection().deserialize(
                        org.bukkit.ChatColor.RED + org.bukkit.ChatColor.stripColor(text));
            } else {
                comp = LegacyComponentSerializer.legacySection().deserialize(text);
            }
            animData.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, comp));
            animData.add(new EntityData<>(27, EntityDataTypes.BYTE, (byte) 3));
            user.sendPacket(new WrapperPlayServerEntityMetadata(entityId, animData));
        }

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (dead) return;
            for (UUID viewerId : viewers) {
                Player p = org.bukkit.Bukkit.getPlayer(viewerId);
                if (p == null || !p.isOnline()) continue;
                var user = PacketEvents.getAPI().getPlayerManager().getUser(p);
                if (user != null) {
                    user.sendPacket(buildMetaPacket(p));
                }
            }
        }, duration);
    }
}