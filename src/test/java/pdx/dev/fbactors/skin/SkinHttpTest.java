package pdx.dev.fbactors.skin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class SkinHttpTest {

    private static final String DEAD_URL = "http://127.0.0.1:1/closed";

    @Test
    void keyIsStableAndPayloadSensitive() {
        assertEquals(SkinHttp.keyFor("https://a/b", null), SkinHttp.keyFor("https://a/b", null));
        assertEquals(40, SkinHttp.keyFor("https://a/b", null).length());
        assertNotEquals(SkinHttp.keyFor("https://a/b", null), SkinHttp.keyFor("https://a/b", "[\"x\"]"));
        assertNotEquals(SkinHttp.keyFor("https://a/b", null), SkinHttp.keyFor("https://a/c", null));
    }

    @Test
    void pngCheckRejectsAnErrorPage() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        assertTrue(SkinHttp.isPng(png));
        assertFalse(SkinHttp.isPng("<?xml version=\"1.0\"?><Error/>".getBytes(StandardCharsets.UTF_8)));
        assertFalse(SkinHttp.isPng(null));
        assertFalse(SkinHttp.isPng(new byte[0]));
    }

    @Test
    void freshCacheServesWithoutNetwork(@TempDir Path dir) throws Exception {
        seed(dir, DEAD_URL, null, "{\"hello\":1}".getBytes(StandardCharsets.UTF_8),
            System.currentTimeMillis() / 1000L + 600L);
        SkinHttp.Response response =
            SkinHttp.request(dir, DEAD_URL, null, false, 600L, false);
        assertTrue(response.fresh());
        assertEquals("{\"hello\":1}",
            new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void staleCacheServesWhenTheNetworkFails(@TempDir Path dir) throws Exception {


        seed(dir, DEAD_URL, null, "{\"old\":true}".getBytes(StandardCharsets.UTF_8),
            System.currentTimeMillis() / 1000L - 3600L);
        SkinHttp.Response response =
            SkinHttp.request(dir, DEAD_URL, null, false, 600L, false);
        assertFalse(response.fresh());
        assertNotNull(response.body());
        assertEquals("{\"old\":true}",
            new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void totalMissIsEmptyAndStale(@TempDir Path dir) {
        SkinHttp.Response response =
            SkinHttp.request(dir, DEAD_URL, null, false, 600L, false);
        assertFalse(response.fresh());
        assertNull(response.body());
    }

    @Test
    void sessionFixtureParsesToATexturedProfile() {
        String json = "{\"id\":\"7857869bcbca49ee866d550193d05da5\",\"name\":\"PDXJS\","
            + "\"properties\":[{\"name\":\"textures\",\"value\":\"dGV4dA==\",\"signature\":\"c2ln\"}]}";
        com.mojang.authlib.GameProfile profile =
            SkinHttp.parseSession(json.getBytes(StandardCharsets.UTF_8));
        assertNotNull(profile);
        assertEquals(java.util.UUID.fromString("7857869b-cbca-49ee-866d-550193d05da5"),
            profileId(profile));
        assertEquals("PDXJS", profileName(profile));
        assertFalse(profileTextures(profile).isEmpty());
    }

    @Test
    void sessionFixtureWithoutPropertiesRebuildsBare() {
        String json = "{\"id\":\"7857869bcbca49ee866d550193d05da5\",\"name\":\"PDXJS\"}";
        com.mojang.authlib.GameProfile profile =
            SkinHttp.parseSession(json.getBytes(StandardCharsets.UTF_8));
        assertNotNull(profile);
        assertTrue(profileTextures(profile).isEmpty());
    }


    private static java.util.UUID profileId(com.mojang.authlib.GameProfile profile) {
        try {
            try {
                return (java.util.UUID) profile.getClass().getMethod("id").invoke(profile);
            } catch (NoSuchMethodException newShape) {
                return (java.util.UUID) profile.getClass().getMethod("getId").invoke(profile);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static String profileName(com.mojang.authlib.GameProfile profile) {
        try {
            try {
                return (String) profile.getClass().getMethod("name").invoke(profile);
            } catch (NoSuchMethodException newShape) {
                return (String) profile.getClass().getMethod("getName").invoke(profile);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static java.util.Collection<?> profileTextures(com.mojang.authlib.GameProfile profile) {
        try {
            Object map;
            try {
                map = profile.getClass().getMethod("properties").invoke(profile);
            } catch (NoSuchMethodException newShape) {
                map = profile.getClass().getMethod("getProperties").invoke(profile);
            }
            @SuppressWarnings("unchecked")
            com.google.common.collect.Multimap<String, ?> multimap =
                (com.google.common.collect.Multimap<String, ?>) map;
            return multimap.get("textures");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }


    private static void seed(Path dir, String url, String payload, byte[] body, long expire)
            throws Exception {
        Files.createDirectories(dir);
        String key = SkinHttp.keyFor(url, payload);
        Files.write(dir.resolve(key + ".bin"), body);
        Files.writeString(dir.resolve(key + ".json"),
            "{\"url\":\"" + url + "\",\"expire\":" + expire + "}", StandardCharsets.UTF_8);
    }
}

