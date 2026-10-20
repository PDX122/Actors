package pdx.dev.fbactors.emote;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import pdx.dev.fbactors.compat.Compat;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;


public final class EmoteApplier {


    private static final Map<Entity, EmotePose> LAST_APPLIED =
        Collections.synchronizedMap(new IdentityHashMap<>());

    private EmoteApplier() {
    }


    public static boolean apply(LivingEntity entity, EmotePose pose, int takeTick) {
        if (entity == null || entity.isRemoved() || pose == null) {
            return false;
        }

        EmotePose previous = LAST_APPLIED.get(entity);
        if (previous == pose) {

            return false;
        }
        LAST_APPLIED.put(entity, pose);

        if (pose.isIdentity()) {


            return false;
        }

        applyHeadAndBody(entity, pose);
        applyStance(entity, pose);
        applyOffsets(entity, pose, takeTick);
        return true;
    }


    public static void endTick() {
        LAST_APPLIED.clear();
    }


    public static void forget(Entity entity) {
        if (entity != null) {
            LAST_APPLIED.remove(entity);
        }
    }


    private static void applyHeadAndBody(LivingEntity entity, EmotePose pose) {
        if (pose.headYaw() != 0.0f) {
            entity.setYHeadRot(entity.getYHeadRot() + pose.headYaw());
        }
        if (pose.headPitch() != 0.0f) {


            entity.setXRot(Mth.clamp(entity.getXRot() + pose.headPitch(), -90.0f, 90.0f));
        }
        if (pose.bodyYaw() != 0.0f) {


            entity.setYBodyRot(entity.getYRot() + pose.bodyYaw());
        }
    }


    private static void applyStance(LivingEntity entity, EmotePose pose) {
        Pose stance = stanceFor(pose);
        if (stance != null && entity.getPose() != stance) {
            entity.setPose(stance);
        }
    }


    public static Pose stanceFor(EmotePose pose) {
        if (pose.stance() != null) {
            return pose.stance();
        }
        float bodyPitch = pose.bodyPitch();
        float headPitch = pose.headPitch();
        boolean pitchedForward = bodyPitch > 45.0f || headPitch > 45.0f;
        boolean low = pose.yOffset() < -0.5;

        if (low && pitchedForward) {
            return Pose.SWIMMING;
        }
        if (low && bodyPitch > 12.0f) {
            return Pose.CROUCHING;
        }


        return null;
    }


    private static void applyOffsets(LivingEntity entity, EmotePose pose, int takeTick) {
        if (pose.xOffset() == 0.0 && pose.yOffset() == 0.0 && pose.zOffset() == 0.0) {
            return;
        }


        if (!Double.isFinite(pose.xOffset())
            || !Double.isFinite(pose.yOffset())
            || !Double.isFinite(pose.zOffset())) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                "Emote pose at take tick {} has a non-finite offset; ignoring it", takeTick);
            return;
        }
        Compat.snapTo(entity,
            entity.getX() + pose.xOffset(),
            entity.getY() + pose.yOffset(),
            entity.getZ() + pose.zOffset(),
            entity.getYRot(),
            entity.getXRot());
    }
}


