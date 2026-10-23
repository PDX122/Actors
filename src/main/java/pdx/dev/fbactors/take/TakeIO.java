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

    /**
     * This format's logger, under the mod's own category.
     *
     * <p>A local logger rather than the shared one so this pure-data reader
     * never links the client entrypoint: migration lines log here, and those
     * paths are covered by headless unit tests that must not drag the game
     * onto the classpath.
     */
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fbactors");

    private static final int VERSION = 30;

    /**
     * The oldest take this build opens: version 8.
     *
     * <p> takes older than measured combat and analog input (versions 8
     * through 12) are not refused. They are <em>migrated</em> on read by
     * {@link #readLegacy}, which knows the byte layouts those builds wrote:
     * hits as tick, predicted damage and direction only, input runs as tick,
     * key-press flags and look deltas with no movement axes, equipment without
     * NBT, and no vitals, shots, camera or any later section. The migration
     * fills in what those files cannot say with documented stand-ins - a fully
     * charged swing, full-deflection axes, an empty input track where there
     * was none - and the take is re-saved in the current format on load, so
     * the translation happens once and every later load is native. An actor
     * must never need re-recording because the format moved on.
     *
     * <p>Anything older than version 8 is refused outright. No such file has
     * been observed, its layout is unknown, and inventing a layout for it
     * would be guessing at bytes rather than reading them.
     */
    private static final int MIN_SUPPORTED_VERSION = 8;

    /**
     * Whether bumping {@link #VERSION} was safe to do at all.
     *
     * <p>It was, and only because this format is append-only and every read is
     * gated on the version it belongs to. The shot block below is written after
     * the input runs, which are the last thing an older reader consumes, so a
     * version 13 file simply ends where that reader stops and never sees it,
     * while a version 14 file read by a version 13 build is refused by the
     * existing "newer than this build understands" check rather than being
     * misread. Nothing already on disk moved, so {@link #MIN_SUPPORTED_VERSION}
     * stays at 13: existing takes keep loading, they just have no shots, and
     * {@link ActorTake#shots} is initialised empty by the constructor rather than
     * being absent.
     *
     * <p>Version 15 stretches the same argument in both directions. Three
     * sections - block entity edits, container interactions and mining
     * states - are appended after the shots, so they inherit the shots'
     * safety exactly: a reader from before version 15 stops where its format
     * ends, and a version 15 build reading an older take meets the gate
     * below and stops there too. The equipment NBT is the exception that
     * proves the rule. It lands <em>inside</em> a section that has been in
     * the file since version 2, so it cannot be appended anywhere; it is
     * safe only because its read is gated on version 15 - a version 13 or
     * 14 take has no such string per entry, and an ungated read would take
     * the hit count that follows as a UTF length prefix - and because any
     * file that actually contains the field is version 15 or newer, which
     * every older build refuses at the version check before parsing a
     * single section.
     *
     * <p>The alternative - writing shots into the middle of the file to keep the
     * sections thematically grouped - would have required either a sentinel or a
     * rewrite of every existing file, and a take file that only loads if it was
     * rewritten by the very build being introduced is not worth the tidiness.
     *
     * <p>Version 16 appends the Director's data - the camera track it generated
     * and the config that produced it - after the version 15 sections, which
     * inherits the shots' safety exactly. It is written unconditionally, even when
     * both are empty, so the section is always present in a version 16 file and a
     * reader never has to guess whether the absence of the block meant "no camera"
     * or "truncated". {@link #MIN_SUPPORTED_VERSION} stays at 13: a version 15
     * take still loads, with {@link ActorTake#cameraTrack} empty and
     * {@link ActorTake#directorConfig} null.
     *
     * <p>Version 17 adds three fields to the <em>inside</em> of the version 16
     * Director config - the camera move the user chose, how long each shot lasts,
     * and the lens height. That is the one shape of change the append-only rule
     * does not cover, so it is handled the way the equipment NBT was: the fields
     * are written unconditionally, and the reader only reads them when the file
     * says it is version 17. A version 16 file therefore ends its config where it
     * always did, and comes back with an automatic move and height and the default
     * hold rather than with the next section read as a move ordinal.
     *
     * <p>Version 19 stretches the same in-section exception once more, for the
     * hit's measured knockback impulse: a presence flag and three doubles at
     * the end of each hit entry, read only from a version 19 file. A version
     * 18 take still loads, with every hit carrying a null impulse, which
     * replays as the base shove along the recorded direction - exact for a
     * standing unenchanted punch, short for a sprint one. The alternative,
     * appending a parallel impulse section at the end of the file, would split
     * one hit across two sections that a trim then has to keep in step.
     *
     * <p>Version 20 adds one byte per hit entry for the crit flags vanilla
     * raised for the hit, read only from a version 20 file. A version 19 take
     * still loads, with every hit carrying no crit, which replays the damage
     * with no impact particles - the way those takes always played.
     *
     * <p>Version 21 appends the playback speed and the hidden ranges at the
     * very end of the file. Older files load with 1.0x speed and no hides.
     *
     * <p>Version 22 appends the Director's forced shot type after the move
     * trio inside the Director config. Older files load with automatic
     * framing, like the move and height before it.
     *
     * <p>Version 23 appends the actor's dialogue colour after the hidden
     * ranges. Older files load with the global subtitle colour.
     *
     * <p>Version 24 appends the Director's explicit shot list inside the
     * Director config. Older configs come back with an empty list, which is
     * auto-detected moments.
     *
     * <p>Version 25 appends the track lock flag after the dialogue colour.
     * Older files load unlocked, and a file truncated before the flag keeps
     * its colour with lock defaulting to false.
     *
     * <p>Version 26 introduces the per-replay host-hits sidecar
     * ({@code fbactors/<replay-uuid>/host-hits}, see {@link HostStore}) and
     * changes not one take byte to do it: the writer emits exactly the
     * version 25 layout, and the reader needs no version 26 gate because
     * there is nothing new to read. The bump marks the first build that
     * understands host damage, so a version 25 take loads natively here -
     * with every section it ever had - and simply finds no sidecar beside
     * it, which reads as empty: no host damage, the long-standing behaviour.
     * Like every older file it is re-saved in the current format on load
     * (see {@link TakeStore#loadAll}), so the translation happens once.
     *
     * <p>Version 27 appends ride transitions (mount/dismount with vehicle
     * identity) after the version 25 lock flag. Older files end before the
     * section and load with no rides; older builds refuse the file at the
     * version check before they can misread one.
     *
     * <p>Version 28 appends sound cues (registry id, position, volume, pitch,
     * source) after the rides. Older files end before the section and load
     * silent; older builds refuse the file at the version check.
     *
     * <p>Version 29 appends discrete uses (kind, block pos, entity UUID, hand)
     * after the sounds. Older files end before the section and load with no
     * uses; older builds refuse the file at the version check.
     *
     * <p>Version 30 appends placed-entity spawns (registry id, position,
     * rotation, opaque NBT) after the uses. Older files end before the
     * section and load with no spawns; older builds refuse the file at the
     * version check.
     */

    /**
     * Longest single input run accepted on read. A run is one stretch of
     * identical presses and look deltas, so a legitimate one is a few seconds
     * at most; anything longer is corrupt.
     */
    private static final int MAX_INPUT_RUN = 20 * 60 * 10;

    /**
     * The most actors a stored Director config may name.
     *
     * <p>Generous - the generator caps itself at ten - but finite, so a corrupt
     * count cannot be used to allocate an arbitrary list before the file runs out.
     */
    private static final int MAX_CONFIG_ACTORS = 4096;

    /**
     * Hard caps for section counts read from disk. A corrupt count would
     * otherwise pre-size an ArrayList (or loop) into an OOM before the file
     * runs out. Voice/subtitle/director sections already had bounds; frames
     * and the small-record sections get the same treatment here. Bounds are
     * generous (5h at 20tps = 360k ticks) so no legitimate take can reach them.
     */
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
        // Atomic write: a crash mid-save must not leave a truncated .take
        // that the next load skips entirely. Write to a sibling tmp file
        // and move it over the target.
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
            }

            out.writeInt(take.equipment.size());
            for (ActorEquip e : take.equipment) {
                out.writeInt(e.tick());
                out.writeByte(e.slot());
                out.writeUTF(e.itemId() == null ? "" : e.itemId());
                out.writeInt(e.count());
                // The stack's NBT, written as the empty string when the slot
                // held a plain item - the same convention itemId uses above,
                // so a take full of unenchanted gear costs two bytes per
                // entry rather than a fixed string.
                out.writeUTF(fitUtf(e.nbt()));
            }

