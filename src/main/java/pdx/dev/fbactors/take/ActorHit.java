package pdx.dev.fbactors.take;

import net.minecraft.util.Mth;

import java.util.UUID;


public record ActorHit(
    int tick,
    float damage,
    double dirX,
    double dirZ,
    float attackStrength,
    @org.jetbrains.annotations.Nullable UUID target,
    @org.jetbrains.annotations.Nullable net.minecraft.world.phys.Vec3 impulse,
    byte crit
) {


    public static final byte CRIT_NONE = 0;

    public static final byte CRIT_HIT = 1;

    public static final byte CRIT_MAGIC = 2;


    public boolean isCritical() {
        return (crit & CRIT_HIT) != 0;
    }


    public boolean isMagicCritical() {
        return (crit & CRIT_MAGIC) != 0;
    }


    public ActorHit(int tick, float damage, double dirX, double dirZ,
                    float attackStrength,
                    @org.jetbrains.annotations.Nullable UUID target) {
        this(tick, damage, dirX, dirZ, attackStrength, target, null, CRIT_NONE);
    }

    public ActorHit {
        tick = Math.max(0, tick);

        damage = Float.isFinite(damage) ? Math.max(0.0f, damage) : 0.0f;
        attackStrength = Mth.clamp(Float.isFinite(attackStrength) ? attackStrength : 1.0f,
            0.0f, 1.0f);


        double lengthSquared = dirX * dirX + dirZ * dirZ;
        if (!Double.isFinite(lengthSquared) || lengthSquared < 1.0e-9) {
            dirX = 0.0;
            dirZ = 1.0;
        } else {
            double length = Math.sqrt(lengthSquared);
            dirX /= length;
            dirZ /= length;
        }


        if (impulse != null && (!Double.isFinite(impulse.x)
            || !Double.isFinite(impulse.y) || !Double.isFinite(impulse.z))) {
            impulse = null;
        }


        crit = (byte) (crit & (CRIT_HIT | CRIT_MAGIC));
    }


    public static ActorHit legacy(int tick, float damage, double dirX, double dirZ) {
        return new ActorHit(tick, damage, dirX, dirZ, 1.0f, null);
    }
}
