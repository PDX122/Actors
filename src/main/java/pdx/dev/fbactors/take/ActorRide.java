package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;


public record ActorRide(
    int tick,
    boolean mount,
    @Nullable String vehicleType,
    @Nullable UUID vehicleUuid
) {

    public ActorRide {
        tick = Math.max(0, tick);
        if (vehicleType != null && vehicleType.isEmpty()) {
            vehicleType = null;
        }
    }

    public static ActorRide mount(int tick, String vehicleType, UUID vehicleUuid) {
        return new ActorRide(tick, true, vehicleType, vehicleUuid);
    }

    public static ActorRide dismount(int tick) {
        return new ActorRide(tick, false, null, null);
    }
}

