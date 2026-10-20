package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;


public final class HostStore {

    private static final int MAGIC = 0x46424848;


    private static final int VERSION = 1;


    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fbactors");


    private static final int MAX_VICTIMS = 4096;
    private static final int MAX_HITS = 200_000;

    private HostStore() {
    }


    public static Path pathFor(UUID replayId) {
        return TakeStore.rootFor(replayId).resolve("host-hits");
    }


    public static void save(UUID replayId, HostHits hits) {
        try {
            write(pathFor(replayId), hits);
        } catch (IOException e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.error(
                "Failed to save host hits for replay {}", replayId, e);
        }
    }


    public static HostHits load(UUID replayId) {
        return load(pathFor(replayId));
    }


    public static HostHits load(Path path) {
        HostHits hits = new HostHits();
        if (!Files.isRegularFile(path)) {
            return hits;
        }
        try {
            read(path, hits);
        } catch (IOException e) {
            LOGGER.warn("Skipping unreadable host-hits file {}", path.getFileName(), e);
        }
        return hits;
    }


    static void write(Path path, HostHits hits) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            List<UUID> victims = List.copyOf(hits.victims());
            out.writeInt(victims.size());
            for (UUID victim : victims) {
                out.writeLong(victim.getMostSignificantBits());
                out.writeLong(victim.getLeastSignificantBits());
                List<ActorHit> list = hits.hitsFor(victim);
                out.writeInt(list.size());
                for (ActorHit hit : list) {
                    writeHit(out, hit);
                }
            }
            out.flush();
        }
        try {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }


    private static void writeHit(DataOutputStream out, ActorHit hit) throws IOException {
        out.writeInt(hit.tick());
        out.writeFloat(hit.damage());
        out.writeDouble(hit.dirX());
        out.writeDouble(hit.dirZ());
        out.writeFloat(hit.attackStrength());
        net.minecraft.world.phys.Vec3 impulse = hit.impulse();
        boolean hasImpulse = impulse != null;
        out.writeBoolean(hasImpulse);
        if (hasImpulse) {
            out.writeDouble(impulse.x);
            out.writeDouble(impulse.y);
            out.writeDouble(impulse.z);
        }
        out.writeByte(hit.crit());
    }

    private static void read(Path path, HostHits hits) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Not a host-hits file: " + path.getFileName());
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Host-hits file version " + version
                    + " is not version " + VERSION);
            }
            int victimCount = in.readInt();
            checkCount("victims", victimCount, MAX_VICTIMS);
            for (int v = 0; v < victimCount; v++) {
                UUID victim = new UUID(in.readLong(), in.readLong());
                int hitCount = in.readInt();
                checkCount("host hits", hitCount, MAX_HITS);
                for (int i = 0; i < hitCount; i++) {
                    ActorHit hit = readHit(in, victim);
                    if (hit != null) {
                        hits.recordHit(victim, hit);
                    }
                }
            }
        }
        hits.sortHits();
    }

    @Nullable
    private static ActorHit readHit(DataInputStream in, UUID victim) throws IOException {
        int tick = in.readInt();
        float damage = in.readFloat();
        double dirX = in.readDouble();
        double dirZ = in.readDouble();
        float charge = in.readFloat();
        net.minecraft.world.phys.Vec3 impulse = in.readBoolean()
            ? new net.minecraft.world.phys.Vec3(
                in.readDouble(), in.readDouble(), in.readDouble())
            : null;
        byte crit = in.readByte();
        if (tick < 0) {
            return null;
        }
        return new ActorHit(tick, damage, dirX, dirZ, charge, victim, impulse, crit);
    }

    private static void checkCount(String what, int count, int max) throws IOException {
        if (count < 0 || count > max) {
            throw new IOException("Host-hits file declares " + count + " " + what
                + "; the file is truncated or was written by an incompatible build");
        }
    }
}

