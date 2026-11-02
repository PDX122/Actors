package pdx.dev.fbactors.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinLookupValidationTest {
    private static final UUID ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String URL = "https://textures.minecraft.net/texture/" + "a".repeat(64);
    @Test void acceptsOfficialTexturesAndChecksProfileOwnership() {
        assertTrue(SkinLookupValidation.validSession(profile(URL,ID,ID),ID));
        assertTrue(SkinLookupValidation.validSession(profile(URL.replace("https", "http"),ID,ID),ID));
        assertFalse(SkinLookupValidation.validSession(profile(URL,UUID.randomUUID(),ID),ID));
        assertFalse(SkinLookupValidation.validSession(profile(URL,ID,UUID.randomUUID()),ID));
    }
    @Test void rejectsMalformedOrUnexpectedTextureData() {
        for (String url : new String[]{"file:///tmp/skin.png", "https://evil.test/texture/"+"a".repeat(64),
            "https://textures.minecraft.net.evil.test/texture/"+"a".repeat(64), URL+"?redirect=evil", URL+"#fragment",
            "https://user@textures.minecraft.net/texture/"+"a".repeat(64), "https://textures.minecraft.net/texture/not-a-hash"})
            assertFalse(SkinLookupValidation.validSession(profile(url,ID,ID),ID),url);
        for (byte[] data : new byte[][]{new byte[0], new byte[70_000], "<html>error</html>".getBytes(StandardCharsets.UTF_8),
            "{}".getBytes(StandardCharsets.UTF_8), "[]".getBytes(StandardCharsets.UTF_8)})
            assertFalse(SkinLookupValidation.validSession(data,ID));
        assertFalse(SkinLookupValidation.validSession(null,ID));
    }
    @Test void brokenBase64AndMissingTexturesCannotReachTheSkinLoader() {
        var body=new String(profile(URL,ID,ID),StandardCharsets.UTF_8);
        var root=com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        root.getAsJsonArray("properties").get(0).getAsJsonObject().addProperty("value","not base64!");
        assertFalse(SkinLookupValidation.validSession(root.toString().getBytes(StandardCharsets.UTF_8),ID));
        root.add("properties",new JsonArray());
        assertFalse(SkinLookupValidation.validSession(root.toString().getBytes(StandardCharsets.UTF_8),ID));
    }
    @Test void rejectsDuplicateTextureProperties() {
        var root=com.google.gson.JsonParser.parseString(new String(profile(URL,ID,ID),StandardCharsets.UTF_8)).getAsJsonObject();
        var properties=root.getAsJsonArray("properties");
        properties.add(properties.get(0).deepCopy());
        assertFalse(SkinLookupValidation.validSession(root.toString().getBytes(StandardCharsets.UTF_8),ID));
    }
    private static byte[] profile(String url,UUID account,UUID owner) {
        var skin=new JsonObject();skin.addProperty("url",url);
        var textures=new JsonObject();textures.add("SKIN",skin);
        var payload=new JsonObject();payload.add("textures",textures);payload.addProperty("profileId",owner.toString().replace("-",""));
        var property=new JsonObject();property.addProperty("name","textures");
        property.addProperty("value",Base64.getEncoder().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8)));
        var properties=new JsonArray();properties.add(property);
        var response=new JsonObject();response.addProperty("id",account.toString().replace("-",""));
        response.addProperty("name","Notch");response.add("properties",properties);
        return response.toString().getBytes(StandardCharsets.UTF_8);
    }
}
