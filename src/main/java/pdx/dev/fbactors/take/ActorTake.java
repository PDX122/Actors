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

    public final List<ActorVisibility> hiddenRanges;

    public final List<ActorSubtitle> subtitles;

    public final List<ActorVitals> vitals;

    public final List<ActorMining> mining;

    public final List<ActorEffect> effects;

    public final List<ActorShot> shots;

    public final List<ActorInput> inputs;

    public final List<ActorBlockEntityEdit> blockEntities;

    public final List<ActorContainerInteraction> containers;

    public final List<ActorRide> rides;
    public final List<ActorVehicleFrame> vehicles = new ArrayList<>();
    public final List<ActorMomentum> momentum = new ArrayList<>();

    public final List<ActorSound> sounds;

    public final List<ActorUse> uses;

    public final List<ActorSpawn> spawns;

    public final List<ActorMiningState> miningStates;

    public final CameraTrack cameraTrack = new CameraTrack();

    @Nullable
    public DirectorConfig directorConfig;

    public boolean voiceEnabled = false;

    public String voiceDevice;

    public boolean voiceActivation = true;

    public int voiceThreshold = -45;

    public int offsetTicks = 0;
    public int trimIn = 0;
    public int trimOut = 0;
    public boolean enabled = true;
    public int colour = 0;

    public boolean locked = false;

    public int speakerColorArgb = 0;

    private float speedMultiplier = 1.0f;

    public static final float MAX_HEALTH = 20.0f;

    public static final int DEATH_LINGER_TICKS = 30;

    public ActorTake(UUID id, String name, String dimension, int startTick) {
        this(id, name, dimension, startTick, new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
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
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
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
                     List<ActorMiningState> miningStates,
                     List<ActorSubtitle> subtitles) {
        this(id, name, dimension, startTick, frames, events, equipment, hits, deaths, blocks,
            voices, vitals, mining, effects, shots, blockEntities, containers, miningStates,
            subtitles, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
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
                     List<ActorSubtitle> subtitles,
                     List<ActorVisibility> hiddenRanges) {
        this(id, name, dimension, startTick, frames, events, equipment, hits, deaths, blocks,
            voices, vitals, mining, effects, shots, blockEntities, containers, miningStates,
            subtitles, hiddenRanges, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
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
            subtitles, hiddenRanges, rides, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
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
        this(id, name, dimension, startTick, frames, events, equipment, hits, deaths, blocks,
            voices, vitals, mining, effects, shots, blockEntities, containers, miningStates,
            subtitles, hiddenRanges, rides, sounds, new ArrayList<>(), new ArrayList<>());
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
                     List<ActorSound> sounds,
                     List<ActorUse> uses,
                     List<ActorSpawn> spawns) {
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
        this.uses = uses;
        this.spawns = spawns;
        this.inputs = new ArrayList<>();
    }

    public int recordedTick(int takeTick) {
        return takeTick + this.offsetTicks;
    }

    private int recorded(int takeTick) {
        return recordedTick(takeTick);
    }

    public void setSpeedMultiplier(float speed) {
        this.speedMultiplier = speed;
    }

    public float getSpeedMultiplier() {
        return this.speedMultiplier;
    }

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

    public boolean simulated() {
        return !this.inputs.isEmpty();
    }

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

    public void recordHit(ActorHit hit) {

        int index = this.hits.size();
        while (index > 0 && this.hits.get(index - 1).tick() > hit.tick()) {
            index--;
        }
        while (index > 0 && this.hits.get(index - 1).tick() == hit.tick()
            && java.util.Objects.equals(this.hits.get(index - 1).target(), hit.target())) {
            this.hits.remove(index - 1);
            index--;
        }
        this.hits.add(index, hit);
    }

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

    public boolean hasHitAt(int replayTick) {
        return hitAt(replayTick) != null;
    }

    public float healthAt(int replayTick) {
        float health = MAX_HEALTH;
        for (ActorHit hit : this.hits) {
            if (hit.tick() <= replayTick) {
                health -= hit.damage();
            } else {

                break;
            }
        }
        return Mth.clamp(health, 0.0f, MAX_HEALTH);
    }

    public int deathTick() {
        int earliest = -1;

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

    public void sortHits() {
        this.hits.sort(Comparator.comparingInt(ActorHit::tick));

        List<ActorHit> collapsed = new ArrayList<>(this.hits.size());
        int lastTick = Integer.MIN_VALUE;
        java.util.UUID lastTarget = null;
        boolean hasLast = false;
        for (ActorHit hit : this.hits) {
            if (hasLast && hit.tick() == lastTick
                && java.util.Objects.equals(hit.target(), lastTarget)) {
                continue;
            }
            collapsed.add(hit);
            lastTick = hit.tick();
            lastTarget = hit.target();
            hasLast = true;
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
            new ArrayList<>(this.sounds), new ArrayList<>(this.uses),
            new ArrayList<>(this.spawns));
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
        copy.vehicles.addAll(this.vehicles);
        copy.momentum.addAll(this.momentum);

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

    public byte selectedSlotAt(int replayTick) {
        ActorInput input = inputAt(replayTick);
        return input == null ? (byte) -1 : input.selectedSlot();
    }

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

    public boolean hasBlockEntityChangeAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorBlockEntityEdit edit : this.blockEntities) {
            if (edit.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

    public boolean hasContainerChangeAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorContainerInteraction interaction : this.containers) {
            if (interaction.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

    public ActorMomentum momentumAt(int takeTick) {
        int tick = recorded(takeTick);
        int low = 0, high = momentum.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            var motion = momentum.get(mid);
            if (motion.tick() < tick) low = mid + 1;
            else if (motion.tick() > tick) high = mid - 1;
            else return motion;
        }
        return null;
    }

    public ActorVehicleFrame vehicleAt(int takeTick) {
        int tick = recorded(takeTick);
        int low = 0, high = vehicles.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            var vehicle = vehicles.get(mid);
            if (vehicle.tick() < tick) low = mid + 1;
            else if (vehicle.tick() > tick) high = mid - 1;
            else return vehicle;
        }
        return null;
    }

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

    public boolean hasRideAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorRide ride : this.rides) {
            if (ride.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

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

    public boolean hasSoundAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorSound sound : this.sounds) {
            if (sound.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

    public List<ActorUse> usesAt(int takeTick) {
        int wanted = recorded(takeTick);
        List<ActorUse> out = new ArrayList<>();
        for (ActorUse use : this.uses) {
            if (use.tick() == wanted) {
                out.add(use);
            }
        }
        return out;
    }

    public boolean hasUseAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorUse use : this.uses) {
            if (use.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

    public List<ActorSpawn> spawnsAt(int takeTick) {
        int wanted = recorded(takeTick);
        List<ActorSpawn> out = new ArrayList<>();
        for (ActorSpawn spawn : this.spawns) {
            if (spawn.tick() == wanted) {
                out.add(spawn);
            }
        }
        return out;
    }

    public boolean hasSpawnAt(int takeTick) {
        int wanted = recorded(takeTick);
        for (ActorSpawn spawn : this.spawns) {
            if (spawn.tick() == wanted) {
                return true;
            }
        }
        return false;
    }

    private static final double WALK_DIST_SCALE = 0.6;

    private static final float WALK_SPEED_SCALE = 4.0f;

    private static final float WALK_SMOOTHING = 0.4f;

    private transient double[] walkPrefix;

    private transient float[] walkAnimSpeed;

    private transient float[] walkAnimPosition;

    private transient int walkCacheStartTick = Integer.MIN_VALUE;

    private transient int walkCacheFrameCount = -1;

    public void invalidateWalkCache() {
        this.walkPrefix = null;
        this.walkAnimSpeed = null;
        this.walkAnimPosition = null;
        this.walkCacheStartTick = Integer.MIN_VALUE;
        this.walkCacheFrameCount = -1;
    }

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

    public float walkDistAt(int takeTick) {
        ensureWalkCache();
        int index = walkCacheIndex(takeTick);
        if (index < 0) {
            return 0.0f;
        }
        return (float) (this.walkPrefix[index] * WALK_DIST_SCALE);
    }

    public float walkDistOAt(int takeTick) {
        ensureWalkCache();
        int index = walkCacheIndex(takeTick);
        if (index <= 0) {
            return 0.0f;
        }
        return (float) (this.walkPrefix[index - 1] * WALK_DIST_SCALE);
    }

    public float walkSpeedAt(int takeTick) {
        ensureWalkCache();
        int index = walkCacheIndex(takeTick);
        if (index < 0) {
            return 0.0f;
        }
        return this.walkAnimSpeed[index];
    }

    public float walkSpeedOldAt(int takeTick) {
        ensureWalkCache();
        int index = walkCacheIndex(takeTick);
        if (index <= 0) {
            return 0.0f;
        }
        return this.walkAnimSpeed[index - 1];
    }

    public float walkPositionAt(int takeTick) {
        ensureWalkCache();
        int index = walkCacheIndex(takeTick);
        if (index < 0) {
            return 0.0f;
        }
        return this.walkAnimPosition[index];
    }
}
