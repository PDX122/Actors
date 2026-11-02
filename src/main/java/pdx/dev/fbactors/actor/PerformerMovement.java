package pdx.dev.fbactors.actor;

import net.minecraft.world.entity.LivingEntity;
import pdx.dev.fbactors.compat.Compat;
import java.util.UUID;

public final class PerformerMovement {
    public record Snapshot(UUID player, float forward, float strafe, boolean jump,
                           net.minecraft.world.phys.Vec3 velocity, UUID vehicle,
                           net.minecraft.world.phys.Vec3 vehicleVelocity,
                           float leftPrevious, float left, float rightPrevious, float right,
                           boolean leftPaddle, boolean rightPaddle) {}
    private static volatile Snapshot latest;
    private PerformerMovement() {}

    public static void capture(LivingEntity player) {

        var vehicle = player.getVehicle();
        var boat = vehicle instanceof BoatPhysics.Boat paddles ? paddles : null;
        latest = new Snapshot(player.getUUID(), player.zza / 0.98f, player.xxa / 0.98f,
            Compat.jumpHeld(player), player.getDeltaMovement(), vehicle == null ? null : vehicle.getUUID(),
            vehicle == null ? net.minecraft.world.phys.Vec3.ZERO : vehicle.getDeltaMovement(),
            boat == null ? Float.NaN : boat.fbactors$rowingTime(0, 0),
            boat == null ? Float.NaN : boat.fbactors$rowingTime(0, 1),
            boat == null ? Float.NaN : boat.fbactors$rowingTime(1, 0),
            boat == null ? Float.NaN : boat.fbactors$rowingTime(1, 1),
            boat != null && boat.fbactors$paddle(0), boat != null && boat.fbactors$paddle(1));
    }

    public static Snapshot forPlayer(UUID id) {
        Snapshot snapshot = latest;
        return snapshot != null && snapshot.player().equals(id) ? snapshot : null;
    }

    public static void clear() { latest = null; }
}
