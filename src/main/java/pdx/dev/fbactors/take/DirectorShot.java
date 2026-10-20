package pdx.dev.fbactors.take;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


public record DirectorShot(
    UUID subjectId,
    @Nullable CameraMove move,
    @Nullable CameraKeyframe.ShotType shotType,
    @Nullable CameraHeight height,
    int holdTicks,
    int startTick
) {

    public DirectorShot {
        if (subjectId == null) {
            throw new IllegalArgumentException("A director shot needs a subject");
        }
        holdTicks = Mth.clamp(holdTicks,
            DirectorConfig.MIN_HOLD_TICKS, DirectorConfig.MAX_HOLD_TICKS);
        startTick = Math.max(0, startTick);
    }


    public static DirectorShot of(UUID subjectId, int startTick) {
        return new DirectorShot(subjectId, null, null, null, 60, startTick);
    }
}

