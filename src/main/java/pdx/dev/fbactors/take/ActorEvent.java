package pdx.dev.fbactors.take;

import java.util.UUID;

public record ActorEvent(int tick, Type type, UUID target) {

    public enum Type {
        SWING_MAIN,
        SWING_OFF,

        ATTACK,
        USE_ITEM,

        HURT
    }

    public static ActorEvent hurt(int tick) {
        return new ActorEvent(tick, Type.HURT, null);
    }

    public static ActorEvent swing(int tick, boolean offHand) {
        return new ActorEvent(tick, offHand ? Type.SWING_OFF : Type.SWING_MAIN, null);
    }

    public static ActorEvent attack(int tick, UUID target) {
        return new ActorEvent(tick, Type.ATTACK, target);
    }
}

