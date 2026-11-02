package pdx.dev.fbactors.take;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TakeIO {

    private static final int MAGIC = 0x46424154;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fbactors");

    private static final int VERSION = 35;

    private static final int MIN_SUPPORTED_VERSION = 8;

    private static final int MAX_INPUT_RUN = 20 * 60 * 10;

    private static final int MAX_CONFIG_ACTORS = 4096;

    private static final int MAX_FRAMES = 1_000_000;
    private static final int MAX_SMALL_ENTRIES = 200_000;
    private static final long MAX_FILE_BYTES = 256L * 1024L * 1024L;

    private static String fitUtf(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() < 20000) {
            return value;
        }
        try {
            byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (encoded.length <= 60000) {
                return value;
            }
        } catch (RuntimeException e) {
            return "";
        }
        return "";
    }

    private static void checkCount(String what, int count, int max) throws IOException {
        if (count < 0 || count > max) {
            throw new IOException("Take declares " + count + " " + what
                + "; the take is truncated or was written by an incompatible build");
        }
    }

    private TakeIO() {
    }

    public static void write(Path path, ActorTake take) throws IOException {
        Files.createDirectories(path.getParent());

        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);

            out.writeLong(take.id.getMostSignificantBits());
            out.writeLong(take.id.getLeastSignificantBits());
            out.writeUTF(take.name == null ? "" : take.name);
            out.writeUTF(take.skinName == null ? "" : take.skinName);
            out.writeUTF(take.dimension == null ? "minecraft:overworld" : take.dimension);
            out.writeInt(take.startTick);

            out.writeInt(take.frames.size());
            for (ActorFrame f : take.frames) {
                out.writeDouble(f.x());
                out.writeDouble(f.y());
                out.writeDouble(f.z());
                out.writeFloat(f.yRot());
                out.writeFloat(f.xRot());
                out.writeFloat(f.yHeadRot());
                out.writeByte(f.pose());
                out.writeByte(f.flags());
            }

            out.writeInt(take.events.size());
            for (ActorEvent e : take.events) {
                out.writeInt(e.tick());
                out.writeByte(e.type().ordinal());
                boolean hasTarget = e.target() != null;
                out.writeBoolean(hasTarget);
                if (hasTarget) {
                    out.writeLong(e.target().getMostSignificantBits());
                    out.writeLong(e.target().getLeastSignificantBits());
                }
                out.writeFloat(e.attackStrength());
                out.writeDouble(e.fallDistance());
                out.writeBoolean(e.sprinting());
                out.writeUTF(e.swingAnimation() == null ? "" : e.swingAnimation());
                out.writeInt(e.swingDuration());
            }

            out.writeInt(take.equipment.size());
            for (ActorEquip e : take.equipment) {
                out.writeInt(e.tick());
                out.writeByte(e.slot());
                out.writeUTF(e.itemId() == null ? "" : e.itemId());
                out.writeInt(e.count());

                out.writeUTF(fitUtf(e.nbt()));
            }

