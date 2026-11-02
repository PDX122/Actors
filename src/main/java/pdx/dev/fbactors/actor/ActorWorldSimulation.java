package pdx.dev.fbactors.actor;

import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.mixin.LevelActorTicks;
import pdx.dev.fbactors.mixin.ServerLevelActorAccess;
import java.util.*;

public final class ActorWorldSimulation {
    private static final class State {
        final SimulationArea area = new SimulationArea();
        int tick = Integer.MIN_VALUE;
        int serverTick = Integer.MIN_VALUE;
        long clock;
    }
    private static final Map<ServerLevel, State> states = new WeakHashMap<>();
    private static final Set<Entity> created = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final ThreadLocal<Integer> simulating = ThreadLocal.withInitial(() -> 0);
    private ActorWorldSimulation() {}
    public static boolean isSimulating() { return simulating.get() > 0; }
    public static void track(Entity entity) { created.add(entity); }
    public static Entity owner(Projectile projectile) {
        if (projectile instanceof ProjectileAttachment attachment && attachment.fbactors$attachedTo() != null)
            return attachment.fbactors$attachedTo();
        return projectile.getOwner();
    }
    public static boolean manualFirework(Entity entity) {
        return entity instanceof FireworkRocketEntity rocket && owner(rocket) instanceof ActorClonePlayer;
    }
    public static void touch(ServerLevel level, BlockPos pos) {
        if (!(level.getServer() instanceof ReplayServer)) return;
        states.computeIfAbsent(level, ignored -> new State()).area.touch(pos.getX(), pos.getY(), pos.getZ());
    }
    public static void changed(net.minecraft.world.level.Level world, BlockPos pos) {
        if (simulating.get() > 0 && world instanceof ServerLevel level) touch(level, pos);
    }
    public static void tick(ReplayServer replay, int tick) {
        boolean prep = ActorMode.phase() == ActorMode.Phase.PREP || ActorMode.phase() == ActorMode.Phase.COUNTDOWN;
        if (replay.replayPaused && !prep && !com.moulberry.flashback.Flashback.isExporting()) return;
        for (var actor : FlashbackActors.ACTORS.actors()) {
            var clone = actor.clone();
            if (clone != null && clone.level() instanceof ServerLevel level) {
                touch(level, clone.blockPosition());
                if (clone.getVehicle() != null) touch(level, clone.getVehicle().blockPosition());
            }
        }
        for (var viewer : replay.getReplayViewers()) if (ActorMode.isActive()) touch((ServerLevel)viewer.level(), viewer.blockPosition());
        for (var level : replay.getAllLevels()) {
            var state = states.get(level);
            if (state == null) continue;
            if (state.tick == tick && (!prep || state.serverTick == replay.getTickCount())) continue;
            if (tick < state.tick) state.clock = level.getGameTime();
            state.tick = tick;
            state.serverTick = replay.getTickCount();
            state.clock = Math.max(level.getGameTime(), state.clock + 1);
            if (level.getLevelData() instanceof ServerLevelData data) data.setGameTime(state.clock);
            simulating.set(simulating.get() + 1);
            try {
                if (pdx.dev.fbactors.settings.FbSettings.settings().worldMechanics) {
                    var access = (ServerLevelActorAccess) level;
                    level.getBlockTicks().tick(state.clock, 4096, (pos, block) -> {
                        if (level.hasChunkAt(pos) && state.area.contains(pos.getX(),pos.getY(),pos.getZ())) access.fbactors$tickBlock(pos, block);
                        else level.scheduleTick(pos, block, 1);
                    });
                    level.getFluidTicks().tick(state.clock, 4096, (pos, fluid) -> {
                        if (level.hasChunkAt(pos) && state.area.contains(pos.getX(),pos.getY(),pos.getZ())) access.fbactors$tickFluid(pos, fluid);
                        else level.scheduleTick(pos, fluid, 1);
                    });
                    access.fbactors$runBlockEvents();
                    tickBlockEntities(level, state.area);
                }
                List<Entity> rockets;
                synchronized (created) { rockets = created.stream().filter(e -> e.level() == level && !e.isRemoved() && manualFirework(e)).toList(); }
                if (pdx.dev.fbactors.settings.FbSettings.settings().flightPlayback) for (var rocket : rockets) rocket.tick();
            } finally {
                int depth = simulating.get() - 1;
                if (depth == 0) simulating.remove(); else simulating.set(depth);
            }
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void tickBlockEntities(ServerLevel level, SimulationArea area) {
        var access = (LevelActorTicks) level;
        var tickers = new ArrayList<>(access.fbactors$blockEntityTickers());
        tickers.addAll(access.fbactors$pendingBlockEntityTickers());
        Set<BlockPos> visited = new HashSet<>();
        int budget = 512;
        for (var ticker : tickers) {
            var pos = ticker.getPos();
            if (!visited.add(pos) || !area.contains(pos.getX(),pos.getY(),pos.getZ()) || !level.hasChunkAt(pos)) continue;
            var entity = level.getBlockEntity(pos);
            var state = level.getBlockState(pos);
            if (entity == null || entity.isRemoved() || !(state.getBlock() instanceof EntityBlock block)) continue;
            BlockEntityTicker nativeTicker = block.getTicker(level, state, entity.getType());
            if (nativeTicker != null) {
                nativeTicker.tick(level, pos, state, entity);
                if (--budget == 0) break;
            }
        }
    }
    public static boolean shouldTick(Entity entity) {
        if (ActorMode.isActive() && entity.getPassengers().stream().anyMatch(r -> FlashbackActors.RECORDER.isPerformer(r.getUUID()))) return true;
        if (!(entity.level() instanceof ServerLevel level) || !(level.getServer() instanceof ReplayServer replay)) return false;
        if (BoatPhysics.driven(entity) || manualFirework(entity)) return false;
        if (created.contains(entity)) return !replay.replayPaused || ActorMode.phase() == ActorMode.Phase.PREP
            || ActorMode.phase() == ActorMode.Phase.COUNTDOWN || com.moulberry.flashback.Flashback.isExporting();
        if (!(entity instanceof Projectile projectile)) return false;
        Entity owner = owner(projectile);
        if (owner instanceof ActorClonePlayer) return !replay.replayPaused || com.moulberry.flashback.Flashback.isExporting();
        return owner != null && ActorMode.isActive() && FlashbackActors.RECORDER.isPerformer(owner.getUUID());
    }
}
