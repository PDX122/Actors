package pdx.dev.fbactors.debug;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drag-and-drop crash tracing.
 *
 * <p>Master switch {@link #ENABLED} reads the {@code fbactors.diagDrag}
 * system property (default off). Release builds run with it off: only the
 * rare guard-trip warnings below can ever print, and only when a draw was
 * actually skipped. Diagnostic builds flip the default on so a repro log
 * captures the full drag lifecycle (mouse position, lane geometry, clip
 * bounds, payload, drop outcome) plus every guard trip.
 */
public final class DragDiag {
    public static final boolean ENABLED =
        Boolean.parseBoolean(System.getProperty("fbactors.diagDrag", "false"));
    public static final String TAG = "[FBACTORS-DIAG]";

    private static final Map<String, Long> LAST_WARN = new ConcurrentHashMap<>();

    private DragDiag() {}

    public static void log(String format, Object... args) {
        if (!ENABLED) return;
        try {
            pdx.dev.fbactors.FlashbackActors.LOGGER.info(TAG + " " + String.format(Locale.ROOT, format, args));
        } catch (Throwable ignored) {
        }
    }

    /** Warn-once-per-key-per-5s, for guard trips. Safe to call in release builds. */
    public static void warnRare(String key, String format, Object... args) {
        try {
            long now = System.nanoTime();
            Long last = LAST_WARN.get(key);
            if (last != null && now - last < 5_000_000_000L) return;
            LAST_WARN.put(key, now);
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn(TAG + " " + String.format(Locale.ROOT, format, args));
        } catch (Throwable ignored) {
        }
    }
}
