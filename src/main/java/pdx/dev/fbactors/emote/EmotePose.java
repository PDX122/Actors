package pdx.dev.fbactors.emote;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.Nullable;


public record EmotePose(
    double xOffset,
    double yOffset,
    double zOffset,
    float bodyYaw,
    float bodyPitch,
    float headYaw,
    float headPitch,
    float leftArmPitch,
    float leftArmYaw,
    float leftArmRoll,
    float rightArmPitch,
    float rightArmYaw,
    float rightArmRoll,
    float leftLegPitch,
    float leftLegYaw,
    float leftLegRoll,
    float rightLegPitch,
    float rightLegYaw,
    float rightLegRoll,
    @Nullable Pose stance
) {


    public static final EmotePose IDENTITY = new EmotePose(
        0.0, 0.0, 0.0,
        0.0f, 0.0f,
        0.0f, 0.0f,
        0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f,
        null);


    private static final double REST_EPSILON = 0.005;


    public static EmotePose identity() {
        return IDENTITY;
    }


    public static EmotePose of(EmoteKeyframe keyframe) {
        return new EmotePose(keyframe.xOffset(), keyframe.yOffset(), keyframe.zOffset(),
            keyframe.bodyYaw(), keyframe.bodyPitch(),
            keyframe.headYaw(), keyframe.headPitch(),
            keyframe.leftArmPitch(), keyframe.leftArmYaw(), keyframe.leftArmRoll(),
            keyframe.rightArmPitch(), keyframe.rightArmYaw(), keyframe.rightArmRoll(),
            keyframe.leftLegPitch(), keyframe.leftLegYaw(), keyframe.leftLegRoll(),
            keyframe.rightLegPitch(), keyframe.rightLegYaw(), keyframe.rightLegRoll(),
            EmoteStance.parse(keyframe.stance()));
    }


    public EmotePose blend(EmotePose to, float t) {
        if (!Float.isFinite(t)) {
            return this;
        }
        float k = EmoteMath.clamp01(t);


        if (k <= 0.0f) {
            return this;
        }
        if (k >= 1.0f) {
            return to;
        }

        return new EmotePose(
            Mth.lerp(k, this.xOffset, to.xOffset),
            Mth.lerp(k, this.yOffset, to.yOffset),
            Mth.lerp(k, this.zOffset, to.zOffset),

            angle(this.bodyYaw, to.bodyYaw, k),
            Mth.lerp(k, this.bodyPitch, to.bodyPitch),
            angle(this.headYaw, to.headYaw, k),
            Mth.lerp(k, this.headPitch, to.headPitch),

            Mth.lerp(k, this.leftArmPitch, to.leftArmPitch),
            angle(this.leftArmYaw, to.leftArmYaw, k),
            Mth.lerp(k, this.leftArmRoll, to.leftArmRoll),

            Mth.lerp(k, this.rightArmPitch, to.rightArmPitch),
            angle(this.rightArmYaw, to.rightArmYaw, k),
            Mth.lerp(k, this.rightArmRoll, to.rightArmRoll),

            Mth.lerp(k, this.leftLegPitch, to.leftLegPitch),
            angle(this.leftLegYaw, to.leftLegYaw, k),
            Mth.lerp(k, this.leftLegRoll, to.leftLegRoll),

            Mth.lerp(k, this.rightLegPitch, to.rightLegPitch),
            angle(this.rightLegYaw, to.rightLegYaw, k),
            Mth.lerp(k, this.rightLegRoll, to.rightLegRoll),


            to.stance != null ? to.stance : this.stance);
    }


    private static float angle(float from, float to, float t) {
        return from + EmoteMath.shortestAngleDelta(from, to) * t;
    }


    public boolean isIdentity() {
        return Math.abs(this.xOffset) < REST_EPSILON
            && Math.abs(this.yOffset) < REST_EPSILON
            && Math.abs(this.zOffset) < REST_EPSILON
            && nearZero(this.bodyYaw) && nearZero(this.bodyPitch)
            && nearZero(this.headYaw) && nearZero(this.headPitch)
            && nearZero(this.leftArmPitch) && nearZero(this.leftArmYaw)
            && nearZero(this.leftArmRoll) && nearZero(this.rightArmPitch)
            && nearZero(this.rightArmYaw) && nearZero(this.rightArmRoll)
            && nearZero(this.leftLegPitch) && nearZero(this.leftLegYaw)
            && nearZero(this.leftLegRoll) && nearZero(this.rightLegPitch)
            && nearZero(this.rightLegYaw) && nearZero(this.rightLegRoll)
            && this.stance == null;
    }

    private static boolean nearZero(float v) {
        return Math.abs(v) < REST_EPSILON;
    }
}


