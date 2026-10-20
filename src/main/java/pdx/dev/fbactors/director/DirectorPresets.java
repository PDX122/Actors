package pdx.dev.fbactors.director;

import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.take.CameraHeight;
import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.CameraMove;
import pdx.dev.fbactors.take.DirectorConfig;


public final class DirectorPresets {


    public record Preset(String label, String tooltip, DirectorConfig.DirectorStyle style,
                         @Nullable CameraMove move, @Nullable CameraHeight height,
                         @Nullable CameraKeyframe.ShotType shotType, int holdTicks) {
    }

    private DirectorPresets() {
    }


    public static Preset orbit() {
        return new Preset("Orbit showcase", "Slow arc around the subject at medium framing.",
            DirectorConfig.DirectorStyle.SPIELBERG, CameraMove.ORBIT, null,
            CameraKeyframe.ShotType.MEDIUM, 100);
    }


    public static Preset follow() {
        return new Preset("Follow", "Trail a walking subject; falls back to static when they stand still.",
            DirectorConfig.DirectorStyle.SPIELBERG, CameraMove.FOLLOW, CameraHeight.EYE,
            CameraKeyframe.ShotType.MEDIUM, 120);
    }


    public static Preset overShoulder() {
        return new Preset("Over-shoulder", "Locked-off interview framing, pinned over the shoulder.",
            DirectorConfig.DirectorStyle.FINCHER, CameraMove.STATIC, CameraHeight.EYE,
            CameraKeyframe.ShotType.OVER_SHOULDER, 100);
    }


    public static Preset drone() {
        return new Preset("Drone", "High wide follow; falls back to static when nobody moves.",
            DirectorConfig.DirectorStyle.VILLENEUVE, CameraMove.FOLLOW, CameraHeight.HIGH,
            CameraKeyframe.ShotType.WIDE, 140);
    }


    public static Preset crane() {
        return new Preset("Crane reveal", "Rise while holding the subject, from feet to high.",
            DirectorConfig.DirectorStyle.NOLAN, CameraMove.CRANE, null,
            CameraKeyframe.ShotType.WIDE, 120);
    }


    public static Preset pushIn() {
        return new Preset("Push in", "Slow push onto a close-up.",
            DirectorConfig.DirectorStyle.KUBRICK, CameraMove.PUSH_IN, CameraHeight.EYE,
            CameraKeyframe.ShotType.CLOSE_UP, 80);
    }


    public static Preset[] all() {
        return new Preset[] {orbit(), follow(), overShoulder(), drone(), crane(), pushIn()};
    }
}

