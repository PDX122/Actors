package pdx.dev.fbactors.skin;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;


public final class SkinId {


    public enum Kind {

        USERNAME,

        UUID,

        BUILTIN
    }


    private static final Pattern DASHED_UUID = Pattern.compile(
        "^\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}$");


    private static final Pattern UNDASHED_UUID = Pattern.compile("^\\p{XDigit}{32}$");


    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{1,32}$");


    private static final Pattern SLUG = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final Kind kind;
    private final String value;

    private SkinId(Kind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    public static SkinId parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Skin reference was null");
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Skin reference was blank");
        }


        if (text.charAt(0) == '/') {
            String slug = text.substring(1);
            if (!SLUG.matcher(slug).matches()) {
                throw new IllegalArgumentException(
                    "Not a built-in skin name: " + raw + " (a built-in is / followed by "
                        + "letters, digits, dot, dash or underscore)");
            }
            return new SkinId(Kind.BUILTIN, "/" + slug);
        }


        boolean uuidShaped = text.indexOf('-') >= 0 || UNDASHED_UUID.matcher(text).matches();
        if (uuidShaped) {
            return new SkinId(Kind.UUID, canonicalUuid(raw, text));
        }

        if (!USERNAME.matcher(text).matches()) {
            throw new IllegalArgumentException(
                "Not a skin reference: " + raw + " (expected a username, a UUID, or a "
                    + "built-in name beginning with /)");
        }
        return new SkinId(Kind.USERNAME, text.toLowerCase(Locale.ROOT));
    }


    @Nullable
    public static SkinId tryParse(String raw) {
        try {
            return parse(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    private static String canonicalUuid(String raw, String text) {
        String hex;
        if (DASHED_UUID.matcher(text).matches()) {
            hex = text.replace("-", "");
        } else if (UNDASHED_UUID.matcher(text).matches()) {
            hex = text;
        } else {
            throw new IllegalArgumentException(
                "Malformed UUID: " + raw + " (expected 32 hex digits, optionally in "
                    + "8-4-4-4-12 groups)");
        }

        StringBuilder sb = new StringBuilder(36);
        sb.append(hex, 0, 8).append('-')
            .append(hex, 8, 12).append('-')
            .append(hex, 12, 16).append('-')
            .append(hex, 16, 20).append('-')
            .append(hex, 20, 32);
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    public Kind kind() {
        return this.kind;
    }


    public String value() {
        return this.value;
    }


    public boolean isValid() {
        return this.kind != null && this.value != null && !this.value.isEmpty();
    }


    public String cacheKey() {
        switch (this.kind) {
            case USERNAME:
                return "u_" + this.value;
            case UUID:
                return "p_" + this.value.replace("-", "");
            case BUILTIN:
            default:
                return "b_" + this.value.substring(1);
        }
    }


    @Nullable
    public UUID profileId() {
        return this.kind == Kind.UUID ? UUID.fromString(this.value) : null;
    }


    public boolean isBuiltin() {
        return this.kind == Kind.BUILTIN;
    }


    public boolean isUsername() {
        return this.kind == Kind.USERNAME;
    }


    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SkinId id)) {
            return false;
        }
        return this.kind == id.kind && this.value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return this.kind.hashCode() * 31 + this.value.hashCode();
    }


    @Override
    public String toString() {
        return this.value;
    }
}
