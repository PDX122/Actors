package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A discrete right-click the performer did, in absolute replay ticks.
 *
 * <p>Indexed like {@link ActorRide} and read through
 * {@link ActorTake#recordedTick}: a use belongs to the body that did it and
 * moves with the clip when dragged. Separate from {@link ActorEvent} because
 * it carries position/target/hand, and separate from {@link ActorInput}
 * because it is an edge (one click) not a level (held button).
 */
public record ActorUse(
    int tick,
    Kind kind,
    int x, int y, int z,
    @Nullable UUID target,
    boolean offHand
) {
    public enum Kind {
        BLOCK,
        ENTITY,
        AIR
    }

    public ActorUse {
        tick = Math.max(0, tick);
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (kind != Kind.BLOCK) {
            x = -1;
            y = -1;
            z = -1;
        }
        if (kind != Kind.ENTITY) {
            target = null;
        }
    }

    public static ActorUse block(int tick, int x, int y, int z, boolean offHand) {
        return new ActorUse(tick, Kind.BLOCK, x, y, z, null, offHand);
    }

    public static ActorUse entity(int tick, UUID target, boolean offHand) {
        return new ActorUse(tick, Kind.ENTITY, -1, -1, -1, target, offHand);
    }

    public static ActorUse air(int tick, boolean offHand) {
        return new ActorUse(tick, Kind.AIR, -1, -1, -1, null, offHand);
    }
}
