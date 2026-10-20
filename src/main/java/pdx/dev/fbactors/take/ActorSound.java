package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;


public record ActorSound(
    int tick,
    @Nullable String soundId,
    double x,
    double y,
    double z,
    float volume,
    float pitch,
    @Nullable String source
) {


    private static final float MAX_VOLUME = 8.0f;


    private static final float MAX_PITCH = 2.0f;

    public ActorSound {
        tick = Math.max(0, tick);
        if (soundId != null && soundId.isEmpty()) {
            soundId = null;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            x = 0.0;
            y = 0.0;
            z = 0.0;
        }
        volume = Float.isFinite(volume) ? Math.max(0.0f, Math.min(volume, MAX_VOLUME)) : 1.0f;
        pitch = Float.isFinite(pitch) ? Math.max(0.0f, Math.min(pitch, MAX_PITCH)) : 1.0f;
        if (source != null && source.isEmpty()) {
            source = null;
        }
    }
}

