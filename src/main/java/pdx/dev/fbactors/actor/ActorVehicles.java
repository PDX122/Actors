package pdx.dev.fbactors.actor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorSpawn;

public final class ActorVehicles {
    private ActorVehicles() {}

    public static ActorFrame riderFrame(Entity rider, ActorFrame frame, pdx.dev.fbactors.take.ActorVehicleFrame pose) {
        Entity vehicle = rider.getVehicle();
        if (pose == null || vehicle == null || !vehicle.getUUID().equals(pose.uuid())) return frame;
        var offset = vehicle.getPassengerRidingPosition(rider).subtract(vehicle.position());
        return pdx.dev.fbactors.take.ActorRidingPose.resolve(frame, pose, offset.x, offset.y, offset.z);
    }

    public static boolean recorded(Entity vehicle) {
        if (!(vehicle.getFirstPassenger() instanceof ActorClonePlayer driver)) return false;
        var actor = FlashbackActors.ACTORS.byUuid(driver.getUUID());
        if (actor == null) return false;
        int tick = (int) (actor.takeTickFor(FlashbackActors.replayTick()) * actor.take.getSpeedMultiplier());
        return actor.sim().usesRecordedBaseline(tick) && actor.take.vehicleAt(tick) != null;
    }

    public static void apply(ServerLevel level, Actor actor, int tick, ActorFrame frame) {
        if (!actor.sim().usesRecordedBaseline(tick) || actor.clone() == null) {
            BoatPhysics.forget(actor.take.id);
            return;
        }
        var pose = actor.take.vehicleAt(tick);
        var clone = actor.clone();
        if (pose == null) {
            if (actor.take.vehicles.isEmpty()) restoreLegacyRide(level, actor, tick, frame);
            else if (clone.getVehicle() != null) clone.stopRiding();
            return;
        }
        Entity vehicle = Compat.entityByUuid(level, pose.uuid());
        if (vehicle == null || vehicle.isRemoved()) {
            String nbt = actor.take.spawns.stream()
                .filter(spawn -> pose.uuid().equals(ActorEntityIdentity.read(spawn.nbt())))
                .map(ActorSpawn::nbt).findFirst().orElse(ActorEntityIdentity.write(pose.uuid()));
            vehicle = Compat.spawnEntityFromRecord(level, new ActorSpawn(pose.tick(), pose.type(),
                pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), nbt));
            if (vehicle == null) return;
            actor.trackSpawn(vehicle.getUUID(), tick);
            ActorWorldSimulation.track(vehicle);
        }
        Compat.snapTo(vehicle, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
        var before = actor.take.vehicleAt(tick - 1);
        var velocity = pdx.dev.fbactors.settings.FbSettings.settings().slidingMomentum && pose.hasMomentum()
            ? new net.minecraft.world.phys.Vec3(pose.velocityX(), pose.velocityY(), pose.velocityZ())
            : before != null && before.uuid().equals(pose.uuid())
                ? new net.minecraft.world.phys.Vec3(pose.x() - before.x(), pose.y() - before.y(), pose.z() - before.z())
                : net.minecraft.world.phys.Vec3.ZERO;
        vehicle.setDeltaMovement(velocity);
        BoatPhysics.animate(actor, vehicle, pose);
        if (clone.getVehicle() != vehicle) clone.startRiding(vehicle);

        var seated = riderFrame(clone, frame, pose);
        Compat.snapTo(clone, seated.x(), seated.y(), seated.z(), seated.yRot(), seated.xRot());
    }

    private static void restoreLegacyRide(ServerLevel level, Actor actor, int tick, ActorFrame frame) {
        pdx.dev.fbactors.take.ActorRide desired = null;
        int recordedTick = actor.take.recordedTick(tick);
        for (var ride : actor.take.rides) {
            if (ride.tick() <= recordedTick && (desired == null || ride.tick() >= desired.tick())) desired = ride;
        }
        var clone = actor.clone();
        if (desired == null || !desired.mount()) {
            if (clone.getVehicle() != null) clone.stopRiding();
            return;
        }
        if (desired.vehicleUuid() == null || desired.vehicleType() == null) return;
        Entity vehicle = Compat.entityByUuid(level, desired.vehicleUuid());
        if (vehicle == null || vehicle.isRemoved()) {
            var ride = desired;
            var snapshot = actor.take.spawns.stream().filter(spawn -> ride.vehicleUuid().equals(
                ActorEntityIdentity.read(spawn.nbt()))).findFirst().orElse(new ActorSpawn(ride.tick(), ride.vehicleType(),
                    frame.x(), frame.y(), frame.z(), frame.yRot(), 0, ActorEntityIdentity.write(ride.vehicleUuid())));
            vehicle = Compat.spawnEntityFromRecord(level, snapshot);
            if (vehicle == null) return;
            actor.trackSpawn(vehicle.getUUID(), tick);
            ActorWorldSimulation.track(vehicle);
        }
        if (clone.getVehicle() != vehicle) clone.startRiding(vehicle);
    }
}
