package pdx.dev.fbactors.actor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.TakeStore;
import pdx.dev.fbactors.util.AtomicSave;

public final class ActorTerrain {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ThreadLocal<Integer> editing = ThreadLocal.withInitial(() -> 0);
    private static ReplayServer server;
    private static TerrainEdits edits = new TerrainEdits();
    private static boolean dirty;
    private static final PlaybackContinuity continuity = new PlaybackContinuity();
    private static final java.util.Map<ServerLevel, java.util.Set<BlockPos>> pendingEdits = new java.util.WeakHashMap<>();
    private static final java.util.Set<ServerLevel> appliedLevels = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private ActorTerrain() {}
    public static boolean isEditing() { return editing.get() > 0 && !BlockEdits.isApplying(); }

    private static void session(ReplayServer replay) {
        if (server == replay) return;
        server = replay;
        edits = new TerrainEdits();
        dirty = false;
        continuity.reset();
        appliedLevels.clear();
        pendingEdits.clear();
        var file = TakeStore.rootFor(replay.getMetadata().replayIdentifier).resolve("scene-blocks.json");
        if (!java.nio.file.Files.isRegularFile(file)) return;
        try (var reader = java.nio.file.Files.newBufferedReader(file)) {
            var saved = GSON.fromJson(reader, TerrainEdits.Entry[].class);
            if (saved != null) for (var edit : saved) {
                if (edit != null && edit.dimension() != null && edit.before() != null && edit.after() != null) edits.record(edit);
            }
        } catch (Exception error) {
            FlashbackActors.LOGGER.warn("Could not load viewport block edits", error);
        }
    }
    public static boolean begin(ServerPlayer player) {
        if (!pdx.dev.fbactors.settings.FbSettings.settings().viewportEdits) return false;
        if (player instanceof ActorClonePlayer || !(player.level() instanceof ServerLevel level)
            || !(level.getServer() instanceof ReplayServer replay) || BlockEdits.isApplying()
            || ActorMode.phase() != ActorMode.Phase.IDLE) return false;
        session(replay);
        editing.set(editing.get() + 1);
        return true;
    }
    public static void end() {
        int depth = editing.get() - 1;
        if (depth > 0) { editing.set(depth); return; }
        editing.remove();
        if (dirty && server != null) {
            dirty = !AtomicSave.writeJson(TakeStore.rootFor(server.getMetadata().replayIdentifier),
                "scene-blocks.json", writer -> GSON.toJson(edits.entries(), writer));
        }
    }
    public static void changed(Level world, BlockPos pos, BlockState before, BlockState after) {
        if (editing.get() <= 0 || BlockEdits.isApplying() || before.equals(after)
            || !(world instanceof ServerLevel level)) return;
        edits.record(new TerrainEdits.Entry(Compat.dimensionId(level), pos.getX(), pos.getY(), pos.getZ(),
            BlockEdits.serialize(before), BlockEdits.serialize(after)));
        dirty = true;
        ActorWorldSimulation.touch(level, pos);
    }
    public static void apply(ReplayServer replay) {
        if (!pdx.dev.fbactors.settings.FbSettings.settings().viewportEdits) {
            continuity.reset(); appliedLevels.clear(); pendingEdits.clear(); return;
        }
        session(replay);
        var overrides = edits.entries();
        if (overrides.isEmpty()) return;
        boolean restore = continuity.restoreAt(replay.getReplayTick());
        BlockEdits.beginApply();
        try {
            for (var level : replay.getAllLevels()) {
                String dimension = Compat.dimensionId(level);
                boolean restoreLevel = !appliedLevels.contains(level) || restore;
                appliedLevels.add(level);
                var pending = pendingEdits.computeIfAbsent(level, ignored -> new java.util.HashSet<>());
                if (restoreLevel) for (var edit : overrides) {
                    if (dimension.equals(edit.dimension())) pending.add(new BlockPos(edit.x(), edit.y(), edit.z()));
                }
                for (var edit : overrides) {
                    if (!dimension.equals(edit.dimension())) continue;
                    var pos = new BlockPos(edit.x(), edit.y(), edit.z());
                    ActorWorldSimulation.touch(level, pos);
                    if (!pending.contains(pos) || !level.hasChunkAt(pos)) continue;
                    if (!BlockEdits.serialize(level.getBlockState(pos)).equals(edit.after())) {
                        var state = BlockEdits.parse(level, edit.after());
                        if (state != null) level.setBlock(pos, state, 3);
                    }
                    pending.remove(pos);
                }
            }
        } finally { BlockEdits.endApply(); }
    }
    public static void react(ServerLevel level, Actor actor, int tick) {
        var settings = pdx.dev.fbactors.settings.FbSettings.settings();
        if (!settings.viewportEdits || !settings.terrainReactions) return;
        var clone = actor.clone();
        if (clone == null || !actor.sim().usesRecordedBaseline(tick)) return;
        var body = clone.getVehicle() == null ? clone : clone.getVehicle();
        var area = body.getBoundingBox().expandTowards(body.getDeltaMovement()).inflate(0.05, 0.6, 0.05);
        if (edits.affects(Compat.dimensionId(level), area.minX, area.minY, area.minZ, area.maxX, area.maxY, area.maxZ)) {
            actor.sim().diverge(tick);
            BoatPhysics.forget(actor.take.id);
            FlashbackActors.LOGGER.info("Terrain edit released actor '{}' at take tick {} near {}",
                actor.displayName(), tick, body.position());
        }
    }
}