out.writeInt(take.hits.size());
        for (ActorHit h : take.hits) {
    out.writeInt(h.tick());
        out.writeFloat(h.damage());
    out.writeDouble(h.dirX());
    out.writeDouble(h.dirZ());
       out.writeFloat(h.attackStrength());

    boolean hasTarget = h.target() != null;
     out.writeBoolean(hasTarget);
            if (hasTarget) {
            out.writeLong(h.target().getMostSignificantBits());
                out.writeLong(h.target().getLeastSignificantBits());
            }

            net.minecraft.world.phys.Vec3 impulse = h.impulse();
            boolean hasImpulse = impulse != null;
            out.writeBoolean(hasImpulse);
            if (hasImpulse) {
                out.writeDouble(impulse.x);
                out.writeDouble(impulse.y);
                out.writeDouble(impulse.z);
            }

            out.writeByte(h.crit());
        }

            out.writeInt(take.offsetTicks);
            out.writeInt(take.trimIn);
            out.writeInt(take.trimOut);
            out.writeBoolean(take.enabled);
            out.writeInt(take.colour);

            out.writeInt(take.deaths.size());
            for (ActorDeath d : take.deaths) {
                out.writeInt(d.tick());
                out.writeByte(d.kind().ordinal());
            }

            out.writeInt(take.blocks.size());
            for (ActorBlockEdit b : take.blocks) {
                out.writeInt(b.tick());
                out.writeInt(b.x());
                out.writeInt(b.y());
                out.writeInt(b.z());
                out.writeUTF(fitUtf(b.before()));
                out.writeUTF(fitUtf(b.after()));
            }

            out.writeInt(take.voices.size());
            for (ActorVoiceLine v : take.voices) {
                out.writeInt(v.tick());
                out.writeInt(v.samples().length);
                for (short s : v.samples()) {
                    out.writeShort(s);
                }

                out.writeUTF(v.caption() == null ? "" : v.caption());
            }

            out.writeBoolean(take.voiceEnabled);
            out.writeUTF(take.voiceDevice == null ? "" : take.voiceDevice);
            out.writeBoolean(take.voiceActivation);
            out.writeInt(take.voiceThreshold);

            out.writeInt(take.vitals.size());
            for (ActorVitals v : take.vitals) {
                out.writeInt(v.tick());
                out.writeFloat(v.health());
                out.writeFloat(v.absorption());
            }

            out.writeInt(take.mining.size());
            for (ActorMining m : take.mining) {
                out.writeInt(m.tick());
                out.writeInt(m.pos().getX());
                out.writeInt(m.pos().getY());
                out.writeInt(m.pos().getZ());
                out.writeInt(m.stage());
            }

            out.writeInt(take.effects.size());
            for (ActorEffect e : take.effects) {
                out.writeInt(e.tick());
                out.writeUTF(e.effectId() == null ? "" : e.effectId());
                out.writeInt(e.amplifier());
                out.writeInt(e.duration());
            }

            List<ActorInput> runs = new ArrayList<>();
            List<Integer> lengths = new ArrayList<>();
            for (ActorInput input : take.inputs) {
                if (!runs.isEmpty() && mergeable(runs.get(runs.size() - 1), input)) {
                    lengths.set(lengths.size() - 1, lengths.get(lengths.size() - 1) + 1);
                } else {
                    runs.add(input);
                    lengths.add(1);
                }
            }

            out.writeInt(runs.size());
            for (int i = 0; i < runs.size(); i++) {
                writeInput(out, runs.get(i), lengths.get(i));
            }

            out.writeInt(take.shots.size());
            for (ActorShot shot : take.shots) {
                out.writeInt(shot.tick());
                out.writeDouble(shot.dirX());
                out.writeDouble(shot.dirY());
                out.writeDouble(shot.dirZ());
                out.writeFloat(shot.power());

                out.writeUTF(shot.itemId() == null ? "" : shot.itemId());
                boolean hasTarget = shot.target() != null;
                out.writeBoolean(hasTarget);
                if (hasTarget) {
                    out.writeLong(shot.target().getMostSignificantBits());
                    out.writeLong(shot.target().getLeastSignificantBits());
                }
                out.writeUTF(shot.entityId() == null ? "" : shot.entityId());
                out.writeUTF(fitUtf(shot.entityNbt()));
            }

            out.writeInt(take.blockEntities.size());
            for (ActorBlockEntityEdit be : take.blockEntities) {
                out.writeInt(be.tick());
                out.writeInt(be.x());
                out.writeInt(be.y());
                out.writeInt(be.z());
                out.writeUTF(be.type() == null ? "" : be.type());
                out.writeUTF(fitUtf(be.nbt()));
            }

            out.writeInt(take.containers.size());
            for (ActorContainerInteraction ci : take.containers) {
                out.writeInt(ci.tick());
                out.writeInt(ci.x());
                out.writeInt(ci.y());
                out.writeInt(ci.z());
                out.writeUTF(ci.action() == null ? "" : ci.action());
                out.writeInt(ci.slot());
                out.writeUTF(ci.itemId() == null ? "" : ci.itemId());
                out.writeInt(ci.count());
                out.writeUTF(fitUtf(ci.nbt()));
            }

            out.writeInt(take.miningStates.size());
            for (ActorMiningState ms : take.miningStates) {
                out.writeInt(ms.tick());
                out.writeInt(ms.x());
                out.writeInt(ms.y());
                out.writeInt(ms.z());
                out.writeInt(ms.stage());
                out.writeBoolean(ms.swinging());
            }

            out.writeInt(take.cameraTrack.size());
            for (CameraKeyframe keyframe : take.cameraTrack.keyframes) {
                out.writeInt(keyframe.tick());
                out.writeDouble(keyframe.x());
                out.writeDouble(keyframe.y());
                out.writeDouble(keyframe.z());
                out.writeFloat(keyframe.yaw());
                out.writeFloat(keyframe.pitch());
                out.writeFloat(keyframe.fov());
                out.writeByte(keyframe.shotType().ordinal());
                boolean hasTarget = keyframe.targetActorId() != null;
                out.writeBoolean(hasTarget);
                if (hasTarget) {
                    out.writeLong(keyframe.targetActorId().getMostSignificantBits());
                    out.writeLong(keyframe.targetActorId().getLeastSignificantBits());
                }
                out.writeByte(keyframe.easing());
            }

            writeDirectorConfig(out, take.directorConfig);

            out.writeInt(take.subtitles.size());
            for (ActorSubtitle subtitle : take.subtitles) {
                out.writeInt(subtitle.tick());
                out.writeInt(subtitle.durationTicks());
                out.writeUTF(subtitle.speaker() == null ? "" : subtitle.speaker());
                out.writeUTF(subtitle.text() == null ? "" : subtitle.text());
                out.writeInt(subtitle.colorArgb());
                out.writeFloat(subtitle.size());
                out.writeByte(subtitle.position());
            }

            out.writeFloat(take.getSpeedMultiplier());

            out.writeInt(take.hiddenRanges.size());
            for (ActorVisibility h : take.hiddenRanges) {
                out.writeInt(h.startTick());
                out.writeInt(h.endTick());
            }

            out.writeInt(take.speakerColorArgb);

            out.writeBoolean(take.locked);

            out.writeInt(take.rides.size());
            for (ActorRide ride : take.rides) {
                out.writeInt(ride.tick());
                out.writeBoolean(ride.mount());
                out.writeUTF(ride.vehicleType() == null ? "" : ride.vehicleType());
                boolean hasVehicle = ride.vehicleUuid() != null;
                out.writeBoolean(hasVehicle);
                if (hasVehicle) {
                    out.writeLong(ride.vehicleUuid().getMostSignificantBits());
                    out.writeLong(ride.vehicleUuid().getLeastSignificantBits());
                }
            }

            out.writeInt(take.sounds.size());
            for (ActorSound cue : take.sounds) {
                out.writeInt(cue.tick());
                out.writeUTF(cue.soundId() == null ? "" : cue.soundId());
                out.writeDouble(cue.x());
                out.writeDouble(cue.y());
                out.writeDouble(cue.z());
                out.writeFloat(cue.volume());
                out.writeFloat(cue.pitch());
                out.writeUTF(cue.source() == null ? "" : cue.source());
            }

            out.writeInt(take.uses.size());
            for (ActorUse use : take.uses) {
                out.writeInt(use.tick());
                out.writeByte(use.kind().ordinal());
                out.writeInt(use.x());
                out.writeInt(use.y());
                out.writeInt(use.z());
                boolean hasTarget = use.target() != null;
                out.writeBoolean(hasTarget);
                if (hasTarget) {
                    out.writeLong(use.target().getMostSignificantBits());
                    out.writeLong(use.target().getLeastSignificantBits());
                }
                out.writeBoolean(use.offHand());
            }

            out.writeInt(take.spawns.size());
            for (ActorSpawn spawn : take.spawns) {
                out.writeInt(spawn.tick());
                out.writeUTF(spawn.entityId() == null ? "" : spawn.entityId());
                out.writeDouble(spawn.x());
                out.writeDouble(spawn.y());
                out.writeDouble(spawn.z());
                out.writeFloat(spawn.yaw());
                out.writeFloat(spawn.pitch());
                out.writeUTF(fitUtf(spawn.nbt()));
            }

            out.writeInt(take.uses.size());
            for (ActorUse use : take.uses) {
                out.writeUTF(fitUtf(use.itemId()));
                out.writeUTF(fitUtf(use.itemNbt()));
            }

            out.writeInt(take.vehicles.size());
            for (ActorVehicleFrame vehicle : take.vehicles) {
                out.writeInt(vehicle.tick());
                out.writeLong(vehicle.uuid().getMostSignificantBits());
                out.writeLong(vehicle.uuid().getLeastSignificantBits());
                out.writeUTF(vehicle.type());
                out.writeDouble(vehicle.x()); out.writeDouble(vehicle.y()); out.writeDouble(vehicle.z());
                out.writeFloat(vehicle.yaw()); out.writeFloat(vehicle.pitch());
            }

            out.writeInt(take.vehicles.size());
            for (ActorVehicleFrame vehicle : take.vehicles) {
                out.writeDouble(vehicle.velocityX()); out.writeDouble(vehicle.velocityY()); out.writeDouble(vehicle.velocityZ());
                out.writeFloat(vehicle.leftPrevious()); out.writeFloat(vehicle.left());
                out.writeFloat(vehicle.rightPrevious()); out.writeFloat(vehicle.right());
                out.writeBoolean(vehicle.leftPaddle()); out.writeBoolean(vehicle.rightPaddle());
            }
            out.writeInt(take.momentum.size());
            for (ActorMomentum motion : take.momentum) {
                out.writeInt(motion.tick());
                out.writeDouble(motion.x()); out.writeDouble(motion.y()); out.writeDouble(motion.z());
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

    private static void writeDirectorConfig(DataOutputStream out, @Nullable DirectorConfig config)
            throws IOException {
        if (config == null) {
            out.writeBoolean(false);
            return;
        }
        out.writeBoolean(true);
        out.writeByte(config.style().ordinal());
        out.writeBoolean(config.handheld());
        out.writeFloat(config.anticipationTicks());
        out.writeInt(config.actors().size());
        for (DirectorConfig.ActorSelection selection : config.actors()) {
            out.writeLong(selection.actorId().getMostSignificantBits());
            out.writeLong(selection.actorId().getLeastSignificantBits());
            out.writeBoolean(selection.primary());
            out.writeFloat(selection.weight());
        }

        out.writeInt(config.move() == null ? -1 : config.move().ordinal());
        out.writeInt(config.holdTicks());
        out.writeInt(config.height() == null ? -1 : config.height().ordinal());

        out.writeInt(config.shotType() == null ? -1 : config.shotType().ordinal());

        out.writeInt(config.shots().size());
        for (DirectorShot shot : config.shots()) {
            out.writeLong(shot.subjectId().getMostSignificantBits());
            out.writeLong(shot.subjectId().getLeastSignificantBits());
            out.writeInt(shot.move() == null ? -1 : shot.move().ordinal());
            out.writeInt(shot.shotType() == null ? -1 : shot.shotType().ordinal());
            out.writeInt(shot.height() == null ? -1 : shot.height().ordinal());
            out.writeInt(shot.holdTicks());
            out.writeInt(shot.startTick());
        }
    }

    private static boolean mergeable(ActorInput previous, ActorInput next) {
        return previous.samePresses(next)
            && Float.compare(previous.yawDelta(), next.yawDelta()) == 0
            && Float.compare(previous.pitchDelta(), next.pitchDelta()) == 0;
    }

private static void writeInput(DataOutputStream out, ActorInput input, int repeats)
    throws IOException {
    out.writeInt(input.tick());
        out.writeInt(repeats);
        out.writeShort(input.packFlags());

        out.writeFloat(input.forwardAxis());
        out.writeFloat(input.strafeAxis());
        out.writeFloat(input.yawDelta());
    out.writeFloat(input.pitchDelta());
        out.writeFloat(input.attackStrength());
 out.writeByte(input.selectedSlot());
    }

    public static ActorTake read(Path path) throws IOException {
        long fileSize = Files.size(path);
        if (fileSize < 0 || fileSize > MAX_FILE_BYTES) {
            throw new IOException("Take file " + path.getFileName()
                + " declares " + fileSize + " bytes");
        }
        byte[] raw;
        try {
            raw = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IOException("Could not read take file " + path.getFileName(), e);
        }

        try (DataInputStream in = new DataInputStream(
                new java.io.ByteArrayInputStream(raw))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Not a take file: " + path.getFileName());
            }
            int version = in.readInt();
            if (version > VERSION) {
                throw new IOException("Take file version " + version
                    + " is newer than this build understands (" + VERSION + ")");
            }
            if (version < MIN_SUPPORTED_VERSION) {
                throw new IOException("Take file version " + version
                    + " predates version " + MIN_SUPPORTED_VERSION
                    + ", whose layout this build no longer knows. Re-record the take.");
            }
            if (version < 13) {
                return readLegacy(raw, version, path.getFileName().toString());
            }

            UUID id = new UUID(in.readLong(), in.readLong());
            String name = in.readUTF();
            String skinName = in.readUTF();
            String dimension = in.readUTF();
            int startTick = in.readInt();

            int frameCount = in.readInt();
            checkCount("frames", frameCount, MAX_FRAMES);
            List<ActorFrame> frames = new ArrayList<>(Math.max(0, frameCount));
            for (int i = 0; i < frameCount; i++) {
                frames.add(new ActorFrame(
                    in.readDouble(), in.readDouble(), in.readDouble(),
                    in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readByte(), in.readByte()
                ));
            }

            int eventCount = in.readInt();
            checkCount("events", eventCount, MAX_SMALL_ENTRIES);
            List<ActorEvent> events = new ArrayList<>(Math.max(0, Math.min(eventCount, 1024)));
            ActorEvent.Type[] types = ActorEvent.Type.values();
            for (int i = 0; i < eventCount; i++) {
                int tick = in.readInt();
                int typeOrdinal = in.readByte();
                UUID target = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
                float attackStrength = version >= 32 ? in.readFloat() : -1.0f;
                double fallDistance = version >= 32 ? in.readDouble() : -1.0;
                boolean sprinting = version >= 32 && in.readBoolean();
                String swingAnimation = version >= 32 ? in.readUTF() : "";
                int swingDuration = version >= 32 ? in.readInt() : -1;
                if (typeOrdinal >= 0 && typeOrdinal < types.length) {
                    events.add(new ActorEvent(tick, types[typeOrdinal], target,
                        attackStrength, fallDistance, sprinting,
                        swingAnimation.isEmpty() ? null : swingAnimation, swingDuration));
                }
            }

            List<ActorEquip> equipment = new ArrayList<>();
            if (version >= 2) {
                int equipCount = in.readInt();
                checkCount("equipment", equipCount, MAX_SMALL_ENTRIES);
                for (int i = 0; i < equipCount; i++) {
                    int tick = in.readInt();
                    byte slot = in.readByte();
                    String itemId = in.readUTF();
                    int count = in.readInt();

                    String nbt = version >= 15 ? in.readUTF() : null;

                    equipment.add(new ActorEquip(tick, slot, itemId, count,
                        nbt == null || nbt.isEmpty() ? null : nbt));
                }
            }

List<ActorHit> hits = new ArrayList<>();
            {
                int hitCount = in.readInt();
                checkCount("hits", hitCount, MAX_SMALL_ENTRIES);
    for (int i = 0; i < hitCount; i++) {
       hits.add(new ActorHit(in.readInt(), in.readFloat(),
     in.readDouble(), in.readDouble(),
        in.readFloat(),
      in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null,
            version >= 19 && in.readBoolean()
                ? new net.minecraft.world.phys.Vec3(
                    in.readDouble(), in.readDouble(), in.readDouble())
                : null,
            version >= 20 ? in.readByte() : ActorHit.CRIT_NONE));
        }
    }

            ActorTake take = new ActorTake(id, name, dimension, startTick,
                frames, events, equipment, hits, new ArrayList<>());
            take.skinName = skinName.isEmpty() ? null : skinName;

            if (version >= 5) {
                take.offsetTicks = in.readInt();
                take.trimIn = in.readInt();
                take.trimOut = in.readInt();
                take.enabled = in.readBoolean();
                take.colour = in.readInt();
            }

            if (version >= 7) {
                ActorDeath.Kind[] kinds = ActorDeath.Kind.values();
                int deathCount = in.readInt();
                checkCount("deaths", deathCount, MAX_SMALL_ENTRIES);
                for (int i = 0; i < deathCount; i++) {
                    int tick = in.readInt();
                    int kindOrdinal = in.readByte();
                    if (kindOrdinal >= 0 && kindOrdinal < kinds.length) {
                        take.deaths.add(new ActorDeath(tick, kinds[kindOrdinal]));
                    }
                }
            }

            if (version >= 8) {
                int blockCount = in.readInt();
                checkCount("blocks", blockCount, MAX_SMALL_ENTRIES);
                for (int i = 0; i < blockCount; i++) {
                    int tick = in.readInt();
                    int x = in.readInt();
                    int y = in.readInt();
                    int z = in.readInt();
                    String before = in.readUTF();
                    String after = in.readUTF();
                    take.blocks.add(new ActorBlockEdit(tick, x, y, z,
                        before.isEmpty() ? null : before,
                        after.isEmpty() ? null : after));
                }
            }

            if (version >= 9) {
                int voiceCount = in.readInt();
                checkCount("voices", voiceCount, MAX_SMALL_ENTRIES);
                int budget = ActorVoiceLine.MAX_TOTAL_SAMPLES;
                for (int i = 0; i < voiceCount; i++) {
                    int tick = in.readInt();
                    int length = in.readInt();

                    if (length < 0 || length > budget) {
                        throw new IOException("Voice line at tick " + tick
                            + " has an implausible length of " + length);
                    }
                    short[] samples = new short[length];
                    for (int j = 0; j < length; j++) {
                        samples[j] = in.readShort();
                    }
                    budget -= length;
                    String caption = version >= 18 ? in.readUTF() : "";
                    take.voices.add(new ActorVoiceLine(tick, samples,
                        caption.isEmpty() ? null : caption));
                }
            }

            if (version >= 10) {
                take.voiceEnabled = in.readBoolean();
                String device = in.readUTF();
                take.voiceDevice = device.isEmpty() ? null : device;
                take.voiceActivation = in.readBoolean();
                take.voiceThreshold = in.readInt();
            }

            if (version >= 11) {
                int vitalCount = in.readInt();
                checkCount("vitals", vitalCount, MAX_SMALL_ENTRIES);
                for (int i = 0; i < vitalCount; i++) {
                    take.vitals.add(new ActorVitals(
                        in.readInt(), in.readFloat(), in.readFloat()));
                }

                int miningCount = in.readInt();
                checkCount("mining", miningCount, MAX_SMALL_ENTRIES);
                for (int i = 0; i < miningCount; i++) {
                    int tick = in.readInt();
                    int x = in.readInt();
                    int y = in.readInt();
                    int z = in.readInt();
                    take.mining.add(new ActorMining(
                        tick, new BlockPos(x, y, z), in.readInt()));
                }

                int effectCount = in.readInt();
                checkCount("effects", effectCount, MAX_SMALL_ENTRIES);
                for (int i = 0; i < effectCount; i++) {
                    int tick = in.readInt();
                    String effectId = in.readUTF();
                    int amplifier = in.readInt();
                    int duration = in.readInt();
                    if (!effectId.isEmpty()) {
                        take.effects.add(new ActorEffect(
                            tick, effectId, Math.max(0, amplifier), Math.max(0, duration)));
                    }
                }
            }

            if (version >= 12) {
                int runCount = in.readInt();
                checkCount("inputs", runCount, MAX_SMALL_ENTRIES);

for (int i = 0; i < runCount; i++) {
   int tick = in.readInt();
    int repeats = in.readInt();
          int flags = in.readShort();
    float forwardAxis = in.readFloat();
   float strafeAxis = in.readFloat();
  float yawDelta = in.readFloat();
      float pitchDelta = in.readFloat();
        float attackStrength = in.readFloat();
        byte slot = in.readByte();

              if (repeats <= 0 || repeats > MAX_INPUT_RUN) {

         throw new IOException("Input run at tick " + tick
             + " declares " + repeats + " ticks; the take is truncated"
        + " or was written by an incompatible build");
            }

      ActorInput first = ActorInput.unpack(tick, flags,
          forwardAxis, strafeAxis, yawDelta, pitchDelta, slot, attackStrength);
            take.inputs.add(first);
        for (int r = 1; r < repeats; r++) {
         take.inputs.add(first.retimed(tick + r));
       }
          }
      take.inputs.sort(java.util.Comparator.comparingInt(ActorInput::tick));
        }

        if (version >= 14) {
            int shotCount = in.readInt();
            checkCount("shots", shotCount, MAX_SMALL_ENTRIES);
            for (int i = 0; i < shotCount; i++) {
                int tick = in.readInt();
                double dx = in.readDouble();
                double dy = in.readDouble();
                double dz = in.readDouble();
                float power = in.readFloat();
                String itemId = in.readUTF();
                UUID target = in.readBoolean()
                    ? new UUID(in.readLong(), in.readLong()) : null;

                String entityId = version >= 33 ? in.readUTF() : "";
                String entityNbt = version >= 33 ? in.readUTF() : "";
                take.shots.add(new ActorShot(
                    tick, dx, dy, dz, power, itemId.isEmpty() ? null : itemId, target,
                    entityId.isEmpty() ? null : entityId, entityNbt.isEmpty() ? null : entityNbt));
            }
        }

        if (version >= 15) {

            int blockEntityCount = in.readInt();
            checkCount("block entities", blockEntityCount, MAX_SMALL_ENTRIES);
            for (int i = 0; i < blockEntityCount; i++) {
                int tick = in.readInt();
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                String type = in.readUTF();
                String nbt = in.readUTF();
                take.blockEntities.add(new ActorBlockEntityEdit(tick, x, y, z,
                    type.isEmpty() ? null : type,
                    nbt.isEmpty() ? null : nbt));
            }

            int containerCount = in.readInt();
            checkCount("containers", containerCount, MAX_SMALL_ENTRIES);
            for (int i = 0; i < containerCount; i++) {
                int tick = in.readInt();
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                String action = in.readUTF();
                int slot = in.readInt();
                String itemId = in.readUTF();
                int count = in.readInt();
                String nbt = in.readUTF();
                take.containers.add(new ActorContainerInteraction(
                    tick, x, y, z,
                    action.isEmpty() ? null : action,
                    slot,
                    itemId.isEmpty() ? null : itemId,
                    count,
                    nbt.isEmpty() ? null : nbt));
            }

            int miningStateCount = in.readInt();
            checkCount("mining states", miningStateCount, MAX_SMALL_ENTRIES);
            for (int i = 0; i < miningStateCount; i++) {
                int tick = in.readInt();
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                int stage = in.readInt();
                boolean swinging = in.readBoolean();
                take.miningStates.add(new ActorMiningState(
                    tick, x, y, z, stage, swinging));
            }
        }

        if (version >= 16) {

            int cameraCount = in.readInt();
            checkCount("camera", cameraCount, MAX_FRAMES);
            CameraKeyframe.ShotType[] shotTypes = CameraKeyframe.ShotType.values();
            for (int i = 0; i < cameraCount; i++) {
                int tick = in.readInt();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();
                float fov = in.readFloat();
                int shotOrdinal = in.readByte();
                UUID target = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
                byte easing = in.readByte();

                CameraKeyframe.ShotType shotType = shotOrdinal >= 0 && shotOrdinal < shotTypes.length
                    ? shotTypes[shotOrdinal] : CameraKeyframe.ShotType.MEDIUM;
                take.cameraTrack.add(new CameraKeyframe(
                    tick, x, y, z, yaw, pitch, fov, shotType, target, easing));
            }
            take.directorConfig = readDirectorConfig(in, version);
        }

        if (version >= 18) {

            int subtitleCount = in.readInt();
            if (subtitleCount < 0 || subtitleCount > 4096) {
                throw new IOException("Take declares " + subtitleCount
                    + " subtitles; the take is truncated or was written by an incompatible build");
            }
            for (int i = 0; i < subtitleCount; i++) {
                int tick = in.readInt();
                int duration = in.readInt();
                String speaker = in.readUTF();
                String text = in.readUTF();
                int color = in.readInt();
                float size = in.readFloat();
                byte position = in.readByte();
                if (!text.isEmpty()) {
                    take.subtitles.add(new ActorSubtitle(tick, duration,
                        speaker.isEmpty() ? null : speaker, text, color, size, position));
                }
            }
        }
        if (version >= 21) {

            try {
                float speed = in.readFloat();
                if (Float.isFinite(speed) && speed > 0.0f && speed <= 8.0f) {
                    take.setSpeedMultiplier(speed);
                }
                int hideCount = in.readInt();
                if (hideCount < 0 || hideCount > 4096) {
                    throw new IOException("Take declares " + hideCount
                        + " hidden ranges; the take is truncated or was written by an incompatible build");
                }
                for (int i = 0; i < hideCount; i++) {
                    take.hiddenRanges.add(new ActorVisibility(in.readInt(), in.readInt()));
                }

                if (version >= 23) {
                    take.speakerColorArgb = in.readInt();
                }

                if (version >= 25) {
                    try {
                        take.locked = in.readBoolean();
                    } catch (EOFException shortFile) {
                        take.locked = false;
                    }
                }

                if (version >= 27) {
                    try {
                        int rideCount = in.readInt();
                        if (rideCount < 0 || rideCount > 4096) {
                            throw new IOException("Take declares " + rideCount
                                + " rides; the take is truncated or was written by an incompatible build");
                        }
                        for (int i = 0; i < rideCount; i++) {
                            int tick = in.readInt();
                            boolean mount = in.readBoolean();
                            String type = in.readUTF();
                            UUID vehicle = in.readBoolean()
                                ? new UUID(in.readLong(), in.readLong()) : null;
                            take.rides.add(new ActorRide(tick, mount,
                                type.isEmpty() ? null : type, vehicle));
                        }
                    } catch (EOFException shortFile) {

                    }
                }

                if (version >= 28) {
                    try {
                        int soundCount = in.readInt();
                        if (soundCount < 0 || soundCount > MAX_SMALL_ENTRIES) {
                            throw new IOException("Take declares " + soundCount
                                + " sounds; the take is truncated or was written by an incompatible build");
                        }
                        for (int i = 0; i < soundCount; i++) {
                            int tick = in.readInt();
                            String soundId = in.readUTF();
                            double x = in.readDouble();
                            double y = in.readDouble();
                            double z = in.readDouble();
                            float volume = in.readFloat();
                            float pitch = in.readFloat();
                            String source = in.readUTF();
                            take.sounds.add(new ActorSound(tick,
                                soundId.isEmpty() ? null : soundId, x, y, z,
                                volume, pitch, source.isEmpty() ? null : source));
                        }
                    } catch (EOFException shortFile) {

                    }
                }

                if (version >= 29) {
                    try {
                        int useCount = in.readInt();
                        if (useCount < 0 || useCount > MAX_SMALL_ENTRIES) {
                            throw new IOException("Take declares " + useCount
                                + " uses; the take is truncated or was written by an incompatible build");
                        }
                        ActorUse.Kind[] kinds = ActorUse.Kind.values();
                        for (int i = 0; i < useCount; i++) {
                            int tick = in.readInt();
                            int kindOrdinal = in.readByte();
                            int x = in.readInt();
                            int y = in.readInt();
                            int z = in.readInt();
                            UUID target = in.readBoolean()
                                ? new UUID(in.readLong(), in.readLong()) : null;
                            boolean offHand = in.readBoolean();
                            if (kindOrdinal >= 0 && kindOrdinal < kinds.length) {
                                take.uses.add(new ActorUse(tick, kinds[kindOrdinal],
                                    x, y, z, target, offHand));
                            }
                        }
                    } catch (EOFException shortFile) {

                    }
                }

                if (version >= 30) {
                    try {
                        int spawnCount = in.readInt();
                        if (spawnCount < 0 || spawnCount > MAX_SMALL_ENTRIES) {
                            throw new IOException("Take declares " + spawnCount
                                + " spawns; the take is truncated or was written by an incompatible build");
                        }
                        for (int i = 0; i < spawnCount; i++) {
                            int tick = in.readInt();
                            String entityId = in.readUTF();
                            double x = in.readDouble();
                            double y = in.readDouble();
                            double z = in.readDouble();
                            float yaw = in.readFloat();
                            float pitch = in.readFloat();
                            String nbt = in.readUTF();
                            take.spawns.add(new ActorSpawn(tick,
                                entityId.isEmpty() ? null : entityId, x, y, z,
                                yaw, pitch, nbt.isEmpty() ? null : nbt));
                        }
                    } catch (EOFException shortFile) {

                    }
                }
                if (version >= 31) {
                    int count = in.readInt();
                    if (count < 0 || count > MAX_SMALL_ENTRIES) throw new IOException("Invalid item-use snapshot count: " + count);
                    for (int i = 0; i < count; i++) {
                        String itemId = in.readUTF(), nbt = in.readUTF();
                        if (i < take.uses.size()) take.uses.set(i, take.uses.get(i)
                            .withItem(itemId.isEmpty() ? null : itemId, nbt.isEmpty() ? null : nbt));
                    }
                }
                if (version >= 34) {
                    int count = in.readInt();
                    if (count < 0 || count > MAX_SMALL_ENTRIES) throw new IOException("Invalid vehicle frame count: " + count);
                    for (int i = 0; i < count; i++) {
                        take.vehicles.add(new ActorVehicleFrame(in.readInt(), new UUID(in.readLong(), in.readLong()),
                            in.readUTF(), in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat()));
                    }
                }
                if (version >= 35) {
                    int count = in.readInt();
                    if (count != take.vehicles.size()) throw new IOException("Vehicle motion count does not match poses: " + count);
                    for (int i = 0; i < count; i++) {
                        take.vehicles.set(i, take.vehicles.get(i).withMotion(in.readDouble(), in.readDouble(), in.readDouble(),
                            in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readBoolean(), in.readBoolean()));
                    }
                    int motions = in.readInt();
                    if (motions < 0 || motions > MAX_SMALL_ENTRIES) throw new IOException("Invalid momentum frame count: " + motions);
                    for (int i = 0; i < motions; i++) take.momentum.add(new ActorMomentum(in.readInt(), in.readDouble(), in.readDouble(), in.readDouble()));
                }
            } catch (EOFException shortFile) {

            }
        }

   take.sortHits();
   return take;
        }
    }

    public static int peekVersion(Path path) {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (in.readInt() != MAGIC) {
                return -1;
            }
            return in.readInt();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    public static boolean isLegacy(Path path) {
        int version = peekVersion(path);
        return version >= 0 && version < VERSION;
    }

    private static ActorTake readLegacy(byte[] raw, int version, String who) throws IOException {
        String[] reason = new String[1];
        ActorTake take = attemptLegacy(raw, version, true, who, reason);
        if (take != null) {
            return take;
        }
        String firstReason = reason[0];
        take = attemptLegacy(raw, version, false, who, reason);
        if (take != null) {
            return take;
        }
        throw new IOException("Take " + who + " (version " + version + ") could not be migrated: "
            + firstReason + " / " + reason[0]);
    }

    @Nullable
    private static ActorTake attemptLegacy(byte[] raw, int version, boolean withReserved,
            String who, String[] reason) {
        List<String> notes = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(raw))) {
            in.readInt();
            in.readInt();

            UUID id = new UUID(in.readLong(), in.readLong());
            String name = in.readUTF();
            String skinName = in.readUTF();
            String dimension = in.readUTF();
            int startTick = in.readInt();

            int frameCount = in.readInt();
            checkCount("frames", frameCount, MAX_FRAMES);
            if (frameCount <= 0) {
                reason[0] = "no frames";
                return null;
            }
            List<ActorFrame> frames = new ArrayList<>(frameCount);
            for (int i = 0; i < frameCount; i++) {
                frames.add(new ActorFrame(
                    in.readDouble(), in.readDouble(), in.readDouble(),
                    in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readByte(), in.readByte()));
            }

            int tickLo = startTick - 72000;
            int tickHi = startTick + frameCount + 72000;

            int eventCount = in.readInt();
            checkCount("events", eventCount, MAX_SMALL_ENTRIES);
            List<ActorEvent> events = new ArrayList<>();
            ActorEvent.Type[] types = ActorEvent.Type.values();
            for (int i = 0; i < eventCount; i++) {
                int tick = in.readInt();
                int typeOrdinal = in.readByte();
                UUID target = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
                if (tick < tickLo || tick > tickHi) {
                    reason[0] = "event tick " + tick + " outside the take";
                    return null;
                }
                if (typeOrdinal >= 0 && typeOrdinal < types.length) {
                    events.add(new ActorEvent(tick, types[typeOrdinal], target));
                }
            }

            int equipCount = in.readInt();
            checkCount("equipment", equipCount, MAX_SMALL_ENTRIES);
            List<ActorEquip> equipment = new ArrayList<>();
            int droppedSlots = 0;
            for (int i = 0; i < equipCount; i++) {
                int tick = in.readInt();
                byte slot = in.readByte();
                String itemId = in.readUTF();
                int count = in.readInt();
                if (tick < tickLo || tick > tickHi) {
                    reason[0] = "equipment tick " + tick + " outside the take";
                    return null;
                }

                if (slot < 0 || slot > 5) {
                    droppedSlots++;
                    continue;
                }
                equipment.add(new ActorEquip(tick, slot, itemId, count, null));
            }
            if (droppedSlots > 0) {
                notes.add(droppedSlots + " equipment slot(s) past the player six dropped");
            }

            int hitCount = in.readInt();
            checkCount("hits", hitCount, MAX_SMALL_ENTRIES);
            List<ActorHit> hits = new ArrayList<>();
            boolean hitsSane = true;
            for (int i = 0; i < hitCount; i++) {
                int tick = in.readInt();
                float damage = in.readFloat();
                double dirX = in.readDouble();
                double dirZ = in.readDouble();

                if (tick < tickLo || tick > tickHi
                    || !Float.isFinite(damage) || !Double.isFinite(dirX)
                    || !Double.isFinite(dirZ)) {
                    hitsSane = false;
                }
                hits.add(ActorHit.legacy(tick, damage, dirX, dirZ));
            }
            if (!hitsSane) {
                int dropped = hitCount;
                hits.clear();
                notes.add(dropped + " hit(s) unreadable in this layout, dropped");
            } else if (!hits.isEmpty()) {
                notes.add(hits.size() + " predicted-damage hit(s) taken on trust");
            }

            int offsetTicks = in.readInt();
            int trimIn = in.readInt();
            int trimOut = in.readInt();
            boolean enabled = in.readBoolean();
            int colour = in.readInt();
            int reserved = 0;
            if (withReserved) {
                reserved = in.readInt();
            }
            if (reserved != 0) {
                notes.add("reserved field is " + reserved + ", ignored");
            }

            ActorTake take = new ActorTake(id, name, dimension, startTick,
                frames, events, equipment, hits, new ArrayList<>());
            take.skinName = skinName.isEmpty() ? null : skinName;
            take.offsetTicks = offsetTicks;

            int clampedIn = Math.max(0, Math.min(trimIn, frameCount));
            int clampedOut = Math.max(0, Math.min(trimOut, frameCount));
            if (clampedIn + clampedOut > frameCount) {
                clampedOut = Math.max(0, frameCount - clampedIn);
            }
            if (clampedIn != trimIn || clampedOut != trimOut) {
                notes.add("trims repaired " + trimIn + "/" + trimOut
                    + " to " + clampedIn + "/" + clampedOut);
            }
            take.trimIn = clampedIn;
            take.trimOut = clampedOut;
            take.enabled = enabled;
            take.colour = colour;

            ActorDeath.Kind[] kinds = ActorDeath.Kind.values();
            int deathCount = in.readInt();
            checkCount("deaths", deathCount, MAX_SMALL_ENTRIES);
            for (int i = 0; i < deathCount; i++) {
                int tick = in.readInt();
                int kindOrdinal = in.readByte();
                if (tick < tickLo || tick > tickHi) {
                    reason[0] = "death tick " + tick + " outside the take";
                    return null;
                }
                if (kindOrdinal >= 0 && kindOrdinal < kinds.length) {
                    take.deaths.add(new ActorDeath(tick, kinds[kindOrdinal]));
                }
            }

            int blockCount = in.readInt();
            checkCount("blocks", blockCount, MAX_SMALL_ENTRIES);
            for (int i = 0; i < blockCount; i++) {
                int tick = in.readInt();
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                String before = in.readUTF();
                String after = in.readUTF();
                if (tick < tickLo || tick > tickHi) {
                    reason[0] = "block tick " + tick + " outside the take";
                    return null;
                }
                take.blocks.add(new ActorBlockEdit(tick, x, y, z,
                    before.isEmpty() ? null : before, after.isEmpty() ? null : after));
            }

            if (version >= 9) {
                int voiceCount = in.readInt();
                checkCount("voices", voiceCount, MAX_SMALL_ENTRIES);
                int budget = ActorVoiceLine.MAX_TOTAL_SAMPLES;
                for (int i = 0; i < voiceCount; i++) {
                    int tick = in.readInt();
                    int length = in.readInt();
                    if (length < 0 || length > budget) {
                        reason[0] = "voice line of length " + length;
                        return null;
                    }
                    short[] samples = new short[length];
                    for (int j = 0; j < length; j++) {
                        samples[j] = in.readShort();
                    }
                    budget -= length;
                    take.voices.add(new ActorVoiceLine(tick, samples, null));
                }
            }

            if (version >= 10) {
                take.voiceEnabled = in.readBoolean();
                String device = in.readUTF();
                take.voiceDevice = device.isEmpty() ? null : device;
                take.voiceActivation = in.readBoolean();
                take.voiceThreshold = in.readInt();
            }

            if (version >= 11) {
                int vitalCount = in.readInt();
                checkCount("vitals", vitalCount, MAX_SMALL_ENTRIES);
                for (int i = 0; i < vitalCount; i++) {
                    int tick = in.readInt();
                    float health = in.readFloat();
                    float absorption = in.readFloat();
                    if (tick < tickLo || tick > tickHi
                        || !Float.isFinite(health) || !Float.isFinite(absorption)) {
                        reason[0] = "vitals entry outside the take";
                        return null;
                    }
                    take.vitals.add(new ActorVitals(tick, health, absorption));
                }

                int miningCount = in.readInt();
                checkCount("mining", miningCount, MAX_SMALL_ENTRIES);
                for (int i = 0; i < miningCount; i++) {
                    int tick = in.readInt();
                    int x = in.readInt();
                    int y = in.readInt();
                    int z = in.readInt();
                    int stage = in.readInt();
                    if (tick < tickLo || tick > tickHi) {
                        reason[0] = "mining tick " + tick + " outside the take";
                        return null;
                    }
                    take.mining.add(new ActorMining(
                        tick, new BlockPos(x, y, z), stage));
                }

                int effectCount = in.readInt();
                checkCount("effects", effectCount, MAX_SMALL_ENTRIES);
                for (int i = 0; i < effectCount; i++) {
                    int tick = in.readInt();
                    String effectId = in.readUTF();
                    int amplifier = in.readInt();
                    int duration = in.readInt();
                    if (tick < tickLo || tick > tickHi) {
                        reason[0] = "effect tick " + tick + " outside the take";
                        return null;
                    }
                    if (!effectId.isEmpty()) {
                        take.effects.add(new ActorEffect(
                            tick, effectId, Math.max(0, amplifier), Math.max(0, duration)));
                    }
                }
            }

            if (version >= 12) {
                int runCount = in.readInt();
                if (runCount < 0 || runCount > 10_000_000) {
                    reason[0] = "input run count " + runCount;
                    return null;
                }

                int runs = 0;
                while (in.available() >= 19) {
                    int tick = in.readInt();
                    int extra = in.readInt();
                    int flags = in.readShort();
                    float yawDelta = in.readFloat();
                    float pitchDelta = in.readFloat();
                    byte slot = in.readByte();

                    if (tick < 0 || tick > tickHi
                        || extra < 0 || extra > frameCount + 72000
                        || !Float.isFinite(yawDelta) || !Float.isFinite(pitchDelta)) {
                        reason[0] = "input run at tick " + tick + " failed sanity";
                        return null;
                    }
                    ActorInput first = new ActorInput(tick,
                        (flags & LEGACY_SPRINT) != 0 ? 1.0f : 0.0f,
                        0.0f,
                        (flags & LEGACY_JUMP) != 0,
                        (flags & LEGACY_SNEAK) != 0,
                        (flags & LEGACY_SPRINT) != 0,
                        yawDelta, pitchDelta, slot,
                        (flags & LEGACY_USE) != 0,
                        (flags & LEGACY_ATTACK) != 0,
                        1.0f,
                        (flags & LEGACY_FLYING) != 0,
                        false);
                    take.inputs.add(first);
                    for (int r = 1; r <= extra; r++) {
                        take.inputs.add(first.retimed(tick + r));
                    }
                    runs++;
                }
                take.inputs.sort(java.util.Comparator.comparingInt(ActorInput::tick));
                if (runs > 0) {
                    notes.add(runs + " key-press input run(s) expanded"
                        + " (sprint implies full forward, strafe is lost)");
                }
            }

            if (in.available() != 0) {
                reason[0] = in.available() + " byte(s) left over";
                return null;
            }

            take.sortHits();
            LOGGER.info(
                "Migrated take {} (\"{}\") from version {} to version {} [reserved={}]: {}",
                take.id, take.name, version, VERSION, withReserved,
                notes.isEmpty() ? "exact" : String.join("; ", notes));
            return take;
        } catch (IOException e) {
            reason[0] = "unreadable (" + e.getMessage() + ")";
            return null;
        } catch (RuntimeException e) {
            reason[0] = "unreadable (" + e.getMessage() + ")";
            return null;
        }
    }

    private static final int LEGACY_JUMP = 16;
    private static final int LEGACY_SNEAK = 32;
    private static final int LEGACY_SPRINT = 64;
    private static final int LEGACY_USE = 128;
    private static final int LEGACY_ATTACK = 256;
    private static final int LEGACY_FLYING = 512;

    @Nullable
    private static DirectorConfig readDirectorConfig(DataInputStream in, int version) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        DirectorConfig.DirectorStyle[] styles = DirectorConfig.DirectorStyle.values();
        int styleOrdinal = in.readByte();
        DirectorConfig.DirectorStyle style = styleOrdinal >= 0 && styleOrdinal < styles.length
            ? styles[styleOrdinal] : DirectorConfig.DirectorStyle.SPIELBERG;
        boolean handheld = in.readBoolean();
        float anticipation = in.readFloat();

        int count = in.readInt();
        if (count < 0 || count > MAX_CONFIG_ACTORS) {
            throw new IOException("Director config declares " + count
                + " actors; the take is truncated or was written by an incompatible build");
        }
        List<DirectorConfig.ActorSelection> actors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = new UUID(in.readLong(), in.readLong());
            boolean primary = in.readBoolean();
            float weight = in.readFloat();
            actors.add(new DirectorConfig.ActorSelection(id, primary, weight));
        }

        int moveOrdinal = -1;
        int hold = DirectorConfig.MIN_HOLD_TICKS * 4;
        int heightOrdinal = -1;
        int shotOrdinal = -1;
        if (version >= 17) {
            try {
                moveOrdinal = in.readInt();
                hold = in.readInt();
                heightOrdinal = in.readInt();
            } catch (EOFException shortFile) {
                moveOrdinal = -1;
                heightOrdinal = -1;
            }
        }

        if (version >= 22) {
            try {
                shotOrdinal = in.readInt();
            } catch (EOFException shortFile) {
                shotOrdinal = -1;
            }
        }

        return new DirectorConfig(actors, style, handheld, anticipation, hold,
            CameraMove.byOrdinal(moveOrdinal), CameraHeight.byOrdinal(heightOrdinal),
            shotByOrdinal(shotOrdinal), readShots(in, version));
    }

    private static List<DirectorShot> readShots(DataInputStream in, int version) throws IOException {
        List<DirectorShot> shots = new ArrayList<>();
        if (version < 24) {
            return shots;
        }
        try {
            int count = in.readInt();
            if (count < 0 || count > 4096) {
                throw new IOException("Director config declares " + count
                    + " shots; the take is truncated or was written by an incompatible build");
            }
            for (int i = 0; i < count; i++) {
                UUID subject = new UUID(in.readLong(), in.readLong());
                CameraMove move = CameraMove.byOrdinal(in.readInt());
                CameraKeyframe.ShotType shotType = shotByOrdinal(in.readInt());
                CameraHeight height = CameraHeight.byOrdinal(in.readInt());
                int hold = in.readInt();
                int start = in.readInt();
                try {
                    shots.add(new DirectorShot(subject, move, shotType, height, hold, start));
                } catch (IllegalArgumentException bad) {

                }
            }
        } catch (EOFException shortFile) {

        }
        return shots;
    }

    @Nullable
    private static CameraKeyframe.ShotType shotByOrdinal(int ordinal) {
        CameraKeyframe.ShotType[] types = CameraKeyframe.ShotType.values();
        return ordinal >= 0 && ordinal < types.length ? types[ordinal] : null;
    }
}
