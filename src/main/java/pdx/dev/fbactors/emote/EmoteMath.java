package pdx.dev.fbactors.emote;

import net.minecraft.util.Mth;


public final class EmoteMath {

    private EmoteMath() {
    }


    public static float smoothstep(float t) {
        float x = clamp01(t);
        return x * x * (3.0f - 2.0f * x);
    }


    public static float easeInOutSine(float t) {
        return (1.0f - (float) Math.cos(Math.PI * clamp01(t))) * 0.5f;
    }


    public static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float x = clamp01(t);
        float t2 = x * x;
        float t3 = t2 * x;

        return 0.5f * ((2.0f * p1)
            + (-p0 + p2) * x
            + (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2
            + (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3);
    }


    public static float shortestAngleDelta(float from, float to) {
        return Mth.wrapDegrees(to - from);
    }


    public static float lerpAngle(float from, float to, float t) {
        return from + shortestAngleDelta(from, to) * t;
    }


    public static float ease(String mode, float t) {
        if (mode == null) {
            return smoothstep(t);
        }
        return switch (mode) {
            case EmoteKeyframe.LINEAR -> clamp01(t);
            case EmoteKeyframe.CATMULL -> smoothstep(t);
            default -> easeInOutSine(t);
        };
    }


    public static String normaliseMode(String mode) {
        if (mode == null || mode.isBlank()) {


            return EmoteKeyframe.SMOOTH;
        }
        String normalised = mode.trim().toLowerCase(java.util.Locale.ROOT);
        for (String known : EmoteKeyframe.MODES) {
            if (known.equals(normalised)) {
                return known;
            }
        }
        throw new IllegalArgumentException(
            "Unknown emote interpolation '" + mode + "'; expected one of " + EmoteKeyframe.MODES);
    }


    public static float clamp01(float t) {
        if (t < 0.0f) {
            return 0.0f;
        }
        return t > 1.0f ? 1.0f : t;
    }


    public static int wrapTick(int tick, int length) {
        if (length <= 0) {
            return 0;
        }
        return Mth.positiveModulo(tick, length);
    }
}


