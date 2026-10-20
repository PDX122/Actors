package pdx.dev.fbactors.util;

import net.minecraft.SharedConstants;


public final class GameVersions {

    private static volatile String cachedName;
    private static volatile int cachedData = Integer.MIN_VALUE;
    private static volatile int cachedProtocol = Integer.MIN_VALUE;

    private GameVersions() {
    }

    public static String gameVersionName() {
        String hit = cachedName;
        if (hit != null) {
            return hit;
        }
        Object version = SharedConstants.getCurrentVersion();
        hit = firstString(version, "name", "getName");
        if (hit == null) {
            hit = "unknown";
        }
        cachedName = hit;
        return hit;
    }

    public static int gameDataVersion() {
        if (cachedData != Integer.MIN_VALUE) {
            return cachedData;
        }
        Object version = SharedConstants.getCurrentVersion();

        try {
            Object data = version.getClass().getMethod("dataVersion").invoke(version);
            Object v = data.getClass().getMethod("version").invoke(data);
            if (v instanceof Number n) {
                cachedData = n.intValue();
                return cachedData;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Object data = version.getClass().getMethod("getDataVersion").invoke(version);
            Object v = data.getClass().getMethod("getVersion").invoke(data);
            if (v instanceof Number n) {
                cachedData = n.intValue();
                return cachedData;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        cachedData = 0;
        return 0;
    }

    public static int gameProtocolVersion() {
        if (cachedProtocol != Integer.MIN_VALUE) {
            return cachedProtocol;
        }
        try {
            Object v = SharedConstants.class.getMethod("getProtocolVersion").invoke(null);
            if (v instanceof Number n) {
                cachedProtocol = n.intValue();
                return cachedProtocol;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        cachedProtocol = 0;
        return 0;
    }

    private static String firstString(Object target, String... methods) {
        for (String method : methods) {
            try {
                Object v = target.getClass().getMethod(method).invoke(target);
                if (v instanceof String s && !s.isEmpty()) {
                    return s;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}

