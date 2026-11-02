package pdx.dev.fbactors.actor;

import net.minecraft.world.entity.Entity;
import pdx.dev.fbactors.FlashbackActors;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class BoatPhysics {
    public interface Boat {
        float fbactors$rowingTime(int side, float partial);
        boolean fbactors$paddle(int side);
        void fbactors$paddles(boolean left, boolean right);
    }
    private record Visual(Object replay, int tick, java.util.UUID boat,
                          pdx.dev.fbactors.take.ActorVehicleFrame previous,
                          pdx.dev.fbactors.take.ActorVehicleFrame pose) {}
    private static final java.util.concurrent.ConcurrentMap<java.util.UUID, Visual> visuals = new java.util.concurrent.ConcurrentHashMap<>();

    public static void step(Entity vehicle) {
        if (!driven(vehicle) || vehicle.level().isClientSide()) return;
        pdx.dev.fbactors.compat.Compat.cancelInterpolation(vehicle);
        vehicle.tick();
    }
    public static void grip(Entity boat, net.minecraft.world.phys.Vec3 before) {
        var settings = pdx.dev.fbactors.settings.FbSettings.settings();
        if (settings.boatGrip == 1 || !boat.onGround() || before == null) return;
        var after = boat.getDeltaMovement();
        double yaw = Math.toRadians(boat.getYRot());
        double sx = Math.cos(yaw), sz = Math.sin(yaw);
        double oldSide = before.x * sx + before.z * sz;
        double currentSide = after.x * sx + after.z * sz;
        double grip = BoatHandling.clamp(settings.boatGrip, 0, 2);
        double wanted = grip < 1 ? oldSide + (currentSide - oldSide) * grip : currentSide * Math.exp(-(grip-1)*.15);
        boat.setDeltaMovement(after.x + sx*(wanted-currentSide), after.y, after.z + sz*(wanted-currentSide));
    }
    public static void bounce(Entity boat, net.minecraft.world.phys.Vec3 before, net.minecraft.world.phys.Vec3 velocity,
                              net.minecraft.world.phys.Vec3 requested) {
        var settings = pdx.dev.fbactors.settings.FbSettings.settings();
        if (settings.boatBounce <= 0 || !driven(boat) || boat.level().isClientSide() || !boat.horizontalCollision) return;
        var moved = boat.position().subtract(before);
        var after = boat.getDeltaMovement();
        boat.setDeltaMovement(BoatHandling.bounce(velocity.x, after.x, requested.x, moved.x, settings.boatBounce), after.y,
            BoatHandling.bounce(velocity.z, after.z, requested.z, moved.z, settings.boatBounce));
    }
    public static void seat(ActorClonePlayer driver, float oldHead, float oldBody) {
        Entity vehicle = driver.getVehicle();
        if (!(vehicle instanceof Boat)) return;
        vehicle.positionRider(driver);
        float yaw = driver.getYRot();
        driver.setYHeadRot(yaw);
        driver.setYBodyRot(yaw);
        driver.yHeadRotO = yaw + net.minecraft.util.Mth.wrapDegrees(oldHead - yaw);
        driver.yBodyRotO = yaw + net.minecraft.util.Mth.wrapDegrees(oldBody - yaw);
    }
    public static void animate(Actor actor, Entity boat, pdx.dev.fbactors.take.ActorVehicleFrame pose) {
        int takeTick = (int) (actor.takeTickFor(FlashbackActors.replayTick()) * actor.take.getSpeedMultiplier());
        var before = actor.take.vehicleAt(takeTick - 1);
        var settings = pdx.dev.fbactors.settings.FbSettings.settings();
        if (!Float.isFinite(pose.left()) && !settings.legacyBoatPaddles) {
            forget(actor.take.id);
            if (boat instanceof Boat paddles) paddles.fbactors$paddles(false, false);
            return;
        }

        if (!Float.isFinite(pose.left())) {
            int tick = (int) (actor.takeTickFor(FlashbackActors.replayTick()) * actor.take.getSpeedMultiplier());
            var input = actor.take.inputAt(tick);
            if (input != null) {
                boolean left = input.right() && !input.left() || input.forward();
                boolean right = input.left() && !input.right() || input.forward();
                float step = (float) Math.PI / 8;
                float phase = (actor.take.recordedTick(tick) - actor.take.startTick + 1) * step;
                pose = pose.withMotion(pose.velocityX(), pose.velocityY(), pose.velocityZ(),
                    phase - step, phase, phase - step, phase, left, right);
            }
        }
        visuals.put(actor.take.id, new Visual(com.moulberry.flashback.Flashback.getReplayServer(),
            FlashbackActors.replayTick(), boat.getUUID(), before, pose));
        if (boat instanceof Boat paddles && Float.isFinite(pose.left()))
            paddles.fbactors$paddles(settings.boatPaddles && pose.leftPaddle(), settings.boatPaddles && pose.rightPaddle());
    }
    public static void forget(java.util.UUID actor) { visuals.remove(actor); }
    public static float rowing(Entity boat, int side, float partial) {
        if (!boat.level().isClientSide() || boat.getFirstPassenger() == null) return Float.NaN;
        if (ActorMode.isActive() && FlashbackActors.RECORDER.isPerformer(boat.getFirstPassenger().getUUID())) return Float.NaN;
        var visual = visuals.get(boat.getFirstPassenger().getUUID());
        var replay = com.moulberry.flashback.Flashback.getReplayServer();
        if (visual == null || replay == null || visual.replay() != replay
            || !boat.getUUID().equals(visual.boat())) return Float.NaN;
        var settings = pdx.dev.fbactors.settings.FbSettings.settings();
        if (!settings.boatPaddles) return 0;
        if (!settings.smoothBoatPaddles) return visual.pose().rowing(side, 1);
        boolean exporting = com.moulberry.flashback.Flashback.isExporting();
        double renderTick = pdx.dev.fbactors.camera.BoatSubjects.clock();
        float fraction = replay.replayPaused && !exporting ? 1
            : pdx.dev.fbactors.take.BoatRenderInterpolation.fraction(renderTick, visual.tick());
        return pdx.dev.fbactors.take.BoatRenderInterpolation.rowing(visual.previous(), visual.pose(), side, fraction);
    }
    private static final Map<Entity, Integer> ticks = Collections.synchronizedMap(new WeakHashMap<>());
    private BoatPhysics() {}
    public static boolean driven(Entity entity) {
        return entity instanceof Boat && entity.getFirstPassenger() instanceof ActorClonePlayer;
    }
    public static boolean fresh(Entity entity) {
        return ticks.getOrDefault(entity, Integer.MIN_VALUE) != FlashbackActors.replayTick();
    }
    public static void ticked(Entity entity) {
        if (driven(entity)) ticks.put(entity, FlashbackActors.replayTick());
    }
}
