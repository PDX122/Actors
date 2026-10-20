package pdx.dev.fbactors.take;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActorTake {

    public final UUID id;
    public String name;

    public String skinName;

    public String dimension;

    public int startTick;

    public final List<ActorFrame> frames;
    public final List<ActorEvent> events;

    public final List<ActorEquip> equipment;

    public final List<ActorHit> hits;

    public final List<ActorDeath> deaths;

    public final List<ActorBlockEdit> blocks;

    public final List<ActorVoiceLine> voices;

    /**
     * Spans of this take during which the actor is hidden, in take ticks.
     *
     * <p>Take-relative like {@link #voices}: a hide cue belongs to the body
     * it hides and moves with the clip when it is dragged.
     */
    public final List<ActorVisibility> hiddenRanges;

    /**
     * Hand-authored captions on this take, in take ticks.
     *
     * <p>Take-relative like {@link #voices}, {@link #hits} and
     * {@link #deaths}: a line belongs to the body that speaks it and moves
     * with the clip when it is dragged, exactly as a punch does.
     */
    public final List<ActorSubtitle> subtitles;

    public final List<ActorVitals> vitals;

    public final List<ActorMining> mining;

    public final List<ActorEffect> effects;

    /**
     * Every arrow the performer loosed, as 0-based frame indices.
     *
     * <p>Indexed like {@link #frames} and read through the
     * {@code takeTick - startTick} conversion in {@link #shotsAt}, not through
     * {@link #recordedTick} like {@link #events} or {@link #inputs}. A shot is
     * a discrete thing that happened at a moment, and dragging the clip along
     * the timeline carries it with the body that loosed it: frame index
     * {@code i} is shown at take tick {@code startTick + i} wherever the clip
     * sits. See {@link ActorShot} for why the velocity here is measured off
     * vanilla's own arrow rather than re-derived from a charge curve.
     */
    public final List<ActorShot> shots;

    /**
     * What the performer pressed, per tick. This is what actually drives
     * playback; {@link #frames} is kept as a baseline to fall back on and to
     * check the simulation against.
     */
    public final List<ActorInput> inputs;

    /**
     * Block-entity data changes the performer caused, in take ticks.
     *
     * <p>Indexed take-relative like {@link #equipment} and {@link #vitals},
     * and read through {@link #recorded} like the rest of the absolute-tick
     * tracks. Each entry is a full NBT snapshot of a block entity after the
     * performer changed it - a sign they edited, a chest they looted, a banner
     * they re-patterned. See {@link ActorBlockEntityEdit} for why this is a
     * separate track from {@link #blocks} and why the NBT is stored as an
     * opaque string.
     */
    public final List<ActorBlockEntityEdit> blockEntities;

    /**
     * Container interaction events the performer caused, in take ticks.
     *
     * <p>Indexed take-relative and read through {@link #recorded}. Each entry
     * is a discrete event - the performer opened a container, closed one, or
     * changed a slot within one. See {@link ActorContainerInteraction} for
     * why this is separate from {@link #blockEntities} and why open/close
     * events need their own track.
     */
    public final List<ActorContainerInteraction> containers;

    /**
     * Mounts and dismounts the performer did, in absolute replay ticks.
     *
     * <p>Indexed take-relative and read through {@link #recorded} like
     * {@link #containers}. Each entry is a discrete transition, not a state
     * snapshot: a mount names the vehicle, a dismount just gets off. See
     * {@link ActorRide} for why the input track cannot express this.
     */
    public final List<ActorRide> rides;

    /**
     * Sounds the performer's actions caused, in absolute replay ticks.
     *
     * <p>Indexed take-relative and read through {@link #recorded} like
     * {@link #containers}. Each entry is a discrete broadcast replayed at
     * its tick. See {@link ActorSound} for why the broadcast is recorded
     * rather than re-derived.
     */
    public final List<ActorSound> sounds;

    /**
     * Continuous mining state snapshots, in take ticks.
     *
     * <p>Indexed take-relative and read through {@link #recorded}. Each entry
     * is a snapshot of the performer's mining state at a tick - which block
     * they were mining, what crack stage was displayed, and whether their arm
     * was swinging. See {@link ActorMiningState} for why this is separate from
     * {@link #mining} and why a snapshot is needed alongside the event log.
     */
    public final List<ActorMiningState> miningStates;

    /**
     * The camera the Director generated for this take, in replay ticks.
     *
     * <p>This is the Director's own record of what it built, kept alongside the
     * keyframes it wrote into Flashback's camera track. The two are not redundant.
     * Flashback's track is the thing the editor, the playback and the export
     * actually read, and it is fully editable - a user can move, split or delete
     * any of it. This record is what the Director produced, so that a take read
     * back from disk can be previewed without an editor state, and so a
     * regeneration has something to compare against. It is deliberately never read
     * back into Flashback's track automatically: doing so would silently undo the
     * user's edits every time the replay opened.
     */
    public final CameraTrack cameraTrack = new CameraTrack();

    /**
     * The settings that produced {@link #cameraTrack}, or null when this take has
     * never been through the Director.
     *
     * <p>Kept because the generated keyframes say nothing about <em>why</em> they
     * are where they are. Without this, reopening a replay shows a camera the user
     * cannot explain and cannot regenerate; with it, the Director menu
     * pre-populates with the same actor selection and style and the same camera
     * can be rebuilt. See {@link DirectorConfig}.
     */
    @Nullable
    public DirectorConfig directorConfig;

    /** Whether this actor should be recorded and played back with voice. */
    public boolean voiceEnabled = false;

    /** OpenAL capture device name, null for the system default. */
    public String voiceDevice;

    /** Whether capture is gated on voice activity rather than always on. */
    public boolean voiceActivation = true;

    /** dB threshold applied when voiceActivation is on, -60..-20. */
    public int voiceThreshold = -45;

    public int offsetTicks = 0;
    public int trimIn = 0;
    public int trimOut = 0;
    public boolean enabled = true;
    public int colour = 0;

    /**
     * Whether this track is locked against timeline edits.
     *
     * <p>Lock is an editor-only guard: drag-to-retime, trim, razor, ripple and
     * keyframe paste all refuse a locked take, while playback, export and
     * persistence ignore it. It defaults to unlocked and persists in the take
     * file (format v25) so a locked layout survives a reload.
     */
    public boolean locked = false;

    /**
     * This actor's dialogue colour as ARGB, or 0 to follow the global subtitle colour.
     *
     * <p>Used for voice captions and for hand-authored lines that do not set their
     * own colour: the speaker reads in their colour without every line carrying
     * it. Per-line colours still win when set.
     */
    public int speakerColorArgb = 0;

    /**
     * How fast this take plays back relative to real-time.
     *
     * <p>A multiplier of 1.0 is normal speed: the actor advances one take tick
     * per replay tick. A multiplier of 2.0 fast-forwards through the take at
     * double speed - the actor advances two take ticks per replay tick - which
     * is useful for skimming long recordings. A multiplier of 0.5 slows playback
     * to half speed for dramatic effect.
     *
     * <p>This is a per-actor playback speed, not a replay speed. The replay
     * itself always advances at one tick per server tick; the multiplier only
     * affects how far into the take the actor has progressed. Two actors in the
     * same replay can play at different speeds without affecting each other or
     * the replay timeline.
     *
     * <p>The alternative - scaling the replay tick before computing the take
     * tick - was rejected because it would couple actor playback speed to the
     * replay's own tick rate, making it impossible to fast-forward one actor
     * without fast-forwarding everything. Keeping the multiplier on the take
     * means the actor's position is computed as
     * {@code (replayTick - offset) * speed}, which preserves the offset
     * semantics while allowing independent speed control.
     *
     * <p>A multiplier of 0 freezes the actor in place. Negative multipliers
     * are not clamped and will cause the actor to play backwards, which is
     * not a supported use case and may produce undefined behaviour in
     * {@code ActorManager.applyOne}.
     */
    private float speedMultiplier = 1.0f;

    public static final float MAX_HEALTH = 20.0f;

    public static final int DEATH_LINGER_TICKS = 30;

    public ActorTake(UUID id, String name, String dimension, int startTick) {
        this(id, name, dimension, startTick, new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>());
    }

    public ActorTake(UUID id, String name, String dimension, int startTick,
                     List<ActorFrame> frames, List<ActorEvent> events,
                     List<ActorEquip> equipment, List<ActorHit> hits,
                     List<ActorDeath> deaths) {
        this(id, name, dimension, startTick, frames, events, equipment, hits, deaths,
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>());
    }

    public ActorTake(UUID id, String name, String dimension, int startTick,
                     List<ActorFrame> frames, List<ActorEvent> events,
                     List<ActorEquip> equipment, List<ActorHit> hits,
                     List<ActorDeath> deaths, List<ActorBlockEdit> blocks,
                     List<ActorVoiceLine> voices, List<ActorVitals> vitals,
                     List<ActorMining> mining, List<ActorEffect> effects,
                     List<ActorShot> shots,
                     List<ActorBlockEntityEdit> blockEntities,
                     List<ActorContainerInteraction> containers,
                     List<ActorMiningState> miningStates) {
        this(id, name, dimension, startTick, frames, events, equipment, hits, deaths, blocks,
            voices, vitals, mining, effects, shots, blockEntities, containers, miningStates,
            new ArrayList<>(), new ArrayList<>());
    }

    public ActorTake(UUID id, String name, String dimension, int startTick,
                     List<ActorFrame> frames, List<ActorEvent> events,
                     List<ActorEquip> equipment, List<ActorHit> hits,
                     List<ActorDeath> deaths, List<ActorBlockEdit> blocks,
                     List<ActorVoiceLine> voices, List<ActorVitals> vitals,
                     List<ActorMining> mining, List<ActorEffect> effects,
                     List<ActorShot> shots,
                     List<ActorBlockEntityEdit> blockEntities,
                     List<ActorContainerInteraction> containers,
                     List<ActorMiningState> miningStates,
                     List<ActorSubtitle> subtitles) {
        this(id, name, dimension, startTick, frames, events, equipment, hits, deaths, blocks,
            voices, vitals, mining, effects, shots, blockEntities, containers, miningStates,
            subtitles, new ArrayList<>(), new ArrayList<>());
    }

    public ActorTake(UUID id, String name, String dimension, int startTick,
                     List<ActorFrame> frames, List<ActorEvent> events,
                     List<ActorEquip> equipment, List<ActorHit> hits,
                     List<ActorDeath> deaths, List<ActorBlockEdit> blocks,
                     List<ActorVoiceLine> voices, List<ActorVitals> vitals,
                     List<ActorMining> mining, List<ActorEffect> effects,
                     List<ActorShot> shots,
                     List<ActorBlockEntityEdit> blockEntities,
                     List<ActorContainerInteraction> containers,
                     List<ActorMiningState> miningStates,
                     List<ActorSubtitle> subtitles,
                     List<ActorVisibility> hiddenRanges) {
        this(id, name, dimension, startTick, frames, events, equipment, hits, deaths, blocks,
            voices, vitals, mining, effects, shots, blockEntities, containers, miningStates,
            subtitles, hiddenRanges, new ArrayList<>());
    }

    public ActorTake(UUID id, String name, String dimension, int startTick,
                     List<ActorFrame> frames, List<ActorEvent> events,
                     List<ActorEquip> equipment, List<ActorHit> hits,
                     List<ActorDeath> deaths, List<ActorBlockEdit> blocks,
                     List<ActorVoiceLine> voices, List<ActorVitals> vitals,
                     List<ActorMining> mining, List<ActorEffect> effects,
                     List<ActorShot> shots,
                     List<ActorBlockEntityEdit> blockEntities,
                     List<ActorContainerInteraction> containers,
                     List<ActorMiningState> miningStates,
                     List<ActorSubtitle> subtitles,
                     List<ActorVisibility> hiddenRanges,
                     List<ActorRide> rides) {
        this(id, name, dimension, startTick, frames, events, equipment, hits, deaths, blocks,
            voices, vitals, mining, effects, shots, blockEntities, containers, miningStates,
            subtitles, hiddenRanges, rides, new ArrayList<>());
    }

    public ActorTake(UUID id, String name, String dimension, int startTick,
                     List<ActorFrame> frames, List<ActorEvent> events,
                     List<ActorEquip> equipment, List<ActorHit> hits,
                     List<ActorDeath> deaths, List<ActorBlockEdit> blocks,
                     List<ActorVoiceLine> voices, List<ActorVitals> vitals,
                     List<ActorMining> mining, List<ActorEffect> effects,
                     List<ActorShot> shots,
                     List<ActorBlockEntityEdit> blockEntities,
                     List<ActorContainerInteraction> containers,
                     List<ActorMiningState> miningStates,
                     List<ActorSubtitle> subtitles,
                     List<ActorVisibility> hiddenRanges,
                     List<ActorRide> rides,
                     List<ActorSound> sounds) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.startTick = startTick;
        this.frames = frames;
        this.events = events;
        this.equipment = equipment;
        this.hits = hits;
        this.deaths = deaths;
        this.blocks = blocks;
        this.voices = voices;
        this.vitals = vitals;
        this.mining = mining;
        this.effects = effects;
        this.shots = shots;
        this.blockEntities = blockEntities;
        this.containers = containers;
        this.miningStates = miningStates;
        this.subtitles = subtitles;
        this.hiddenRanges = hiddenRanges;
        this.rides = rides;
        this.sounds = sounds;
        this.inputs = new ArrayList<>();
    }

    /**
     * The recorded tick a take-relative tick corresponds to on the timeline.
     *
     * <p>{@code inputs}, {@code events}, {@code equipment}, {@code vitals},
     * {@code effects}, {@code mining} and {@code blocks} are all written by the
     * recorder in <em>absolute replay tick</em>, but every caller reads them
     * with a <em>take tick</em> because that is what
     * {@code Actor.takeTickFor} produces. The two only coincide while the clip
     * sits at offset zero, which is why dragging a clip along the timeline made
     * an actor's input fire at the wrong moment relative to its body.
     *
     * <p>{@code frames} does not need this: it is index anchored and already
     * honours the offset. Neither do {@code hits} or {@code deaths}, which are
     * written in take ticks.
     */
    public int recordedTick(int takeTick) {
        return takeTick + this.offsetTicks;
    }

    private int recorded(int takeTick) {
        return recordedTick(takeTick);
    }

    /**
     * Sets the playback speed multiplier for this take.
     *
     * <p>The multiplier is applied by {@code ActorManager.advanceTicks} when
     * computing the actor's take tick from the replay tick. A value of 1.0
     * preserves normal playback; 2.0 doubles the actor's advance rate; 0.5
     * halves it.
     *
     * @param speed the new speed multiplier; 1.0 is normal, 2.0 is double
     *              speed, 0.5 is half speed. A value of 0 freezes playback.
     * @see #getSpeedMultiplier()
     * @see pdx.dev.fbactors.actor.ActorManager#advanceTicks(int, float)
     */
    public void setSpeedMultiplier(float speed) {
        this.speedMultiplier = speed;
    }

    /**
     * The current playback speed multiplier for this take.
     *
     * @return the speed multiplier; 1.0 is normal speed.
     * @see #setSpeedMultiplier(float)
     */
    public float getSpeedMultiplier() {
        return this.speedMultiplier;
    }

    /**
     * The input recorded for this tick, or null if the performer held nothing
     * and the take has no run-length entry covering it.
     */
    @Nullable
    public ActorInput inputAt(int takeTick) {
        int wanted = recorded(takeTick);
        ActorInput best = null;
        for (ActorInput input : this.inputs) {
            if (input.tick() > wanted) {
                break;
            }
            best = input;
        }
        return best;
    }

    /**
     * The recorded attack fired on a take tick, or null.
     *
     * <p>Events are in absolute replay ticks while take ticks are relative to
     * {@link #startTick}, so this converts first. Getting that conversion wrong is
     * silent: the swing simply never fires, or fires on the wrong tick, and an
     * actor that punched something in the recording walks into the wall instead.
     *
     * <p>Several events can share a tick - a swing, an item use and an attack all
     * fired by the same input - so this returns the attack specifically rather
     * than "an event", and the first one wins when two attacks somehow collided.
     */
    @Nullable
    public ActorEvent attackAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorEvent event : this.events) {
            if (event.type() == ActorEvent.Type.ATTACK && event.tick() == wanted) {
                return event;
            }
        }
        return null;
    }

    /** Whether this take drives its actors from input rather than from frames. */
    public boolean simulated() {
        return !this.inputs.isEmpty();
    }

    /** The tick the last input entry ends on, or -1 when there is none. */
    public int lastInputTick() {
        int last = -1;
        for (ActorInput input : this.inputs) {
            if (input.tick() > last) {
                last = input.tick();
            }
        }
        return last;
    }

    public ActorEquip[] equipmentAt(int takeTick, int slotCount) {
        int wanted = recorded(takeTick);
        ActorEquip[] loadout = new ActorEquip[slotCount];
        for (ActorEquip change : this.equipment) {
            if (change.tick() > wanted) {
                break;
            }
            int slot = change.slot();
            if (slot >= 0 && slot < slotCount) {
                loadout[slot] = change.isEmpty() ? null : change;
            }
        }
        return loadout;
    }

    /**
     * Records a hit, keeping {@link #hits} ordered by tick.
     *
     * <p>{@code hits} used to be appended to directly and every reader
     * assumed it was sorted. It was not, because a viewer landing a punch during
     * a replay or a scrub can hit a take tick earlier than one already recorded,
     * and an append puts it at the end. {@code healthAt} then subtracted the hits
     * in the wrong order, which for a lethal sequence means the actor's death
     * moved to a different tick depending on playback history.
     *
     * <p>Inserting in place costs a scan but there are only ever as many hits as
     * the take contains punches, and it makes the ordering an invariant of the
     * collection rather than a property every caller has to remember.
     */
    public void recordHit(ActorHit hit) {
        // Exactly one entry per tick, and the tick holds whichever measurement
        // came last. Two writers reach this - the viewer's own strike, and a swing
        // re-delivered out of another actor's take - and both can land on the same
        // tick, so two entries at one tick are two numbers for one hit. healthAt
        // adds them both up, and the actor then dies at a tick nothing killed it
        // on.
        //
        // Both walks below have to go back over the *whole* run of entries at the
        // tick. The scan used to stop at the first entry it met, which replaced
        // the one already there only when the tick being written was the last in
        // the list. A tick that already held two entries therefore gained a third,
        // and a fourth, and one more on every pass: the take this was written for
        // had reached 133 points of recorded damage against a twenty-health actor,
        // and the tick its actor died on walked earlier with every playback. That
        // is the whole of the desync between what was fought and what an export
        // shows.
        int index = this.hits.size();
        while (index > 0 && this.hits.get(index - 1).tick() > hit.tick()) {
            index--;
        }
        while (index > 0 && this.hits.get(index - 1).tick() == hit.tick()) {
            this.hits.remove(index - 1);
            index--;
        }
        this.hits.add(index, hit);
    }

    /**
     * The hit recorded against a tick, or null when nothing landed on it.
     *
     * <p>{@link #recordHit} keeps the list in tick order, so the walk stops at
     * the first hit in the future rather than scanning to the end.
     *
     * <p>This is what lets a fight be replayed rather than merely re-walked. The
     * input track says what the performer tried to do and can never say what was
     * done to them, so the tick a punch landed on and the direction it shoved in
     * are only recoverable from here. See
     * {@link pdx.dev.fbactors.actor.InputSim#replayStrike}, which is the reader.
     */
    @Nullable
    public ActorHit hitAt(int replayTick) {
        for (ActorHit hit : this.hits) {
            if (hit.tick() == replayTick) {
                return hit;
            }
            if (hit.tick() > replayTick) {
                break;
            }
        }
        return null;
    }

    /**
     * Whether this take already describes a hit on a tick.
     *
     * <p>Used to keep a replayed swing from being measured a second time. A
     * swing that a take's own attack event re-delivers produces the same hit on
     * every pass, and re-recording it meant the damage total - and with it the
     * tick the actor was killed on - moved every time the take was played.
     */
    public boolean hasHitAt(int replayTick) {
        return hitAt(replayTick) != null;
    }

    /**
     * The health this actor's own recorded performance had at a tick, before
     * anything that happened to it during playback.
     *
     * <p>This is the <em>baseline</em>, and it is only correct while the take is
     * authoritative. Once the actor has been struck, the simulation owns its
     * health and this number describes a performance that is no longer
     * happening, so writing it back onto the clone would undo the hit. See
     * {@link pdx.dev.fbactors.actor.InputSim#usesRecordedBaseline}.
     */
    public float healthAt(int replayTick) {
        float health = MAX_HEALTH;
        for (ActorHit hit : this.hits) {
            if (hit.tick() <= replayTick) {
                health -= hit.damage();
            } else {
                // Sorted, so the first hit in the future ends the walk.
                break;
            }
        }
        return Mth.clamp(health, 0.0f, MAX_HEALTH);
    }

    public int deathTick() {
        int earliest = -1;

        // Safe to walk directly: recordHit keeps the list in tick order and
        // sortHits repairs one that came off disk, so there is nothing to sort
        // a copy of here any more. Sorting a copy every call was what made this
        // look safe to leave unsorted in the first place.
        float health = MAX_HEALTH;
        for (ActorHit hit : this.hits) {
            health -= hit.damage();
            if (health <= 0.0f) {
                earliest = hit.tick();
                break;
            }
        }

        for (ActorDeath death : this.deaths) {
            if (earliest < 0 || death.tick() < earliest) {
                earliest = death.tick();
            }
        }
        return earliest;
    }

    /**
     * Puts the hit list back into tick order, and back to one entry per tick.
     *
     * <p>Called once after a take is loaded, because a file can have been
     * written by a build that appended out of order, and every reader assumes
     * sortedness.
     *
     * <p>The collapsing half is the repair for a take written before
     * {@link #recordHit} learned to replace across a whole tick. Such a take
     * holds several entries on one tick and {@code healthAt} adds every one of
     * them up: the take this was written for read 133 points of damage against a
     * twenty-health actor, so its actor died long before anything killed it, and
     * the tick it died on moved with every playback. Repairing it here rather
     * than only preventing new damage means a take already on disk comes back as
     * the fight that was actually fought.
     *
     * <p>The earliest entry at a tick is the one kept. Every later one is a
     * re-measurement taken while the simulation was already diverging, and the
     * first is the one that describes what the performer took. That depends on
     * the sort being stable, which {@code List.sort} is: entries with equal ticks
     * keep the order they were written in, and within a tick they were written in
     * measurement order.
     */
    public void sortHits() {
        this.hits.sort(Comparator.comparingInt(ActorHit::tick));

        List<ActorHit> collapsed = new ArrayList<>(this.hits.size());
        int lastTick = Integer.MIN_VALUE;
        for (ActorHit hit : this.hits) {
            if (hit.tick() == lastTick) {
                continue;
            }
            collapsed.add(hit);
            lastTick = hit.tick();
        }

        if (collapsed.size() != this.hits.size()) {
            this.hits.clear();
            this.hits.addAll(collapsed);
        }
    }

    @Nullable
    public ActorDeath deathAt(int takeTick) {
        for (ActorDeath death : this.deaths) {
            if (death.tick() == takeTick) {
                return death;
            }
        }
        return null;
    }

    public boolean hasEquipmentChangeAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorEquip change : this.equipment) {
            if (change.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

    public int length() {
        return this.frames.size();
    }

    public int endTick() {
        return this.startTick + this.frames.size() - 1;
    }

    public ActorFrame frameAt(int replayTick) {
        if (this.frames.isEmpty()) {
            return null;
        }
        int index = replayTick - this.startTick;
        if (index < 0) {
            index = 0;
        } else if (index >= this.frames.size()) {
            index = this.frames.size() - 1;
        }
        return this.frames.get(index);
    }

    public int firstTick() {
        return this.startTick + Math.max(0, this.trimIn);
    }

    public int lastTick() {
        return Math.max(firstTick(), endTick() - Math.max(0, this.trimOut));
    }

    public boolean coversTick(int replayTick) {
        return !this.frames.isEmpty()
            && replayTick >= firstTick()
            && replayTick <= lastTick();
    }

    public void resetTrim() {
        this.trimIn = 0;
        this.trimOut = 0;
    }

    public ActorTake copyAs(UUID newId, String newName) {
        ActorTake copy = new ActorTake(newId, newName, this.dimension, this.startTick,
            new ArrayList<>(this.frames), new ArrayList<>(this.events),
            new ArrayList<>(this.equipment), new ArrayList<>(this.hits),
            new ArrayList<>(this.deaths),
            new ArrayList<>(this.blocks), new ArrayList<>(this.voices),
            new ArrayList<>(this.vitals), new ArrayList<>(this.mining),
            new ArrayList<>(this.effects), new ArrayList<>(this.shots),
            new ArrayList<>(this.blockEntities), new ArrayList<>(this.containers),
            new ArrayList<>(this.miningStates), new ArrayList<>(this.subtitles),
            new ArrayList<>(this.hiddenRanges), new ArrayList<>(this.rides),
            new ArrayList<>(this.sounds));
        copy.skinName = this.skinName;
        copy.offsetTicks = this.offsetTicks;
        copy.trimIn = this.trimIn;
        copy.trimOut = this.trimOut;
        copy.speakerColorArgb = this.speakerColorArgb;
        copy.enabled = this.enabled;
        copy.locked = this.locked;
        copy.colour = this.colour;
        copy.voiceEnabled = this.voiceEnabled;
        copy.voiceDevice = this.voiceDevice;
        copy.voiceActivation = this.voiceActivation;
        copy.voiceThreshold = this.voiceThreshold;
        copy.speedMultiplier = this.speedMultiplier;
        copy.inputs.addAll(this.inputs);
        // The Director state is copied rather than shared, so editing the
        // duplicate's camera cannot reach back and change the original's.
        copy.cameraTrack.replaceWith(this.cameraTrack);
        copy.directorConfig = this.directorConfig;
        return copy;
    }

    public List<ActorEvent> eventsAt(int replayTick) {
        List<ActorEvent> out = new ArrayList<>();
        for (ActorEvent event : this.events) {
            if (event.tick() == recorded(replayTick)) {
                out.add(event);
            }
        }
        return out;
    }

    /**
     * The last recorded health and absorption at or before this tick, or null
     * if the performance never took damage or drank anything.
     */
    @Nullable
    public ActorVitals vitalsAt(int takeTick) {
        int wanted = recorded(takeTick);
        ActorVitals latest = null;
        for (ActorVitals sample : this.vitals) {
            if (sample.tick() > wanted) {
                break;
            }
            latest = sample;
        }
        return latest;
    }

    /**
     * Every arrow loosed on the frame shown at this take tick.
     *
     * <p>Shots are stored 0-based (frame indices, as {@code TakeRecorder}
     * files them: {@code replayTick - startTick}), while this lookup arrives
     * in take ticks ({@code replayTick - offsetTicks}). Frame index {@code i}
     * is shown at take tick {@code startTick + i}, hence the conversion.
     * Comparing the raw take tick instead never matches for any take recorded
     * past replay tick zero, which is every real take: arrows silently never
     * replayed.
     *
     * <p>Matching on equality rather than accumulating up to the tick is
     * deliberate - an arrow belongs to the tick it left the bow, and a
     * crossbow can loose more than one on that tick, so this returns a list
     * rather than the single shot {@link #deathAt} offers.
     *
     * <p>The empty case returns the shared empty list rather than a fresh
     * allocation, because this is called once per actor per tick during playback
     * and the overwhelming majority of ticks have no shot on them.
     */
    public List<ActorShot> shotsAt(int takeTick) {
        if (this.shots.isEmpty()) {
            return Collections.emptyList();
        }
        int wanted = takeTick - this.startTick;
        List<ActorShot> out = new ArrayList<>();
        for (ActorShot shot : this.shots) {
            if (shot.tick() == wanted) {
                out.add(shot);
            }
        }
        return out;
    }

    /** Every block-break progress step the actor reached up to this tick. */
    public List<ActorMining> miningAt(int takeTick) {
        if (this.mining.isEmpty()) {
            return Collections.emptyList();
        }
        int wanted = recorded(takeTick);
        List<ActorMining> out = new ArrayList<>();
        for (ActorMining step : this.mining) {
            if (step.tick() > wanted) {
                break;
            }
            out.add(step);
        }
        return out;
    }

    /**
     * Which hotbar slot the performer had selected, or -1 when the take has no
     * input track and so never recorded one.
     */
    public byte selectedSlotAt(int replayTick) {
        ActorInput input = inputAt(replayTick);
        return input == null ? (byte) -1 : input.selectedSlot();
    }

    /**
     * The set of effects the actor is under at this tick, keyed by effect id.
     * Latest entry per id wins, and ids that stopped being recorded are simply
     * absent, which is how a finished potion reads as gone.
     */
    public Map<String, ActorEffect> effectSetAt(int takeTick) {
        int wanted = recorded(takeTick);
        Map<String, ActorEffect> active = new LinkedHashMap<>();
        for (ActorEffect effect : this.effects) {
            if (effect.tick() > wanted) {
                break;
            }
            active.put(effect.effectId(), effect);
        }
        return active;
    }

    /**
     * The latest block-entity edit at or before this tick, keyed by position.
     *
     * <p>Block-entity edits are stored in absolute replay tick (like
     * {@link #equipment} and {@link #vitals}), so the take tick is converted
     * through {@link #recorded} before the walk. The walk accumulates the
     * latest entry per position, so a sign that was edited three times during
     * a looting session appears once, with its final text.
     *
     * <p>The map is keyed by {@link BlockPos} rather than by a composite
     * {@code x,y,z} key because {@link BlockPos} is what vanilla's own
     * block-entity lookup uses, and a reader that wants to apply these edits
     * to a world will need to look up block entities by position anyway. A
     * composite key would force every reader to decompose it and reconstruct
     * a {@link BlockPos} before it could do anything useful.
     *
     * <p>The returned map is a fresh {@link LinkedHashMap} so that callers
     * can iterate it in insertion order (which is tick order, because the
     * list is sorted) without holding a reference into the take. An empty
     * take returns an empty map, never null.
     */
    public Map<BlockPos, ActorBlockEntityEdit> blockEntityAt(int takeTick) {
        int wanted = recorded(takeTick);
        Map<BlockPos, ActorBlockEntityEdit> latest = new LinkedHashMap<>();
        for (ActorBlockEntityEdit edit : this.blockEntities) {
            if (edit.tick() > wanted) {
                break;
            }
            latest.put(edit.pos(), edit);
        }
        return latest;
    }

    /**
     * Every container interaction that happened on this tick.
     *
     * <p>Container interactions are discrete events, not desired-state
     * snapshots, so this returns all entries at exactly the given tick rather
     * than the latest at or before it. A performer can open a chest, change
     * a slot, and close it all on the same tick, and all three events must
     * be replayed in order for the container to end up in the right state.
     *
     * <p>The take tick is converted through {@link #recorded} because
     * container interactions are stored in absolute replay tick, like
     * {@link #equipment} and {@link #vitals}. The returned list is a fresh
     * {@link ArrayList}; an empty take returns an empty list, never null.
     */
    public List<ActorContainerInteraction> containerAt(int takeTick) {
        int wanted = recorded(takeTick);
        List<ActorContainerInteraction> out = new ArrayList<>();
        for (ActorContainerInteraction interaction : this.containers) {
            if (interaction.tick() == wanted) {
                out.add(interaction);
            }
        }
        return out;
    }

    /**
     * The latest mining state at or before this tick, or null if the performer
     * was never mining.
     *
     * <p>Mining states are desired-state snapshots, so this returns the latest
     * entry at or before the playhead rather than every entry up to it. This
     * is the same pattern as {@link #vitalsAt} and {@link #inputAt}: playback
     * applies the latest known state and lets it hold until a newer entry
     * arrives, which means a scrub backwards and forwards through a mining
     * session converges on the correct crack stage and arm animation without
     * replaying every intermediate snapshot.
     *
     * <p>The take tick is converted through {@link #recorded} because mining
     * states are stored in absolute replay tick. Returns null when the take
     * has no mining state entries at or before this tick, which means the
     * performer was not mining at this point in the recording.
     */
    @Nullable
    public ActorMiningState miningStateAt(int takeTick) {
        int wanted = recorded(takeTick);
        ActorMiningState latest = null;
        for (ActorMiningState state : this.miningStates) {
            if (state.tick() > wanted) {
                break;
            }
            latest = state;
        }
        return latest;
    }

    /**
     * Whether any block-entity edit exists at exactly this tick.
     *
     * <p>This is a cheap existence check for callers that need to know whether
     * to bother walking {@link #blockEntityAt} - a playback path that only
     * cares about chests can skip the NBT decode entirely on ticks where no
     * block-entity edit happened at all. The take tick is converted through
     * {@link #recorded} because block-entity edits are stored in absolute
     * replay tick.
     */
    public boolean hasBlockEntityChangeAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorBlockEntityEdit edit : this.blockEntities) {
            if (edit.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether any container interaction exists at exactly this tick.
     *
     * <p>This is a cheap existence check for callers that need to know whether
     * to bother walking {@link #containerAt}. The take tick is converted
     * through {@link #recorded} because container interactions are stored in
     * absolute replay tick.
     */
    public boolean hasContainerChangeAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorContainerInteraction interaction : this.containers) {
            if (interaction.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every ride transition that happened on this tick.
     *
     * <p>Rides are discrete events like {@link #containerAt}: a mount names
     * the vehicle to board, a dismount gets off. At most one transition
     * happens per tick (a performer cannot mount two boats at once), but a
     * list is returned for the same scrub-safety the other event tracks
     * have: every entry at exactly the tick replays, in order.
     */
    public List<ActorRide> ridesAt(int takeTick) {
        int wanted = recorded(takeTick);
        List<ActorRide> out = new ArrayList<>();
        for (ActorRide ride : this.rides) {
            if (ride.tick() == wanted) {
                out.add(ride);
            }
        }
        return out;
    }

    /**
     * Whether any ride transition exists at exactly this tick.
     *
     * <p>Cheap existence check so playback can skip the vehicle lookup on
     * ticks where nothing mounts or dismounts.
     */
    public boolean hasRideAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorRide ride : this.rides) {
            if (ride.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every sound cue recorded on this tick.
     *
     * <p>Discrete broadcasts like {@link #ridesAt}: everything at exactly
     * the tick replays, in record order. A performer can place two blocks
     * on one tick, and both thuds must play.
     */
    public List<ActorSound> soundsAt(int takeTick) {
        int wanted = recorded(takeTick);
        List<ActorSound> out = new ArrayList<>();
        for (ActorSound sound : this.sounds) {
            if (sound.tick() == wanted) {
                out.add(sound);
            }
        }
        return out;
    }

    /**
     * Whether any sound cue exists at exactly this tick.
     *
     * <p>Cheap existence check for the block-edit path, which plays its own
     * derived place/break sounds only when the cue track has nothing nearby
     * (see {@code BlockEdits.apply}): without it every placement would thud
     * twice, once recorded and once re-derived.
     */
    public boolean hasSoundAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorSound sound : this.sounds) {
            if (sound.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scale vanilla accumulates into {@code Entity.walkDist} per metre moved.
     *
     * <p>{@code Entity.move} adds {@code horizontalDistance * 0.6} to
     * {@code walkDist} on every move, and {@code Entity.baseTick} copies it
     * into {@code walkDistO} at the start of the next tick. The prefix cache
     * below replays exactly that accumulation from recorded frames, so the
     * numbers below agree with a body that walked the take rather than one
     * that was re-simulated onto it.
     */
    private static final double WALK_DIST_SCALE = 0.6;

    /**
     * Scale vanilla maps per-tick displacement to a walk speed with.
     *
     * <p>{@code LivingEntity.updateWalkAnimation} feeds
     * {@code min(distance * 4, 1)} into the walk state, which is what caps a
     * sprint-length stride at full swing while keeping a shuffle small. The
     * prefix cache replays that mapping per frame so the recorded swing has
     * the same amplitude as the performer's live one did.
     */
    private static final float WALK_SPEED_SCALE = 4.0f;

    /**
     * Smoothing vanilla advances the walk state with.
     *
     * <p>{@code WalkAnimationState.update(target, 0.4)} moves the speed a
     * little under halfway toward the target each tick and integrates the
     * position by the result. Replaying the same recurrence from the take
     * start is what makes the recorded phase a pure function of the take
     * tick: the same tick always yields the same speed and position no matter
     * which ticks ran before it.
     */
    private static final float WALK_SMOOTHING = 0.4f;

    /**
     * Cumulative horizontal displacement per frame, in metres.
     *
     * <p>Entry {@code i} is the metres the performer had covered from the
     * first frame up to frame {@code i}, measured frame to frame exactly as
     * {@code LivingEntity.calculateEntityAnimation} measures it tick to tick
     * (horizontal only: a player's walk state never sees the vertical). Built
     * once per frame list and then read in O(1) per tick, so a scrub-heavy
     * viewport and a straight-through export both pay one linear pass no
     * matter how many times they revisit the same ticks.
     *
     * <p>Transient: derived data, rebuilt from {@link #frames} on first use
     * after load. Never persisted, because the frames already describe it.
     */
    private transient double[] walkPrefix;

    /**
     * Smoothed walk speed per frame, replaying vanilla's recurrence.
     *
     * <p>Entry {@code i} is the speed {@code WalkAnimationState.update}
     * would hold after integrating frame {@code i}'s displacement starting
     * from rest at the first frame. The renderer reads this (through
     * {@code walkAnimation.speed}) as the swing amplitude, so it is cached
     * alongside {@link #walkPrefix} rather than re-derived per tick.
     */
    private transient float[] walkAnimSpeed;

    /**
     * Integrated walk position per frame, replaying vanilla's recurrence.
     *
     * <p>Entry {@code i} is the phase {@code WalkAnimationState.update} would
     * hold after integrating frame {@code i}'s displacement starting from
     * rest at the first frame. The renderer reads this (through
     * {@code walkAnimation.position}) as the swing phase, which is the value
     * that visibly disagreed between the viewport and the export: two passes
     * over the same tick integrated different histories and landed on
     * different phases while standing on the same frame.
     */
    private transient float[] walkAnimPosition;

    /** The {@link #startTick} {@link #walkPrefix} was built for. */
    private transient int walkCacheStartTick = Integer.MIN_VALUE;

    /** The {@link #frames} size {@link #walkPrefix} was built for. */
    private transient int walkCacheFrameCount = -1;

    /**
     * Drops the recorded limb-phase cache.
     *
     * <p>Structural frame changes (append, trim, splice) are detected
     * automatically by the size and start-tick checks in
     * {@link #ensureWalkCache}, so this is only needed after an in-place
     * frame edit that keeps the list the same length. Without it the limbs
     * keep describing the frames as they were, while the body walks the
     * frames as they are.
     */
    public void invalidateWalkCache() {
        this.walkPrefix = null;
        this.walkAnimSpeed = null;
        this.walkAnimPosition = null;
        this.walkCacheStartTick = Integer.MIN_VALUE;
        this.walkCacheFrameCount = -1;
    }

    /**
     * Rebuilds the limb-phase cache when the frames outgrew it.
     *
     * <p>A single linear pass over {@link #frames} that replays vanilla's own
     * recurrence per frame: the horizontal step feeds
     * {@code min(step * 4, 1)} through the {@code 0.4} smoothing into a speed,
     * and the position integrates that speed. Starting from rest at the first
     * frame is the honest answer for the same reason the derived velocity in
     * {@code InputSim} starts from rest there: no earlier sample exists, so
     * as far as the recording knows the performer was stationary.
     *
     * <p>Teleports and splices are deliberately not special-cased. Vanilla's
     * own tick would see the same one-frame lunge and clamp it to full swing
     * the same way, so replaying it keeps the recording and a live pass in
     * agreement about what the lunge looked like instead of inventing a
     * second opinion.
     */
    private void ensureWalkCache() {
        if (this.walkPrefix != null
            && this.walkCacheStartTick == this.startTick
            && this.walkCacheFrameCount == this.frames.size()) {
            return;
        }
        int count = this.frames.size();
        double[] prefix = new double[count];
        float[] speeds = new float[count];
        float[] positions = new float[count];
        double cumulative = 0.0;
        float speed = 0.0f;
        float position = 0.0f;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                ActorFrame before = this.frames.get(i - 1);
                ActorFrame here = this.frames.get(i);
                double dx = here.x() - before.x();
                double dz = here.z() - before.z();
                double step = Math.sqrt(dx * dx + dz * dz);
                cumulative += step;
                float target = (float) Math.min(step * WALK_SPEED_SCALE, 1.0);
                speed += (target - speed) * WALK_SMOOTHING;
                position += speed;
            }
            prefix[i] = cumulative;
            speeds[i] = speed;
            positions[i] = position;
        }
        this.walkPrefix = prefix;
        this.walkAnimSpeed = speeds;
        this.walkAnimPosition = positions;
        this.walkCacheStartTick = this.startTick;
        this.walkCacheFrameCount = count;
    }

    /**
     * The frame index a take tick reads its limbs from, clamped like {@link #frameAt}.
     *
     * <p>Before the first frame the performer had not moved yet, so the index
     * holds at zero; past the last frame the body holds the last frame, so
     * the limbs hold the last phase. Returns -1 when there are no frames at
     * all, in which case every lookup below answers rest.
     */
    private int walkCacheIndex(int takeTick) {
        if (this.frames.isEmpty()) {
            return -1;
        }
        int index = takeTick - this.startTick;
        if (index < 0) {
            index = 0;
        } else if (index >= this.frames.size()) {
            index = this.frames.size() - 1;
        }
        return index;
    }

    /**
     * The recorded {@code Entity.walkDist} for a take tick.
     *
     * <p>The cumulative recorded displacement up to this tick's frame, times
     * vanilla's {@code 0.6} move scale. A pure function of the take tick by
     * construction: the viewport and the export reach the same tick through
     * different tick histories, but both read the same prefix entry, so the
     * cape sway, the first-person hand bob and the sprint-gate check that
     * read this field all see one phase.
     */
    public float walkDistAt(int takeTick) {
        ensureWalkCache();
        int index = walkCacheIndex(takeTick);
        if (index < 0) {
            return 0.0f;
        }
        return (float) (this.walkPrefix[index] * WALK_DIST_SCALE);
    }

    /**
     * The recorded {@code Entity.walkDistO} for a take tick.
     *
     * <p>The previous tick's {@link #walkDistAt}: vanilla copies
     * {@code walkDist} into {@code walkDistO} at the start of every tick, so
     * the recorded pair is this tick's prefix and the one before it. At or
     * before the first frame both are rest, which is what a body standing on
     * its opening frame should report.
     */
    public float walkDistOAt(int takeTick) {
        ensureWalkCache();
        int index = walkCacheIndex(takeTick);
        if (index <= 0) {
            return 0.0f;
        }
        return (float) (this.walkPrefix[index - 1] * WALK_DIST_SCALE);
    }

    /**
     * The recorded walk-animation speed (swing amplitude) for a take tick.
     *
     * <p>What {@code WalkAnimationState.speed} would hold after integrating
     * the recording up to this tick's frame. The player renderer reads it as
     * the limb-swing amount ({@code HumanoidModel.setupAnim} scales every
     * limb by it), derived here from the per-tick recorded speed with the
     * same {@code min(step * 4, 1)} scale and {@code 0.4} smoothing vanilla
     * uses, so the amplitude matches the stride instead of the tick history.
     */
    public float walkSpeedAt(int takeTick) {
        ensureWalkCache();
        int index = walkCacheIndex(takeTick);
        if (index < 0) {
            return 0.0f;
        }
        return this.walkAnimSpeed[index];
    }

    /**
     * The recorded walk-animation speed of the previous tick.
     *
     * <p>What {@code WalkAnimationState.speedOld} would hold: the smoothed
     * speed one frame earlier, so the renderer's per-frame lerp between old
     * and new has the same endpoints on every pass. Rest at or before the
     * first frame, like {@link #walkDistOAt}.
     */
    public float walkSpeedOldAt(int takeTick) {
        ensureWalkCache();
        int index = walkCacheIndex(takeTick);
        if (index <= 0) {
            return 0.0f;
        }
        return this.walkAnimSpeed[index - 1];
    }

    /**
     * The recorded walk-animation position (swing phase) for a take tick.
     *
     * <p>What {@code WalkAnimationState.position} would hold after
     * integrating the recording up to this tick's frame. The player renderer
     * reads it as the limb-swing phase ({@code HumanoidModel.setupAnim}
     * drives every leg and arm angle off {@code cos(phase * 0.6662)}), and it
     * is this phase that stood mid-stride together in the viewport while
     * standing split wide in the export: same frame, different accumulated
     * histories. Read from the prefix, the same tick always yields the same
     * phase on both paths.
     */
    public float walkPositionAt(int takeTick) {
        ensureWalkCache();
        int index = walkCacheIndex(takeTick);
        if (index < 0) {
            return 0.0f;
        }
        return this.walkAnimPosition[index];
    }
}
