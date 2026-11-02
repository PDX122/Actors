package pdx.dev.fbactors.skin;

import java.net.URI;
import java.util.Locale;

public final class SkinQuery {
    private SkinQuery() {}

    public static SkinId parse(String input) {
        String text = input == null ? "" : input.trim();
        if (text.length() > 2048) throw new IllegalArgumentException("That skin query is too long.");
        if (text.startsWith("namemc.com/") || text.startsWith("www.namemc.com/")) text = "https://" + text;
        if (text.startsWith("http://") || text.startsWith("https://")) {
            URI uri;
            try { uri = URI.create(text); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("That profile link is invalid."); }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!(host.equals("namemc.com") || host.endsWith(".namemc.com")) || uri.getUserInfo() != null)
                throw new IllegalArgumentException("Paste a player name, UUID, or NameMC profile link.");
            String path = uri.getPath();
            if (path == null || !path.startsWith("/profile/"))
                throw new IllegalArgumentException("Use the player's profile link, or paste their name or UUID.");
            text = path.substring("/profile/".length()).replaceFirst("/+$", "");
            text = text.replaceFirst("\\.\\d+$", "");
        }
        SkinId source = SkinId.tryParse(text);
        if (source == null || source.isBuiltin())
            throw new IllegalArgumentException("Enter a player name, UUID, or NameMC profile link.");
        if (source.isUsername() && source.value().length() > 16)
            throw new IllegalArgumentException("Minecraft usernames contain at most 16 letters, digits or underscores.");
        return source;
    }
}
