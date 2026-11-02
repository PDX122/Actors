package pdx.dev.fbactors.take;

import java.util.List;

public final class ActorPathTranslation {
    private ActorPathTranslation() {}
    public static List<ActorFrame> translate(List<ActorFrame> frames, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Translation must be finite");
        return frames.stream().map(f -> new ActorFrame(f.x()+x, f.y()+y, f.z()+z,
            f.yRot(), f.xRot(), f.yHeadRot(), f.pose(), f.flags())).toList();
    }
}
