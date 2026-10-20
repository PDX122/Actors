package pdx.dev.fbactors.emote;

import java.util.List;


public record EmoteKeyframe(

    int tick,
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
    String interpolation,
    String stance
) {


    public static final String LINEAR = "linear";


    public static final String SMOOTH = "smooth";


    public static final String CATMULL = "catmull";


    public static final List<String> MODES = List.of(LINEAR, SMOOTH, CATMULL);


    public static final EmoteKeyframe IDENTITY = new EmoteKeyframe(
        0,
        0.0, 0.0, 0.0,
        0.0f, 0.0f,
        0.0f, 0.0f,
        0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f,
        LINEAR, null);


    public EmoteKeyframe {
        interpolation = EmoteMath.normaliseMode(interpolation);
        if (stance != null && !stance.isBlank()) {


            stance = stance.strip().toLowerCase(java.util.Locale.ROOT);
            EmoteStance.parse(stance);
        } else {
            stance = null;
        }
    }


    public String mode() {
        return this.interpolation;
    }


    public String stance() {
        return this.stance;
    }


    public EmoteKeyframe atTick(int newTick) {
        return new EmoteKeyframe(newTick, this.xOffset, this.yOffset, this.zOffset,
            this.bodyYaw, this.bodyPitch, this.headYaw, this.headPitch,
            this.leftArmPitch, this.leftArmYaw, this.leftArmRoll,
            this.rightArmPitch, this.rightArmYaw, this.rightArmRoll,
            this.leftLegPitch, this.leftLegYaw, this.leftLegRoll,
            this.rightLegPitch, this.rightLegYaw, this.rightLegRoll,
            this.interpolation, this.stance);
    }


    public EmoteKeyframe withMode(String newMode) {
        return new EmoteKeyframe(this.tick, this.xOffset, this.yOffset, this.zOffset,
            this.bodyYaw, this.bodyPitch, this.headYaw, this.headPitch,
            this.leftArmPitch, this.leftArmYaw, this.leftArmRoll,
            this.rightArmPitch, this.rightArmYaw, this.rightArmRoll,
            this.leftLegPitch, this.leftLegYaw, this.leftLegRoll,
            this.rightLegPitch, this.rightLegYaw, this.rightLegRoll,
            newMode, this.stance);
    }


    public boolean isNeutral() {
        return this.xOffset == 0.0 && this.yOffset == 0.0 && this.zOffset == 0.0
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
        return Math.abs(v) < 0.005f;
    }
}

