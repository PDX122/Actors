package pdx.dev.fbactors.voice;

import org.jetbrains.annotations.Nullable;


public final class VoiceMeter {

    public static final int BAR_WIDTH = 110;
    public static final int BAR_HEIGHT = 6;
    public static final int BAR_RADIUS = 3;
    public static final int PAD = 10;

    private static final long HOLD_MS = 600;
    private static final long CLIP_HOLD_MS = 1500;
    private static final long QUIET_MS = 3000;
    private static final float CLIP_PEAK = 0.98f;
    private static final float AUDIBLE_PEAK = 0.05f;
    private static final float HOT_PEAK = 0.70f;


    public record State(float level, float hold, boolean clipping, boolean quiet, String takeName,
                        int gainPercent) {
    }

    private static boolean wasRunning;
    private static float heldPeak;
    private static long heldAt;
    private static long clipUntil;
    private static long lastSoundAt;

    private VoiceMeter() {
    }


    @Nullable
    public static State sample() {
        if (!MicCapture.isRunning()) {
            wasRunning = false;
            return null;
        }
        long now = System.currentTimeMillis();
        if (!wasRunning) {
            wasRunning = true;
            heldPeak = 0.0f;
            heldAt = now;
            clipUntil = 0L;
            lastSoundAt = now;
        }
        float peak = Math.max(0.0f, Math.min(1.0f, MicCapture.lastPeak()));
        if (peak >= heldPeak) {
            heldPeak = peak;
            heldAt = now;
        } else if (now - heldAt > HOLD_MS) {
            heldPeak = peak;
            heldAt = now;
        }
        if (peak >= CLIP_PEAK) {
            clipUntil = now + CLIP_HOLD_MS;
        }
        if (peak >= AUDIBLE_PEAK) {
            lastSoundAt = now;
        }
        return new State(peak, heldPeak, now < clipUntil, now - lastSoundAt > QUIET_MS,
            currentTakeName(), currentGainPercent());
    }

    private static int currentGainPercent() {
        try {
            return Math.max(10, Math.min(200,
                Math.round(pdx.dev.fbactors.settings.FbSettings.settings().micGain * 100.0f)));
        } catch (Throwable ignored) {
            return 100;
        }
    }


    public static int colourFor(float level) {
        if (level >= CLIP_PEAK) {
            return 0xFFFF3333;
        }
        if (level >= HOT_PEAK) {
            return 0xFFFFCC33;
        }
        return 0xFF55DD55;
    }


    public interface RectSink {
        void fill(int x1, int y1, int x2, int y2, int color);
    }


    public static void roundedFill(RectSink out, int x, int y, int w, int h, int radius,
                                   int color) {
        int r = Math.max(0, Math.min(radius, h / 2));
        if (w <= 0 || h <= 0) {
            return;
        }
        if (r <= 0 || w < r * 2) {
            out.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int row = 0; row < h; row++) {
            int inset = 0;
            int edge = Math.min(row, h - 1 - row);
            if (edge < r) {
                double v = r - 0.5 - edge;
                double dx = Math.sqrt(Math.max(0.0, r * r - v * v));
                inset = (int) Math.ceil(r - dx);
                inset = Math.max(0, Math.min(r, inset));
            }
            out.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    private static String currentTakeName() {
        try {
            var take = pdx.dev.fbactors.FlashbackActors.RECORDER.current();
            if (take != null && take.name != null && !take.name.isBlank()) {
                return take.name.length() <= 16 ? take.name : take.name.substring(0, 16);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}

