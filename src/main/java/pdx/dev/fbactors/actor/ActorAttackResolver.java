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
        Actor victim=pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(target.getUUID());
        Actor source=pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(attacker.getUUID());
        if(victim!=null && CombatPlayback.alreadyAuthored(victim.take.hits,
            takeTickFor(victim,source,takeTick),attacker.getUUID(),target.getUUID())) {
            victim.combat().authorize(takeTickFor(victim,source,takeTick));
            return;
        }
        attacker.attack(target);
    }

    private static int takeTickFor(@Nullable Actor victim, @Nullable Actor attacker,
                                   int attackerTakeTick) {
        if (victim == null) {
            return attackerTakeTick;
        }
        int attackerOffset = attacker == null ? 0 : attacker.take.offsetTicks;
        return attackerTakeTick + attackerOffset - victim.take.offsetTicks;
    }

    private static boolean withinReach(ActorClonePlayer attacker, Entity target) {
        return Compat.withinAttackRange(attacker, target);
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
