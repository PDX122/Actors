package pdx.dev.fbactors.skin;

import com.moulberry.flashback.Flashback;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;


public final class SkinHttp {


    public static final String BULK_URL =
        "https://api.minecraftservices.com/minecraft/profile/lookup/bulk/byname";


    public static final String LEGACY_URL = "https://api.mojang.com/users/profiles/minecraft/";


    public static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";


    public static final long UUID_TTL_SECONDS = 600L;


    public static final long SESSION_TTL_SECONDS = 90L;


    public static final long NEGATIVE_TTL_SECONDS = 3600L;


    public record Response(int code, @Nullable byte[] body, boolean fresh) {
    }

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "flashback-actors";


    private static final org.slf4j.Logger LOGGER =
        org.slf4j.LoggerFactory.getLogger("fbactors/skin");

    private SkinHttp() {
    }


    public static Path cacheDir() {
        return Flashback.getDataDirectory().resolve("fbactors").resolve("skincache");
    }


    public static Response get(String url, long ttlSeconds) {
        return request(cacheDir(), url, null, false, ttlSeconds, false);
    }


    public static Response getPng(String url, long ttlSeconds) {
        return request(cacheDir(), url, null, false, ttlSeconds, true);
    }


    public static Response postJson(String url, String json, long ttlSeconds) {
        return request(cacheDir(), url, json, true, ttlSeconds, false);
    }


