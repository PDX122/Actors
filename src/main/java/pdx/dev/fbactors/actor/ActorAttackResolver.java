package pdx.dev.fbactors.actor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.swing.InvulnerabilityAccess;
import pdx.dev.fbactors.take.ActorHit;

import java.util.Optional;
import java.util.UUID;


final class ActorAttackResolver {

    private ActorAttackResolver() {
    }


    private static final double REACH_TOLERANCE = 3.0;


    @Nullable
    static Entity resolve(ActorClonePlayer attacker, @Nullable UUID recorded) {
        if (recorded == null) {
            return null;
        }
        if (!(attacker.level() instanceof ServerLevel level)) {
            return null;
        }

        Entity target = Compat.entityByUuid(level, recorded);
        if (target == null || target == attacker || !target.isAlive()) {
            return null;
        }
        if (!withinReach(attacker, target)) {
            return null;
        }
        if (blocked(attacker, target)) {
            return null;
        }
        return target;
    }


    static void strike(ActorClonePlayer attacker, Entity target, int takeTick) {
        float healthBefore = healthOf(target);
        float attackerHealthBefore = attacker.getHealth();


        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        if (dx * dx + dz * dz < 1.0e-6) {
            dx = 0.0;
            dz = 1.0;
        }

        Actor struckActor = pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(target.getUUID());
        Actor attackerActor = pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(attacker.getUUID());


        int victimTick = takeTickFor(struckActor, attackerActor, takeTick);


        if (struckActor != null && struckActor.take.hasHitAt(victimTick)) {
            Compat.swing(attacker, net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }


        int hostTick = -1;
        if (struckActor == null && target instanceof LivingEntity
            && pdx.dev.fbactors.settings.FbSettings.hostDamageEnabled()
            && HostManager.isHost(target)) {
            hostTick = HostManager.hostTickFor(attackerActor, takeTick);
            if (HostManager.hasHitAt(target.getUUID(), hostTick)) {
                Compat.swing(attacker, net.minecraft.world.InteractionHand.MAIN_HAND);
                return;
            }
        }

        float charge = attacker.getAttackStrengthScale(0.5f);


        Vec3 velocityBefore = target.getDeltaMovement();

        attacker.takeCritStash();
        attacker.attack(target);
        Vec3 impulse = target.getDeltaMovement().subtract(velocityBefore);
        byte crit = attacker.takeCritStash();

        float dealt = Math.max(0.0f, healthBefore - healthOf(target));
        record(struckActor, target, victimTick, hostTick, dealt, charge, dx, dz, impulse, crit);


        if (struckActor != null && dealt > 0.0f) {
            struckActor.sim().suppressStrike(victimTick + 1);
        }


        if (struckActor == null && hostTick >= 0 && dealt > 0.0f) {
            HostManager.suppressStrike(target.getUUID(), hostTick + 1);
        }


        if (struckActor != null) {
            struckActor.sim().noteStrikeDamage(dealt);
            if (dealt > 0.0f) {
                struckActor.sim().noteStrikeImpulse(impulse);
            }
        }


        if (struckActor != null && dealt > 0.0f) {
            struckActor.sim().diverge(victimTick);
        }


        if (struckActor == null && hostTick >= 0 && dealt > 0.0f) {
            HostManager.diverge(target.getUUID(), hostTick);
        }

        if (attackerActor != null && attacker.getHealth() < attackerHealthBefore) {
            attackerActor.sim().diverge(takeTickFor(attackerActor, attackerActor, takeTick));
        }
    }


    private static int takeTickFor(@Nullable Actor victim, @Nullable Actor attacker,
                                   int attackerTakeTick) {
        if (victim == null) {
            return attackerTakeTick;
        }
        int attackerOffset = attacker == null ? 0 : attacker.take.offsetTicks;
        return attackerTakeTick + attackerOffset - victim.take.offsetTicks;
    }

    private static void record(@Nullable Actor actor, Entity target, int victimTick,
                               int hostTick,
                               float dealt, float charge, double dx, double dz,
                               Vec3 impulse, byte crit) {

        if (actor == null) {
            recordHost(target, hostTick, dealt, charge, dx, dz, impulse, crit);
            return;
        }


        if (dealt <= 0.0f) {
            return;
        }


        if (actor.take.hasHitAt(victimTick)) {
            return;
        }

        actor.take.recordHit(new ActorHit(victimTick, dealt, dx, dz, charge, target.getUUID(),
            dealt > 0.0f ? impulse : null, crit));
        if (pdx.dev.fbactors.FlashbackActors.loadedReplayIdOrNull() != null) {
            pdx.dev.fbactors.take.TakeStore.saveThrottled(
                pdx.dev.fbactors.FlashbackActors.loadedReplayIdOrNull(), actor.take);
        }
        pdx.dev.fbactors.FlashbackActors.LOGGER.info(
            "Actor '{}' struck {} for {} damage at take tick {} (charge {}, crit {}, impulse {})",
            actor.take.name, target.getName().getString(), dealt, victimTick, charge, crit,
            impulse);
    }


    private static void recordHost(Entity target, int hostTick,
                                   float dealt, float charge, double dx, double dz,
                                   Vec3 impulse, byte crit) {
        if (hostTick < 0) {
            return;
        }
        if (dealt <= 0.0f) {
            return;
        }
        if (HostManager.hasHitAt(target.getUUID(), hostTick)) {
            return;
        }
        HostManager.recordHit(target.getUUID(), new ActorHit(hostTick, dealt, dx, dz, charge,
            target.getUUID(), dealt > 0.0f ? impulse : null, crit));
    }


    private static float healthOf(Entity entity) {
        return entity instanceof LivingEntity living ? living.getHealth() : 0.0f;
    }


    private static boolean withinReach(ActorClonePlayer attacker, Entity target) {
        double range = (float) attacker.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)
            + REACH_TOLERANCE;
        return attacker.getEyePosition().distanceToSqr(target.getBoundingBox().getCenter())
            <= range * range;
    }


    private static boolean blocked(ActorClonePlayer attacker, Entity target) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 from = attacker.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();

        BlockHitResult blockHit = level.clip(
            new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
        if (blockHit.getType() == HitResult.Type.MISS) {
            return false;
        }


        return from.distanceToSqr(blockHit.getLocation())
            <= from.distanceToSqr(to);
    }


    @Nullable
    static Optional<Vec3> intersects(ActorClonePlayer attacker, Entity target) {
        Vec3 from = attacker.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        AABB hitbox = target.getBoundingBox().inflate(target.getPickRadius());
        return hitbox.clip(from, to);
    }


    static boolean invulnerable(Entity entity) {
        return ((InvulnerabilityAccess) entity).fbactors$invulnerableTime() > 0;
    }
}
