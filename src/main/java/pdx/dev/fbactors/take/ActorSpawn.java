package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;

public record ActorSpawn(
    int tick,
    @Nullable String entityId,
    double x, double y, double z,
    float yaw, float pitch,
    @Nullable String nbt
) {
    public ActorSpawn {
        tick = Math.max(0, tick);
        if (entityId != null && entityId.isEmpty()) {
            entityId = null;
        }
        if (!Double.isFinite(x)) {
            x = 0.0;
        }
        if (!Double.isFinite(y)) {
            y = 0.0;
        }
        if (!Double.isFinite(z)) {
            z = 0.0;
        }
        if (!Float.isFinite(yaw)) {
            yaw = 0.0f;
        }
        if (!Float.isFinite(pitch)) {
            pitch = 0.0f;
        }
        if (nbt != null && nbt.isEmpty()) {
            nbt = null;
        }
    }
}
