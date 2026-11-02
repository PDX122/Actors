package pdx.dev.fbactors.take;

import java.util.UUID;

public record ActorEvent(int tick, Type type, UUID target,
                         float attackStrength, double fallDistance, boolean sprinting,
                         String swingAnimation, int swingDuration) {
    public ActorEvent(int tick, Type type, UUID target, float strength, double distance, boolean sprint) {
        this(tick, type, target, strength, distance, sprint, null, -1);
    }
    public ActorEvent(int tick, Type type, UUID target) {
        this(tick, type, target, -1.0f, -1.0, false);
    }

    public ActorEvent {
        attackStrength = Float.isFinite(attackStrength) ? Math.max(-1.0f, Math.min(1.0f, attackStrength)) : -1.0f;
        fallDistance = Double.isFinite(fallDistance) ? Math.max(-1.0, fallDistance) : -1.0;
        swingDuration = Math.max(-1, Math.min(1200, swingDuration));
    }

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
