package pdx.dev.fbactors.actor;

import java.util.UUID;
import java.util.regex.Pattern;

public final class ActorEntityIdentity {
    private static final Pattern UUID_TAG = Pattern.compile("UUID\\s*:\\s*\\[I;\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
    private ActorEntityIdentity() {}

    public static String write(UUID id) {
        return "{UUID:[I;" + (int) (id.getMostSignificantBits() >> 32) + ","
            + (int) id.getMostSignificantBits() + ","
            + (int) (id.getLeastSignificantBits() >> 32) + ","
            + (int) id.getLeastSignificantBits() + "]}";
    }

    public static UUID read(String nbt) {
        if (nbt == null) return null;
        var match = UUID_TAG.matcher(nbt);
        if (!match.find()) return null;
        try {
            return new UUID(((long) Integer.parseInt(match.group(1)) << 32)
                | Integer.toUnsignedLong(Integer.parseInt(match.group(2))),
                ((long) Integer.parseInt(match.group(3)) << 32)
                | Integer.toUnsignedLong(Integer.parseInt(match.group(4))));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