out.writeInt(take.hits.size());
        for (ActorHit h : take.hits) {
    out.writeInt(h.tick());
        out.writeFloat(h.damage());
    out.writeDouble(h.dirX());
    out.writeDouble(h.dirZ());
       out.writeFloat(h.attackStrength());
    // A hit against something that is not one of our actors, or recorded before
          // targets were stored, has no identity. The flag keeps a run of hits
      // on a mob from costing sixteen bytes each.
    boolean hasTarget = h.target() != null;
     out.writeBoolean(hasTarget);
            if (hasTarget) {
            out.writeLong(h.target().getMostSignificantBits());
                out.writeLong(h.target().getLeastSignificantBits());
            }
            // Version 19: the measured knockback impulse, or no flag when the
            // hit was recorded before impulses were measured. Gated like the
            // equipment NBT: a version 18 take has no such flag per entry, and
            // an ungated read would take the offset that follows as a boolean.
            net.minecraft.world.phys.Vec3 impulse = h.impulse();
            boolean hasImpulse = impulse != null;
            out.writeBoolean(hasImpulse);
            if (hasImpulse) {
                out.writeDouble(impulse.x);
                out.writeDouble(impulse.y);
                out.writeDouble(impulse.z);
            }
            // Version 20: the crit flags vanilla raised for the hit. Gated
            // like the impulse: a version 19 take has no such byte per entry.
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
                // Version 18: the line's subtitle text, empty for none. It
                // lives inside the voice section rather than at the end of
                // the file because a caption belongs to its line the way NBT
                // belongs to its equipment entry, and a reader from before
                // version 18 stops at its own gate below instead of meeting
                // a string it has no reason to expect.
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

            // Run-length encoded: a performer holding W steadily for two
            // seconds is one entry rather than forty, expanded again on read.
            // Look deltas break a run because they differ every tick even when
            // the hands are still, which is the common case during a camera
            // pan, so runs are mostly length one in practice.
            //
            // The count written is the number of RUNS, not the number of
            // inputs, because that is what the reader loops over. Writing
            // inputs.size() here made every file with any merging at all
            // over-read its own input block and fail to load.
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

            // Appended last, and gated on version 14, for the reason
            // MIN_SUPPORTED_VERSION documents: this block has to sit after the
            // input runs so that a version 13 reader stops before reaching it
            // rather than consuming four bytes of arrow direction as an input
            // run count.
            out.writeInt(take.shots.size());
            for (ActorShot shot : take.shots) {
                out.writeInt(shot.tick());
                out.writeDouble(shot.dirX());
                out.writeDouble(shot.dirY());
                out.writeDouble(shot.dirZ());
                out.writeFloat(shot.power());
                // The bow that loosed it. Empty for a shot recorded without one,
                // which is the same convention ActorEquip uses for an empty slot,
                // so a take that fires from an unusual source costs four bytes
                // rather than a fixed string on every arrow.
                out.writeUTF(shot.itemId() == null ? "" : shot.itemId());
                boolean hasTarget = shot.target() != null;
                out.writeBoolean(hasTarget);
                if (hasTarget) {
                    out.writeLong(shot.target().getMostSignificantBits());
                    out.writeLong(shot.target().getLeastSignificantBits());
                }
            }

            // The version 15 sections, appended after the shots - the last
            // thing in the file - for the reason MIN_SUPPORTED_VERSION
            // documents: everything already on disk keeps its offset, a
            // reader from before version 15 stops where its format ends
            // rather than meeting a section it has no gate for, and an
            // older build is refused by the version check before it can
            // misread one.
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

            // The version 16 section, appended after the version 15 ones - still
            // the end of the file, so the append-only argument holds unchanged: a
            // reader from before version 16 stops where its format ends, and an
            // older build is refused by the version check before it can misread a
            // section it has no gate for.
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

            // The version 18 section, appended after the Director state - still
            // the end of the file, so the append-only argument holds unchanged:
            // a reader from before version 18 stops where its format ends, and
            // an older build is refused by the version check before it can
            // misread entries it has no gate for.
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

            // Version 21: playback speed, appended at the very end so older
            // readers stop before it and v21 readers default to 1.0 on older files.
            out.writeFloat(take.getSpeedMultiplier());
            // Version 21: hidden ranges, same append. Take-relative like voices.
            out.writeInt(take.hiddenRanges.size());
            for (ActorVisibility h : take.hiddenRanges) {
                out.writeInt(h.startTick());
                out.writeInt(h.endTick());
            }
            // Version 23: the actor's dialogue colour, 0 for the global.
            // Appended last so older files simply end before it.
            out.writeInt(take.speakerColorArgb);
            // Version 25: the track lock flag. One byte at the very end, so
            // older files simply end before it and every older build is
            // refused by the version check before it can misread one.
            out.writeBoolean(take.locked);
            // Version 27: ride transitions (mount/dismount), appended after
            // the lock flag. Older files end before it and load with no
            // rides; older builds refuse the file at the version check.
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
            // Version 28: sound cues, appended after the rides. Older files
            // end before the section and load silent; older builds refuse the
            // file at the version check.
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
            // Version 29: discrete uses, appended after the sounds. Older
            // files end before the section and load with no uses.
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
            // Version 30: placed-entity spawns, appended after the uses.
            // Older files end before the section and load with no spawns.
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
            out.flush();
        }
        try {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Writes the Director config, or a single false byte when there is none.
     *
     * <p>The presence flag rather than a sentinel style: a take that has never
     * been through the Director costs one byte, and the reader never has to decide
     * whether an empty actor list meant "no config" or "a config with nothing
     * selected", which are different states.
     */
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
        // Version 17: the camera move the user chose (-1 for automatic), how long
        // each shot lasts, and the lens height (-1 for the shot's own). All three
        // are read only from a version 17 file, so a version 16 take still loads
        // with automatic move and height and the default hold.
        out.writeInt(config.move() == null ? -1 : config.move().ordinal());
        out.writeInt(config.holdTicks());
        out.writeInt(config.height() == null ? -1 : config.height().ordinal());
        // Version 22: the forced shot type (-1 for automatic), mirroring the
        // move above. Read only from a version 22 file, so older takes load
        // with automatic framing.
        out.writeInt(config.shotType() == null ? -1 : config.shotType().ordinal());
        // Version 24: the explicit shot list, empty for auto-detected moments.
        // Each shot is a subject, three nullable ordinals (-1 for Auto), a hold
        // and a start tick. Read only from a version 24 file; older configs
        // come back with an empty list, which is auto mode.
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

    /**
     * Whether a tick can extend the previous entry's run.
     *
     * <p>The look deltas have to match exactly, not just the buttons. A
     * performer holding W through a camera pan has identical presses on every
     * tick but a different yaw delta each one, and collapsing those into a
     * single entry would throw the pan away and leave the actor walking
     * north for the whole run.
     */
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
        // The axes are the movement, so they sit next to the look deltas that
        // are also continuous quantities, rather than with the flags that are
        // booleans. Everything a run exists to collapse is compared in
   // ActorInput.samePresses, which covers both groups.
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
        // Read off a byte array rather than the file so a legacy take can be
        // parsed twice: the reserved int beside the v5 block is present in
        // some v8-v12 files and absent in others at the same file version, so
        // the migration tries with it and without it. A stream cannot rewind;
        // a ByteArrayInputStream rewinds for free.
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
                if (typeOrdinal >= 0 && typeOrdinal < types.length) {
                    events.add(new ActorEvent(tick, types[typeOrdinal], target));
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
                    // NBT is a version 15 addition to a section that has been
                    // in the file since version 2, so it is gated here rather
                    // than moved to the end of the file: a version 13 or 14
                    // take has no such string per entry, and an ungated read
                    // would consume the hit count that follows as a UTF
                    // length prefix.
                    String nbt = version >= 15 ? in.readUTF() : null;
                    // Empty means "no NBT", the convention itemId uses, so
                    // the round trip does not accumulate empty strings for
                    // plain items.
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
                    // A corrupt or hostile length would otherwise allocate an
                    // arbitrary array, so clamp against the per-take budget.
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
                // An implausible length is dropped rather than thrown on.
                //
                // The obvious guard here is a running budget across all runs,
                // and that is wrong twice over: it is a cumulative counter, so
                // once the take has consumed it every later run fails even
                // though it is individually reasonable; and throwing loses the
                // entire take over one bad entry, which for a file the recorder
                // itself wrote means a bad read takes out good data. The runs
                // are also not required to tile the take, since a run only
                // breaks when the presses or the look deltas change, so their
                // total is not a quantity to cap against the frame count.
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
            // A zero-length run means the reader is out of step
     // with the writer, and every later field would be
         // garbage. Stopping here loses the input track rather
    // than the whole take, which is the recoverable loss.
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

                // No plausibility bound on the count, deliberately. Unlike the
                // voice budget there is no per-entry payload that could be used
                // to allocate, and a count larger than the take has ticks is
                // self-limiting: each iteration allocates one small record, and
                // the file ends sooner than the loop does, so a corrupt count
                // costs an EOF rather than an OutOfMemoryError. The ActorShot
                // constructor is what validates the values.
                take.shots.add(new ActorShot(
                    tick, dx, dy, dz, power, itemId.isEmpty() ? null : itemId, target));
            }
        }

        if (version >= 15) {
            // Block entity edits, container interactions and mining states:
            // the three sections version 15 appended after the shots. One
            // gate covers all three because a file has either all of them or
            // none - they were introduced together, and a take written by an
            // older build ends at the shots above.
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
            // The Director's camera track and the config that produced it. One
            // gate for both, because a version 16 file always has both - the
            // config may be null but its presence byte is always written.
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
                // An out-of-range ordinal is repaired rather than refused, the
                // same way CameraKeyframe repairs an out-of-range easing: a take
                // with a slightly wrong shot type is worth far more than one that
                // refuses to open.
                CameraKeyframe.ShotType shotType = shotOrdinal >= 0 && shotOrdinal < shotTypes.length
                    ? shotTypes[shotOrdinal] : CameraKeyframe.ShotType.MEDIUM;
                take.cameraTrack.add(new CameraKeyframe(
                    tick, x, y, z, yaw, pitch, fov, shotType, target, easing));
            }
            take.directorConfig = readDirectorConfig(in, version);
        }

        if (version >= 18) {
            // Hand-authored captions, appended after the Director state. One
            // gate for the whole section because a file has either all of it
            // or none of it. Counts are bounded like the Director config: a
            // corrupt count here would otherwise ask for millions of small
            // entries before the file ends.
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
            // Playback speed + hidden ranges, appended at the end of the file.
            // Older files default to 1.0x and no hides. Guard against corrupt
            // values: non-finite or non-positive speed falls back to 1.0.
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
                // Version 23: the dialogue colour. A v22 file ends before it
                // and keeps the global default.
                if (version >= 23) {
                    take.speakerColorArgb = in.readInt();
                }
                // Version 25: the track lock flag. Older files end before it
                // and stay unlocked; a truncated v25 file keeps what parsed.
                if (version >= 25) {
                    try {
                        take.locked = in.readBoolean();
                    } catch (EOFException shortFile) {
                        take.locked = false;
                    }
                }
                // Version 27: ride transitions. Older files end before the
                // section and load with no rides.
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
                        // A v27 file truncated after the lock flag still
                        // loads with whatever rides parsed.
                    }
                }
                // Version 28: sound cues. Older files end before the section
                // and load silent.
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
                        // A v28 file truncated after the rides still loads
                        // with whatever cues parsed.
                    }
                }
                // Version 29: discrete uses. Older files end before the
                // section and load with no uses.
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
                        // A v29 file truncated after the sounds still loads
                        // with whatever uses parsed.
                    }
                }
                // Version 30: placed-entity spawns. Older files end before
                // the section and load with no spawns.
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
                        // A v30 file truncated after the uses still loads
                        // with whatever spawns parsed.
                    }
                }
            } catch (EOFException shortFile) {
                // A v21 file truncated after subtitles still loads with
                // defaults rather than losing the whole actor.
            }
        }
      // A file written by a build that appended hits out of order, or one where
        // an actor was struck during a replay and a later write went in behind
     // an earlier one, would leave the list unsorted. Every reader assumes
   // sortedness now, so it is repaired once here rather than defended against at
    // each call site.
   take.sortHits();
   return take;
        }
    }

    /**
     * The file version without reading the take, or -1 when even that fails.
     */
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

    /**
     * Whether the take on disk predates this build and would be migrated on
     * read. False for current files and for anything unreadable (which fails
     * loudly at read time instead).
     */
    public static boolean isLegacy(Path path) {
        int version = peekVersion(path);
        return version >= 0 && version < VERSION;
    }

    /**
     * Reads a take written by version 8 through 12, translating it into the
     * current in-memory shape.
     *
     * <h2>Layouts, as recovered from takes on disk</h2>
     *
     * <p>There is no surviving writer for these versions, so every layout
     * below was reverse-engineered from real files and validated by parsing
     * each one to its exact last byte:
     *
     * <ul>
     *   <li>Header, frames, events and equipment are byte-identical to today,
     *       except equipment entries carry no NBT string.</li>
     *   <li>Hits are {@code int tick, float damage, double dirX, double dirZ}
     *       with no charge and no target - exactly what
     *       {@link ActorHit#legacy} already constructs.</li>
     *   <li>The v5 block (offset, trims, enabled, colour) is followed by a
     *       four-byte reserved int in <em>most</em> v8-v12 files and absent in
     *       others at the same file version, always zero where present. The
     *       parse below tries with it first and without it second; both
     *       attempts are strictly validated, so a wrong guess fails loudly
     *       here rather than loading a shifted take.</li>
     *   <li>Deaths, blocks, voices and voice settings match their current
     *       shapes minus the version-18 caption. Vitals, mining and effects
     *       (v11) match exactly.</li>
     *   <li>Input runs (v12) are 19 bytes: {@code int tick, int extra, short
     *       flags, float yawDelta, float pitchDelta, byte slot}. Each run
     *       covers ticks {@code tick} through {@code tick + extra} - the
     *       totals tile the take's frames exactly - and there is one record
     *       per stretch of identical presses rather than per tick. The run
     *       count header is the tick count, not the run count, so the reader
     *       parsers runs to end-of-file instead of trusting it.</li>
     * </ul>
     *
     * <h2>The flag bits</h2>
     *
     * <p>The old press flags are the current six shifted left by four:
     * jump 16, sneak 32, sprint 64, use 128, attack-held 256, flying 512.
     * Solved by correlating flag values against the frames of real takes: a
     * run flagged 64 sits on sprinting frames moving at sprint speed, 256 on
     * swinging frames, 32 on sneaking frames, every time, with no
     * counter-examples. Sprint additionally implies full forward deflection,
     * because vanilla only sprints a body that is moving forward; there is no
     * separate direction bit surviving anywhere, so every other run replays
     * at zero deflection and the simulation corrects toward the frames.
     *
     * <h2>What is approximated, and said aloud</h2>
     *
     * <p>Predicted damage is taken on trust; charge is full; axes are full
     * deflection or zero; equipment slots past the six a player has are
     * dropped; trims are clamped into the frames. Every one of these is a
     * note on the migration log line, because a take that loaded with silent
     * substitutions is a take nobody can reason about later.
     */
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

    /** One migration attempt: with the reserved int, or without it. */
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
                // Slots past the six a player clone has (saddle, body) were
                // recorded by ordinal and have nowhere to go on this build.
                // Dropped rather than remapped: guessing a slot for them would
                // dress the wrong body part, and the loadout ignores out-of-range
                // slots anyway, so keeping them would only bloat the re-save.
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
                // A take from a build whose hit layout differs from the one
                // recovered here would decode to wild ticks. Dropping the hits
                // keeps the actor; failing the take would delete it over data
                // the health model treats as advisory anyway.
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
            // A negative trim reaches before the first frame, which the trim
            // math never expected: takes on disk carry one. Clamped into the
            // frames rather than trusted.
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
                // The header counts ticks, not runs, so runs are parsed to
                // end-of-file instead of trusting it. A run that fails its
                // sanity ends the track rather than the take: the frames path
                // plays whatever parsed, which degrades toward standing still
                // instead of refusing the actor.
                int runs = 0;
                while (in.available() >= 19) {
                    int tick = in.readInt();
                    int extra = in.readInt();
                    int flags = in.readShort();
                    float yawDelta = in.readFloat();
                    float pitchDelta = in.readFloat();
                    byte slot = in.readByte();
                    // A run that fails sanity ends the attempt, not just the
                    // track: unlike the count header there is no way to
                    // resynchronize past it, so anything after it is
                    // unparseable by construction. Ticks need not be ordered;
                    // the track is sorted below exactly like the current one.
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

            // A legacy file ends where its newest section ends. Anything left
            // over means the layout guess was wrong somewhere above, and
            // loading on would silently misread the next file format bump.
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

    /**
     * The v12 press flags, which are the current six shifted left by four.
     * Solved empirically (see {@link #readLegacy}): jump 16, sneak 32,
     * sprint 64, use 128, attack-held 256, flying 512.
     */
    private static final int LEGACY_JUMP = 16;
    private static final int LEGACY_SNEAK = 32;
    private static final int LEGACY_SPRINT = 64;
    private static final int LEGACY_USE = 128;
    private static final int LEGACY_ATTACK = 256;
    private static final int LEGACY_FLYING = 512;

    /**
     * Reads the Director config, or null when the presence flag says there is
     * none.
     *
     * <p>The actor count is bounded rather than trusted. Unlike the shot count,
     * which is self-limiting because each entry's payload makes the loop run out
     * of file before it runs out of memory, a config's entries are small and a
     * corrupt count could ask for millions of them before the file ends. The
     * bound is generous - no camera is built for more than a handful of actors -
     * so a legitimate file can never reach it.
     */
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

        // The move, shot length and height, from version 17 on. A version 16 file
        // ends here, so its config comes back automatic rather than a truncated
        // read.
        //
        // The read is defensive for a second reason: version 17 briefly existed in
        // a build that wrote only the first two of these fields, before the lens
        // height was added, so a file from that build ends one int early. Swallowing
        // the short read keeps whatever was read and leaves the rest at its default
        // - and because "no choice" is encoded as -1, the recovered config comes
        // back automatic, which is the honest answer for a file written before the
        // setting existed. The alternative, letting the exception out, discards the
        // whole take: the actor, their frames and their recording, because of two
        // missing bytes at the end of the file.
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
        // Version 22: the forced shot type, appended after the move trio. A
        // version 21 file ends before it and comes back automatic, like the
        // move and height before it.
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

    /**
     * Reads the explicit shot list, or empty when the file predates it.
     *
     * <p>Bounded like the actor list: each entry is small and a corrupt count
     * could otherwise ask for millions before the file ends. Entries naming
     * the same subject twice are kept - the list is a sequence, not a set.
     */
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
                    // A null subject cannot happen from two longs; defensive
                    // anyway, so one bad row costs one shot, not the take.
                }
            }
        } catch (EOFException shortFile) {
            // A v24 file truncated inside the list keeps what parsed.
        }
        return shots;
    }

    @Nullable
    private static CameraKeyframe.ShotType shotByOrdinal(int ordinal) {
        CameraKeyframe.ShotType[] types = CameraKeyframe.ShotType.values();
        return ordinal >= 0 && ordinal < types.length ? types[ordinal] : null;
    }
}
