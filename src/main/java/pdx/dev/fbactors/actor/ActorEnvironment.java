package pdx.dev.fbactors.actor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

public final class ActorEnvironment {
    private ActorEnvironment() {}
    public static void react(ServerLevel level, Actor actor, int tick) {
        var settings = pdx.dev.fbactors.settings.FbSettings.settings();
        if (actor.clone() != null && actor.clone().getVehicle() instanceof BoatPhysics.Boat
            && (settings.boatSpeed != 1 || settings.boatSteering != 1 || settings.boatGrip != 1 || settings.boatBounce != 0)) {
            if (actor.sim().usesRecordedBaseline(tick)) {
                actor.sim().diverge(tick);
                BoatPhysics.forget(actor.take.id);
            }
        }
        if (!settings.environmentForces
            || !actor.sim().usesRecordedBaseline(tick) || actor.clone() == null) return;
        var clone = actor.clone();
        var body = clone.getVehicle() == null ? clone : clone.getVehicle();
        var box = body.getBoundingBox().inflate(0.02, 0.1, 0.02);
        for (var pos : BlockPos.betweenClosed((int)Math.floor(box.minX), (int)Math.floor(box.minY), (int)Math.floor(box.minZ),
            (int)Math.floor(box.maxX), (int)Math.floor(box.maxY), (int)Math.floor(box.maxZ))) {
            var id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            if (id != null && id.toString().equals("minecraft:bubble_column")) {
                actor.sim().diverge(tick);
                BoatPhysics.forget(actor.take.id);
                return;
            }
        }
    }
}
