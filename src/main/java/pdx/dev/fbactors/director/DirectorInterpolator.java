package pdx.dev.fbactors.director;

import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.CameraTrack;
import pdx.dev.fbactors.take.DirectorConfig;


public final class DirectorInterpolator {

    private DirectorInterpolator() {
    }


    public static void apply(CameraTrack track, DirectorStyle style, DirectorConfig config) {
        if (track.isEmpty()) {
            return;
        }
        int lead = Math.round(config.anticipationTicks() > 0.0f
            ? config.anticipationTicks() : style.anticipationTicks());

        for (int i = 0; i < track.keyframes.size(); i++) {
            CameraKeyframe keyframe = track.keyframes.get(i);
            int tick = Math.max(0, keyframe.tick() - lead);
            track.keyframes.set(i, keyframe.withTick(tick).withEasing(style.easing()));
        }


        track.sort();
    }


    @Nullable
    public static CameraKeyframe sample(CameraTrack track, int tick) {
        return track.at(tick);
    }
}

