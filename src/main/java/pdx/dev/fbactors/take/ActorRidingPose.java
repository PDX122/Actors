package pdx.dev.fbactors.take;

public final class ActorRidingPose {
    private ActorRidingPose() {}
    public static ActorFrame resolve(ActorFrame rider, ActorVehicleFrame vehicle,
                                     double seatX, double seatY, double seatZ) {
        if (vehicle == null) return rider;
        return new ActorFrame(vehicle.x() + seatX, vehicle.y() + seatY, vehicle.z() + seatZ,
            rider.yRot(), rider.xRot(), rider.yHeadRot(), rider.pose(), rider.flags());
    }
}