    static Response request(Path dir, String url, @Nullable String payload, boolean post,
                            long ttlSeconds, boolean checkPng) {
        String key = keyFor(url, payload);
        Path bodyFile = dir.resolve(key + ".bin");
        Path metaFile = dir.resolve(key + ".json");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return liveOrStale(dir, url, payload, post, bodyFile, metaFile, ttlSeconds, checkPng);
        }
        long expire = readExpiry(metaFile);
        if (expire > now() && Files.isRegularFile(bodyFile)) {
            try {
                return new Response(200, Files.readAllBytes(bodyFile), true);
            } catch (IOException ignored) {

            }
        }
        return liveOrStale(dir, url, payload, post, bodyFile, metaFile, ttlSeconds, checkPng);
    }


    private static Response liveOrStale(Path dir, String url, @Nullable String payload, boolean post,
                                        Path bodyFile, Path metaFile, long ttlSeconds,
                                        boolean checkPng) {
        try {
            HttpURLConnection connection = open(url, payload, post);
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_NO_CONTENT) {


                store(bodyFile, metaFile, url, new byte[0], NEGATIVE_TTL_SECONDS);
                return new Response(code, new byte[0], true);
            }
            if (code < 200 || code >= 300) {
                return stale(bodyFile, code);
            }
            byte[] body = readBody(connection);
            if (checkPng && !isPng(body)) {
                return stale(bodyFile, code);
            }
            store(bodyFile, metaFile, url, body, ttlSeconds);
            return new Response(code, body, true);
        } catch (Exception e) {
            return stale(bodyFile, -1);
        }
    }


    private static Response stale(Path bodyFile, int code) {
        try {
            if (Files.isRegularFile(bodyFile)) {
                return new Response(code, Files.readAllBytes(bodyFile), false);
            }
        } catch (IOException ignored) {
        }
        return new Response(code, null, false);
    }


    static String keyFor(String url, @Nullable String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(url.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            if (payload != null) {
                digest.update(payload.getBytes(StandardCharsets.UTF_8));
            }
            byte[] hash = digest.digest();
            StringBuilder out = new StringBuilder(40);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString((url + "\n" + payload).hashCode());
        }
    }


    static boolean isPng(@Nullable byte[] body) {
        return body != null && body.length > 8
            && (body[0] & 0xFF) == 0x89 && body[1] == 0x50 && body[2] == 0x4E && body[3] == 0x47
            && (body[4] & 0xFF) == 0x0D && (body[5] & 0xFF) == 0x0A
            && (body[6] & 0xFF) == 0x1A && (body[7] & 0xFF) == 0x0A;
    }


    static long readExpiry(Path metaFile) {
        try {
            String json = Files.readString(metaFile, StandardCharsets.UTF_8);
            int at = json.indexOf("\"expire\"");
            if (at < 0) {
                return -1L;
            }
            int colon = json.indexOf(':', at);
            int end = colon + 1;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
                end++;
            }
            return Long.parseLong(json.substring(colon + 1, end).trim());
        } catch (Exception e) {
            return -1L;
        }
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private static HttpURLConnection open(String url, @Nullable String payload, boolean post)
            throws Exception {
        HttpURLConnection connection =
            (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Encoding", "gzip");
        if (post) {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            byte[] bytes = payload == null ? new byte[0]
                : payload.getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(bytes);
        }
        return connection;
    }

    private static byte[] readBody(HttpURLConnection connection) throws Exception {
        String encoding = connection.getContentEncoding();
        try (InputStream in = "gzip".equalsIgnoreCase(encoding)
                ? new GZIPInputStream(connection.getInputStream())
                : connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }


    private static void store(Path bodyFile, Path metaFile, String url, byte[] body, long ttlSeconds) {
        try {
            Path part = bodyFile.resolveSibling(bodyFile.getFileName() + ".part");
            Files.write(part, body);
            Files.move(part, bodyFile,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            String meta = "{\"url\":" + quote(url) + ",\"expire\":" + (now() + ttlSeconds) + "}";
            Files.writeString(metaFile, meta, StandardCharsets.UTF_8);
        } catch (Exception ignored) {

        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }


    @Nullable
    static com.mojang.authlib.GameProfile parseSession(@Nullable byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser
                .parseString(new String(body, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
            com.google.gson.JsonElement idElement = json.get("id");
            com.google.gson.JsonElement nameElement = json.get("name");
            if (idElement == null || nameElement == null
                || !idElement.isJsonPrimitive() || !nameElement.isJsonPrimitive()) {
                return null;
            }
            SkinId skinId = SkinId.tryParse(idElement.getAsString());
            java.util.UUID uuid = skinId == null ? null : skinId.profileId();
            if (uuid == null) {
                return null;
            }
            return buildProfile(uuid, nameElement.getAsString(), json.get("properties"));
        } catch (Exception e) {
            LOGGER.debug("Could not parse session profile", e);
            return null;
        }
    }


    private static com.mojang.authlib.GameProfile buildProfile(
            java.util.UUID uuid, String name, com.google.gson.JsonElement properties) {
        com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> map =
            com.google.common.collect.HashMultimap.create();
        if (properties != null && properties.isJsonArray()) {
            for (com.google.gson.JsonElement element : properties.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                com.google.gson.JsonObject property = element.getAsJsonObject();
                com.google.gson.JsonElement propName = property.get("name");
                com.google.gson.JsonElement value = property.get("value");
                if (propName == null || value == null
                    || !propName.isJsonPrimitive() || !value.isJsonPrimitive()) {
                    continue;
                }
                com.google.gson.JsonElement signature = property.get("signature");
                if (signature != null && signature.isJsonPrimitive()) {
                    map.put(propName.getAsString(),
                        new com.mojang.authlib.properties.Property(propName.getAsString(),
                            value.getAsString(), signature.getAsString()));
                } else {
                    map.put(propName.getAsString(),
                        new com.mojang.authlib.properties.Property(propName.getAsString(),
                            value.getAsString()));
                }
            }
        }
        return buildProfileReflective(uuid, name, map);
    }

    private static com.mojang.authlib.GameProfile buildProfileReflective(
            java.util.UUID uuid, String name,
            com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> map) {
        Class<com.mojang.authlib.GameProfile> profileClass = com.mojang.authlib.GameProfile.class;
        Class<com.mojang.authlib.properties.PropertyMap> mapClass =
            com.mojang.authlib.properties.PropertyMap.class;


        try {
            Object propertyMap = mapClass.getConstructor(com.google.common.collect.Multimap.class)
                .newInstance(map);
            try {
                return profileClass
                    .getConstructor(java.util.UUID.class, String.class, mapClass)
                    .newInstance(uuid, name, propertyMap);
            } catch (NoSuchMethodException noThreeArg) {

            }
        } catch (ReflectiveOperationException ignored) {

        }


        try {
            Object profile = profileClass.getConstructor(java.util.UUID.class, String.class)
                .newInstance(uuid, name);
            Object properties = profileClass.getMethod("getProperties").invoke(profile);
            @SuppressWarnings("unchecked")
            com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> target =
                (com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property>) properties;
            target.putAll(map);
            return (com.mojang.authlib.GameProfile) profile;
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Could not build textured profile on this authlib; returning bare profile", e);
            try {
                return profileClass.getConstructor(java.util.UUID.class, String.class)
                    .newInstance(uuid, name);
            } catch (ReflectiveOperationException impossible) {


                try {
                    Object empty = mapClass.getConstructor().newInstance();
                    return profileClass
                        .getConstructor(java.util.UUID.class, String.class, mapClass)
                        .newInstance(uuid, name, empty);
                } catch (ReflectiveOperationException failed) {
                    throw new IllegalStateException("Unsupported authlib GameProfile shape", failed);
                }
            }
        }
    }
}

