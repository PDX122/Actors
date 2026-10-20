package pdx.dev.fbactors.morph;

import net.minecraft.world.entity.Pose;


public enum MorphStance {


    HUMANOID(Pose.STANDING, 75.0f, 45.0f),


    HUNCHED(Pose.CROUCHING, 60.0f, 35.0f),


    CRAWLING(Pose.CROUCHING, 50.0f, 30.0f),


    UPRIGHT_TALL(Pose.STANDING, 70.0f, 45.0f),


    BLOB(Pose.CROUCHING, 45.0f, 25.0f),


    FLYING(Pose.FALL_FLYING, 90.0f, 60.0f),


    SWIMMING(Pose.SWIMMING, 60.0f, 45.0f),


    SITTING(Pose.CROUCHING, 55.0f, 30.0f),


    QUADRUPED(Pose.STANDING, 60.0f, 35.0f),


    SNAKING(Pose.SWIMMING, 50.0f, 20.0f);


    private final Pose pose;


    private final float headYawLimit;


    private final float headPitchLimit;

    MorphStance(Pose pose, float headYawLimit, float headPitchLimit) {
        this.pose = pose;
        this.headYawLimit = headYawLimit;
        this.headPitchLimit = headPitchLimit;
    }


    public Pose pose() {
        return this.pose;
    }


    public float headYawLimit() {
        return this.headYawLimit;
    }


    public float headPitchLimit() {
        return this.headPitchLimit;
    }


    public boolean horizontal() {
        return this.pose == Pose.SWIMMING || this.pose == Pose.FALL_FLYING;
    }


    public static MorphStance byName(String name) {
        if (name == null) {
            return null;
        }
        String want = name.trim().toUpperCase(java.util.Locale.ROOT);
        for (MorphStance stance : values()) {
            if (stance.name().equals(want)) {
                return stance;
            }
        }
        return null;
    }
}

