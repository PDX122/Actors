package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ActorUse(
    int tick,
    Kind kind,
    int x, int y, int z,
    @Nullable UUID target,
    boolean offHand,
    @Nullable String itemId,
    @Nullable String itemNbt
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

    public ActorUse(int tick, Kind kind, int x, int y, int z, UUID target, boolean offHand) {
        this(tick, kind, x, y, z, target, offHand, null, null);
    }

    public ActorUse withItem(String id, String nbt) {
        return new ActorUse(tick, kind, x, y, z, target, offHand, id, nbt);
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
