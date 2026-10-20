package pdx.dev.fbactors.director;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorTake;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public final class DirectorSubjects {


    public record PlayerSubject(UUID id, String name) {
    }


    public record Subject(UUID id, String name) {
    }


    public static List<Subject> subjects() {
        List<Subject> out = new ArrayList<>();
        for (pdx.dev.fbactors.actor.Actor actor : FlashbackActors.ACTORS.actors()) {
            out.add(new Subject(actor.take.id, actor.displayName()));
        }
        for (PlayerSubject player : players()) {
            out.add(new Subject(player.id(), player.name() + " (player)"));
        }
        return out;
    }


    public static String subjectName(UUID id, List<Subject> subjects) {
        for (Subject subject : subjects) {
            if (subject.id().equals(id)) {
                return subject.name();
            }
        }
        return id.toString().substring(0, 8);
    }

    private DirectorSubjects() {
    }


    public static List<PlayerSubject> players() {
        List<PlayerSubject> out = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection =
            client == null ? null : client.getConnection();
        if (connection == null) {
            return out;
        }
        Set<UUID> viewers = viewerIds();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            if (profile == null) {
                continue;
            }
            UUID id = Compat.profileId(profile);
            String name = Compat.profileName(profile);
            if (id == null || name == null || name.isBlank()) {
                continue;
            }
            if (viewers.contains(id)) {
                continue;
            }
            if (FlashbackActors.ACTORS.byUuid(id) != null) {
                continue;
            }
            out.add(new PlayerSubject(id, name));
        }
        out.sort(Comparator.comparing(PlayerSubject::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }


    private static Set<UUID> viewerIds() {
        Set<UUID> ids = new HashSet<>();
        try {
            ReplayServer replay = Flashback.getReplayServer();
            if (replay != null) {
                for (ReplayPlayer viewer : replay.getReplayViewers()) {
                    if (viewer != null) {
                        ids.add(viewer.getUUID());
                    }
                }
            }
        } catch (RuntimeException e) {
            FlashbackActors.LOGGER.debug("Could not list replay viewers", e);
        }
        return ids;
    }


    @Nullable
    public static Vec3 positionOf(UUID id) {
        Entity entity = entityOf(id);
        if (entity == null) {
            return null;
        }
        try {
            return entity.position();
        } catch (RuntimeException e) {
            return null;
        }
    }


    public static String dimensionOf(Entity entity) {
        try {
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                return Compat.dimensionId(serverLevel);
            }
        } catch (RuntimeException e) {

        }
        return "minecraft:overworld";
    }


    public static ActorTake syntheticTake(UUID id, String name, Vec3 pos, int tick,
                                          String dimension) {
        List<ActorFrame> frames = new ArrayList<>(1);
        frames.add(new ActorFrame(pos.x, pos.y, pos.z, 0.0f, 0.0f, 0.0f,
            (byte) Pose.STANDING.ordinal(),
            ActorFrame.packFlags(false, false, true, false, false)));
        return new ActorTake(id, name, dimension, tick, frames,
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }


    @Nullable
    public static Entity entityOf(UUID id) {
        if (id == null) {
            return null;
        }
        try {
            ReplayServer replay = Flashback.getReplayServer();
            if (replay == null) {
                return null;
            }
            for (net.minecraft.server.level.ServerLevel level : replay.getAllLevels()) {
                Entity found = Compat.entityByUuid(level, id);
                if (found != null && !found.isRemoved()) {
                    return found;
                }
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

}

