package pdx.dev.fbactors.take;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


public record CameraKeyframe(
    int tick,
    double x, double y, double z,
    float yaw, float pitch,
    float fov,
    ShotType shotType,
    @Nullable UUID targetActorId,
    byte easing
) {


    public static final byte EASING_LINEAR = 0;


    public static final byte EASING_SMOOTH = 1;


    public static final byte EASING_CATMULL = 2;


    public static final float MIN_FOV = 1.0f;


    public static final float MAX_FOV = 179.0f;


    public enum ShotType {
        WIDE,
        MEDIUM,
        CLOSE_UP,
        OVER_SHOULDER,
        TRACKING
    }

    public CameraKeyframe {
        tick = Math.max(0, tick);
        x = finite(x);
        y = finite(y);
        z = finite(z);

        yaw = Mth.wrapDegrees(finite(yaw));
        pitch = Mth.clamp(finite(pitch), -90.0f, 90.0f);
        fov = Mth.clamp(finite(fov), MIN_FOV, MAX_FOV);
        if (shotType == null) {
            shotType = ShotType.MEDIUM;
        }
        if (easing < EASING_LINEAR || easing > EASING_CATMULL) {
            easing = EASING_SMOOTH;
        }
    }


    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }


    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }


    public CameraKeyframe withTick(int newTick) {
        return new CameraKeyframe(newTick, x, y, z, yaw, pitch, fov, shotType, targetActorId, easing);
    }


    public CameraKeyframe withEasing(byte newEasing) {
        return new CameraKeyframe(tick, x, y, z, yaw, pitch, fov, shotType, targetActorId, newEasing);
    }


    public static CameraKeyframe lerp(CameraKeyframe a, CameraKeyframe b, float t) {
        float k = Mth.clamp(t, 0.0f, 1.0f);
        return new CameraKeyframe(
            a.tick,
            Mth.lerp(k, a.x, b.x),
            Mth.lerp(k, a.y, b.y),
            Mth.lerp(k, a.z, b.z),
            a.yaw + Mth.wrapDegrees(b.yaw - a.yaw) * k,
            Mth.lerp(k, a.pitch, b.pitch),
            Mth.lerp(k, a.fov, b.fov),
            k < 0.5f ? a.shotType : b.shotType,
            a.targetActorId != null ? a.targetActorId : b.targetActorId,
            b.easing);
    }
}

