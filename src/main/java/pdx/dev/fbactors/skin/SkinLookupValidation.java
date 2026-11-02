package pdx.dev.fbactors.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class SkinLookupValidation {
    private SkinLookupValidation() {}

    public static boolean validSession(byte[] body, UUID expected) {
        if (body == null || body.length == 0 || body.length > 65_536 || expected == null) return false;
        try {
            var root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            SkinId id = SkinId.tryParse(root.get("id").getAsString());
            if (id == null || !expected.equals(id.profileId()) || !root.get("name").getAsString().matches("[A-Za-z0-9_]{1,16}")) return false;
            var properties = root.getAsJsonArray("properties");
            if (properties == null || properties.size() > 16) return false;
            boolean found = false;
            for (var entry : properties) {
                var property = entry.getAsJsonObject();
                if (!"textures".equals(property.get("name").getAsString())) continue;
                if (found) return false;
                found = true;
                String encoded = property.get("value").getAsString();
                if (encoded.length() > 49_152) return false;
                var decoded = JsonParser.parseString(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)).getAsJsonObject();
                if (decoded.has("profileId")) {
                    var owner = SkinId.tryParse(decoded.get("profileId").getAsString());
                    if (owner == null || !expected.equals(owner.profileId())) return false;
                }
                var textures = decoded.getAsJsonObject("textures");
                if (textures == null || !validTexture(textures.getAsJsonObject("SKIN"))) return false;
                if (textures.has("CAPE") && !validTexture(textures.getAsJsonObject("CAPE"))) return false;
            }
            return found;
        } catch (RuntimeException malformed) { return false; }
    }

    private static boolean validTexture(JsonObject texture) {
        if (texture == null) return false;
        var uri = URI.create(texture.get("url").getAsString());
        return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
            && "textures.minecraft.net".equalsIgnoreCase(uri.getHost()) && uri.getUserInfo() == null
            && uri.getPort() == -1 && uri.getQuery() == null && uri.getFragment() == null
            && uri.getPath().matches("/texture/[a-fA-F0-9]{32,64}");
    }
}
