package pdx.dev.fbactors.ui;

import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.timeline.EmptyTimeline;
import pdx.dev.fbactors.timeline.WorldSnapshot;

public final class TimelineForm {

    public static final int MAX_NAME = 64;
    public static final String DEFAULT_NAME = "Timeline";

    private static String name = DEFAULT_NAME;
    private static String minutes = "1";
    private static String seconds = "0";
    private static String error;
    private static String notice;
    private static boolean noticeOk = true;
    private static boolean busy;
    private static boolean completed;

    private TimelineForm() {
    }

    public static void reset() {
        error = null;
        notice = null;
    }

    public static String name() {
        return name;
    }

    public static void name(String value) {
        name = value == null ? "" : value;
        error = null;
    }

    public static String minutes() {
        return minutes;
    }

    public static void minutes(String value) {
        minutes = value == null ? "" : value;
        error = null;
    }

    public static String seconds() {
        return seconds;
    }

    public static void seconds(String value) {
        seconds = value == null ? "" : value;
        error = null;
    }

    public static void duration(int min, int sec) {
        minutes = String.valueOf(min);
        seconds = String.valueOf(Math.max(0, Math.min(59, sec)));
    }

    public static int totalSeconds() {
        return clampInt(minutes) * 60 + clampInt(seconds);
    }

    public static String readout() {
        int total = totalSeconds();
        return String.format("%02d:%02d  ·  %d ticks", total / 60, total % 60, total * 20);
    }

    public static String error() {
        return error;
    }

    public static String notice() {
        return notice;
    }

    public static boolean noticeOk() {
        return noticeOk;
    }

    public static void notify(String message, boolean ok) {
        notice = message;
        noticeOk = ok;
        if (ok) {
            error = null;
        }
    }

    public static boolean create() {
        int total = totalSeconds();
        if (total < EmptyTimeline.MIN_SECONDS || total > EmptyTimeline.MAX_SECONDS) {
            error = "Between " + EmptyTimeline.MIN_SECONDS + " and "
                + EmptyTimeline.MAX_SECONDS + " seconds";
            return false;
        }

        String chosen = name.isBlank() ? DEFAULT_NAME : name.trim();
        error = null;
        notice = null;
        busy = true;

        WorldSnapshot.capture(snapshot -> {

            Minecraft.getInstance().execute(() -> {
                busy = false;
                completed = true;
                EmptyTimeline.Result result =
                    EmptyTimeline.snapshotAndOpen(chosen, total, snapshot);
                notify(result.message(), result.ok());
            });
        });
        return true;
    }

    public static boolean busy() {
        return busy;
    }

    public static boolean completed() {
        return completed;
    }

    private static int clampInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

