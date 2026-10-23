package pdx.dev.fbactors.actor;

import com.mojang.authlib.GameProfile;
import pdx.dev.fbactors.take.ActorTake;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import pdx.dev.fbactors.compat.Compat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Actor {


    private static final int MINING_ID_START = -982_000_000;

    public final ActorTake take;

    public boolean solo = false;


    public boolean reportedEmbedded;


    public boolean reportedShape;

    @Nullable
    private ActorClonePlayer clone;
    private int lastAppliedTakeTick = Integer.MIN_VALUE;
    private int lastAppliedBlockTick = Integer.MIN_VALUE;

    private final InputSim sim;

    private int nextMiningId = MINING_ID_START;
    private final Map<BlockPos, Integer> miningIds = new HashMap<>();
    private final Map<BlockPos, Integer> miningStages = new HashMap<>();

    /**
     * World entities this actor's playback spawned, keyed by entity UUID to
     * the take tick that spawned them.
     *
     * <p>Used to discard spawns on scrub-backwards (entities whose spawn tick
     * is now after the playhead go away) and on despawn. UUID, not id:
     * session-unique and resolvable via Compat.entityByUuid.
     */
    private final Map<UUID, Integer> spawnedEntities = new HashMap<>();

    public Actor(ActorTake take) {
        this.take = take;
        this.sim = new InputSim(this);
    }

    public GameProfile profile() {
        String name = this.take.name == null || this.take.name.isBlank() ? "Actor" : this.take.name;
        name = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        return new GameProfile(this.take.id, name);
    }

    public ResourceKey<Level> dimensionKey() {
        return Compat.dimensionKey(this.take.dimension);
    }

    public int takeTickFor(int replayTick) {
        return replayTick - this.take.offsetTicks;
    }

    public boolean enabled() {
        return this.take.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.take.enabled = enabled;
    }

    public int offset() {
        return this.take.offsetTicks;
    }

    public void setOffset(int offsetTicks) {
        this.take.offsetTicks = offsetTicks;
    }

    public String displayName() {
        String name = this.take.name;
        return name == null || name.isBlank() ? "Actor" : name;
    }

    @Nullable
    public ActorClonePlayer clone() {
        return this.clone;
    }


    @Nullable
    public net.minecraft.world.phys.Vec3 livePosition() {
        if (this.clone == null || this.clone.isRemoved()) {
            return null;
        }
        return this.clone.position();
    }

    public void setClone(@Nullable ActorClonePlayer clone) {
        this.clone = clone;
        this.lastAppliedTakeTick = Integer.MIN_VALUE;
        this.lastAppliedBlockTick = Integer.MIN_VALUE;
        this.sim.reset();
    }


    public InputSim sim() {
        return this.sim;
    }

    public int lastAppliedTakeTick() {
        return this.lastAppliedTakeTick;
    }

    public void markApplied(int takeTick) {
        this.lastAppliedTakeTick = takeTick;
    }

    public int lastAppliedBlockTick() {
        return this.lastAppliedBlockTick;
    }

    public void markBlocksApplied(int takeTick) {
        this.lastAppliedBlockTick = takeTick;
    }

    public void despawn() {
        if (this.clone != null) {
            this.clone.discard();
            this.clone = null;
            this.lastAppliedTakeTick = Integer.MIN_VALUE;
            this.lastAppliedBlockTick = Integer.MIN_VALUE;
            this.sim.reset();
        }
        this.spawnedEntities.clear();
    }

    public void trackSpawn(UUID entityUuid, int takeTick) {
        if (entityUuid != null) {
            this.spawnedEntities.put(entityUuid, takeTick);
        }
    }

    public Map<UUID, Integer> spawnedEntities() {
        return this.spawnedEntities;
    }


    @Nullable
    public Integer miningId(BlockPos pos) {
        return this.miningIds.get(pos);
    }

    public int assignMiningId(BlockPos pos) {
        int id = this.nextMiningId++;
        this.miningIds.put(pos, id);
        return id;
    }


    @Nullable
    public Integer miningStage(BlockPos pos) {
        return this.miningStages.get(pos);
    }

    public void setMiningStage(BlockPos pos, int stage) {
        this.miningStages.put(pos, stage);
    }


    public Set<Map.Entry<BlockPos, Integer>> outstandingMining() {
        return Set.copyOf(this.miningIds.entrySet());
    }

    public void forgetMining(BlockPos pos) {
        this.miningIds.remove(pos);
        this.miningStages.remove(pos);
    }

    public void forgetAllMining() {
        this.miningIds.clear();
        this.miningStages.clear();
    }
}

