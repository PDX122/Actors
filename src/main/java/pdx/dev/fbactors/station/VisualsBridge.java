package pdx.dev.fbactors.station;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;


final class VisualsBridge {


    @Nullable
    private static final Method DAY_TIME = dayTimeMethod();

    @Nullable
    private static Method dayTimeMethod() {
        for (String name : new String[]{"getDefaultClockTime", "getDayTime"}) {
            try {
                return ClientLevel.class.getMethod(name);
            } catch (NoSuchMethodException e) {

            }
        }
        return null;
    }


    static long currentDayTime(@Nullable ClientLevel level) {
        if (level != null && DAY_TIME != null) {
            try {
                Object value = DAY_TIME.invoke(level);
                if (value instanceof Number number) {
                    return number.longValue();
                }
            } catch (ReflectiveOperationException e) {

            }
        }
        return 6000L;
    }


    @Nullable
    private static final Method HAS_RTC = rtcMethod();

    @Nullable
    private static Method rtcMethod() {
        try {
            return ReplayServer.class.getMethod("hasRtcData");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }


    static boolean hasRtcData(ReplayServer replayServer) {
        if (HAS_RTC == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(HAS_RTC.invoke(replayServer));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }


    @Nullable
    private static Object overlaySection() {
        try {
            Object config = Flashback.getConfig();
            Field field = config.getClass().getField("overlay");
            return field.get(config);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }


    static boolean hasRtcOverlay() {
        Object overlay = overlaySection();
        if (overlay == null) {
            return false;
        }
        try {
            overlay.getClass().getField("rtcOverlay");
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }


    static boolean rtcOverlay() {
        Object overlay = overlaySection();
        if (overlay == null) {
            return false;
        }
        try {
            return overlay.getClass().getField("rtcOverlay").getBoolean(overlay);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }


    static void toggleRtcOverlay() {
        Object overlay = overlaySection();
        if (overlay == null) {
            return;
        }
        try {
            Field field = overlay.getClass().getField("rtcOverlay");
            field.setBoolean(overlay, !field.getBoolean(overlay));
            Flashback.getConfig().delayedSaveToDefaultFolder();
        } catch (ReflectiveOperationException e) {

        }
    }

    private VisualsBridge() {
    }
}

