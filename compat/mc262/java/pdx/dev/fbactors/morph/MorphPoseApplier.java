package pdx.dev.fbactors.morph;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;

public final class MorphPoseApplier {
    private MorphPoseApplier() {}
    public static void apply(LivingEntity mob, String pose, boolean crouching) {
        pose = MorphPoses.resolve(pose,crouching,mob instanceof TamableAnimal);
        if(mob instanceof MorphActionAccess state) state.fbactors$action(pose);
        boolean sitting = "sitting".equals(pose), relaxing = "relaxing".equals(pose);
        if (mob instanceof TamableAnimal tame) {
            tame.setOrderedToSit(sitting);
            tame.setInSittingPose(sitting);
        }
        if (mob instanceof Cat cat) {
            cat.setLying(relaxing);
            if(cat instanceof CatMorphPoseAccess blend) blend.fbactors$relaxing(relaxing);
        }
        if (!"auto".equals(pose)) {
            boolean crouch="crouching".equals(pose);
            mob.setPose(crouch?net.minecraft.world.entity.Pose.CROUCHING:net.minecraft.world.entity.Pose.STANDING);
            mob.setShiftKeyDown(crouch);
            mob.setSwimming(false);
            mob.setSprinting("running".equals(pose));
        }
        if(MorphPoses.animated(pose)) {
            float speed=MorphPoses.speed(pose);
            double tick=pdx.dev.fbactors.morph.MorphPreviews.isPreviewing()?mob.tickCount:pdx.dev.fbactors.camera.BoatSubjects.clock();
            if(!Double.isFinite(tick)) tick=mob.tickCount;
            WalkDriver.driveTo(mob,speed,speed,(float)(tick*speed));
        }
        if (sitting || relaxing || "idle".equals(pose)) WalkDriver.driveTo(mob, 0, 0, 0);
    }
}
