package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;

/**
 * A performer-placed entity, in absolute replay ticks.
 *
 * <p>Indexed like {@link ActorRide} and read through
 * {@link ActorTake#recordedTick}: a placed boat belongs to the body that
 * placed it and moves with the clip when dragged. Position is measured off
 * the real entity at capture; variant-specific state (painting motive,
 * armor-stand pose, minecart contents, crystal beam) lives in opaque
 * {@code nbt}, null when the snapshot failed - a bare boat beats no boat.
 */
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
