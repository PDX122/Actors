package pdx.dev.fbactors.take;

import pdx.dev.fbactors.compat.Compat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.Set;


public record ActorInput(
    int tick,
    float forwardAxis,
    float strafeAxis,
    boolean jump,
    boolean sneak,
    boolean sprint,
    float yawDelta,
    float pitchDelta,
    byte selectedSlot,
    boolean useHeld,
    boolean attackHeld,
    float attackStrength,
    boolean flying,
    boolean useOffhand
) {

    public static final int SLOTS = 9;


    public static final int JUMP = 1;
    public static final int SNEAK = 1 << 1;
    public static final int SPRINT = 1 << 2;
    public static final int USE = 1 << 3;
    public static final int ATTACK = 1 << 4;
    public static final int FLYING = 1 << 5;

    public static final int USE_OFFHAND = 1 << 6;

    private static final int KNOWN = JUMP | SNEAK | SPRINT | USE | ATTACK | FLYING | USE_OFFHAND;


    public static final float MOVEMENT_EPSILON = 2.0e-3f;

    public ActorInput {
        tick = Math.max(0, tick);

        forwardAxis = Mth.clamp(forwardAxis, -1.0f, 1.0f);
        strafeAxis = Mth.clamp(strafeAxis, -1.0f, 1.0f);
        yawDelta = Mth.clamp(yawDelta, -180.0f, 180.0f);
        pitchDelta = Mth.clamp(pitchDelta, -180.0f, 180.0f);
        selectedSlot = (byte) Mth.clamp(selectedSlot, 0, SLOTS - 1);
        attackStrength = Mth.clamp(attackStrength, 0.0f, 1.0f);
    }

    public int packFlags() {
        int flags = 0;
        if (jump) flags |= JUMP;
        if (sneak) flags |= SNEAK;
        if (sprint) flags |= SPRINT;
        if (useHeld) flags |= USE;
        if (attackHeld) flags |= ATTACK;
        if (flying) flags |= FLYING;
        if (useHeld && useOffhand) flags |= USE_OFFHAND;
        return flags;
    }

    public static ActorInput unpack(int tick, int flags, float forwardAxis, float strafeAxis,
                                    float yawDelta, float pitchDelta, byte selectedSlot,
                                    float attackStrength) {
        return new ActorInput(tick,
            forwardAxis,
            strafeAxis,
            (flags & JUMP) != 0,
            (flags & SNEAK) != 0,
            (flags & SPRINT) != 0,
            yawDelta,
            pitchDelta,
            selectedSlot,
            (flags & USE) != 0,
            (flags & ATTACK) != 0,
            attackStrength,
            (flags & FLYING) != 0,
            (flags & USE) != 0 && (flags & USE_OFFHAND) != 0);
    }


    public boolean moving() {
        return Math.abs(this.forwardAxis) > MOVEMENT_EPSILON
            || Math.abs(this.strafeAxis) > MOVEMENT_EPSILON;
    }


    public boolean forward() {
        return this.forwardAxis > MOVEMENT_EPSILON;
    }

    public boolean back() {
        return this.forwardAxis < -MOVEMENT_EPSILON;
    }

    public boolean left() {
        return this.strafeAxis > MOVEMENT_EPSILON;
    }

    public boolean right() {
        return this.strafeAxis < -MOVEMENT_EPSILON;
    }


    public Vec3 movement() {
        if (!moving()) {
            return Vec3.ZERO;
        }
        return new Vec3(this.strafeAxis, 0.0, this.forwardAxis);
    }

    public ActorInput retimed(int newTick) {
        return new ActorInput(newTick, forwardAxis, strafeAxis, jump, sneak, sprint,
            yawDelta, pitchDelta, selectedSlot, useHeld, attackHeld,
            attackStrength, flying, useOffhand);
    }


    public boolean samePresses(ActorInput other) {
        return other != null
            && (packFlags() & KNOWN) == (other.packFlags() & KNOWN)
            && other.selectedSlot == selectedSlot
            && Float.compare(other.forwardAxis, forwardAxis) == 0
            && Float.compare(other.strafeAxis, strafeAxis) == 0
            && Float.compare(other.attackStrength, attackStrength) == 0;
    }


    public static Pose poseFor(boolean sneak, boolean sprint) {
        if (sneak) {
            return Pose.CROUCHING;
        }
        return sprint ? Pose.STANDING : Pose.STANDING;
    }
}

