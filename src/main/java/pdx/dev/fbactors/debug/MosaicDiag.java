package pdx.dev.fbactors.debug;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tessera/actor-mosaic tracing.
 *
 * <p>Master switch {@link #ENABLED} reads the {@code fbactors.diagMosaic}
 * system property (default off). Diagnostic builds flip the default on so a
 * repro log captures the masked-mosaic pipeline: beginFrame gating, capture
 * bail points, material registration, and per-mask render decisions.
 */
public final class MosaicDiag {
    public static final boolean ENABLED =
        Boolean.parseBoolean(System.getProperty("fbactors.diagMosaic", "false"));
    public static final String TAG = "[FBACTORS-MOSAIC]";

    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

    private MosaicDiag() {}

    public static void log(String format, Object... args) {
        if (!ENABLED) return;
        try {
            pdx.dev.fbactors.FlashbackActors.LOGGER.info(TAG + " " + String.format(Locale.ROOT, format, args));
        } catch (Throwable ignored) {
        }
    }

    /** At most one line per key per second. For per-draw / per-frame call sites. */
    public static void logRare(String key, String format, Object... args) {
        if (!ENABLED) return;
        try {
            long now = System.nanoTime();
            Long last = LAST.get(key);
            if (last != null && now - last < 1_000_000_000L) return;
            LAST.put(key, now);
            pdx.dev.fbactors.FlashbackActors.LOGGER.info(TAG + " " + String.format(Locale.ROOT, format, args));
        } catch (Throwable ignored) {
        }
    }
}
