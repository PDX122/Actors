package pdx.dev.fbactors.actor;

import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;

public final class ActorImpulses {
    private ActorImpulses() {}
    public static void vehicleChanged(net.minecraft.world.entity.Entity vehicle, net.minecraft.world.phys.Vec3 before) {
        if (!pdx.dev.fbactors.settings.FbSettings.settings().boatCollisionResponse) return;
        releaseVehicle(vehicle, before);
    }
    private static void releaseVehicle(net.minecraft.world.entity.Entity vehicle, net.minecraft.world.phys.Vec3 before) {
        if (!BoatPhysics.driven(vehicle) || before == null
            || vehicle.getDeltaMovement().subtract(before).lengthSqr() <= 1.0e-12) return;
        release(vehicle.getFirstPassenger());
        Compat.syncMotion(vehicle);
    }
    public static void environmentChanged(net.minecraft.world.entity.Entity entity, net.minecraft.world.phys.Vec3 before) {
        if (!pdx.dev.fbactors.settings.FbSettings.settings().environmentForces || before == null
            || entity.getDeltaMovement().subtract(before).lengthSqr() <= 1.0e-12) return;
        if (entity instanceof ActorClonePlayer) { release(entity); Compat.syncMotion(entity); }
        else releaseVehicle(entity, before);
    }
    public static void release(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof ActorClonePlayer clone)) return;
        var actor = FlashbackActors.ACTORS.byUuid(clone.getUUID());
        if (actor == null) return;
        int tick = (int) (actor.takeTickFor(FlashbackActors.replayTick()) * actor.take.getSpeedMultiplier());
        actor.sim().diverge(tick);
        BoatPhysics.forget(actor.take.id);
    }
    public static void changed(ActorClonePlayer clone) {
        Actor actor = FlashbackActors.ACTORS.byUuid(clone.getUUID());
        if (actor == null) return;
        int tick = (int) ((FlashbackActors.replayTick() - actor.take.offsetTicks) * actor.take.getSpeedMultiplier());
        actor.combat().impulse(clone);
        Compat.syncMotion(clone);
    }
}
