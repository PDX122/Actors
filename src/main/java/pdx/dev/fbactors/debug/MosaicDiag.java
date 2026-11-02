package pdx.dev.fbactors.debug;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
