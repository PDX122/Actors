package pdx.dev.fbactors.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinNames {

    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private static final Map<String, UUID> RESOLVED = new ConcurrentHashMap<>();

    private SkinNames() {
    }

    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static UUID cached(@Nullable String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return RESOLVED.get(key(username));
    }

    public static boolean isResolving(@Nullable String username) {
        return username != null && !username.isBlank()
            && IN_FLIGHT.contains(key(username));
    }

    public static java.util.concurrent.CompletableFuture<UUID> findAsync(String username) {
        if (username == null || !username.trim().matches("[A-Za-z0-9_]{1,16}"))
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        UUID known = cached(username);
        if (known != null) return java.util.concurrent.CompletableFuture.completedFuture(known);
        return SkinLookupExecutor.submit(() -> {
            UUID found = lookup(username.trim());
            if (found != null) RESOLVED.put(key(username), found);
            return found;
        });
    }

    public static void resolveAsync(@Nullable String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        String key = key(username);

        if (RESOLVED.containsKey(key) || !IN_FLIGHT.add(key)) {
            return;
        }
        findAsync(username).whenComplete((id, failure) -> {
            IN_FLIGHT.remove(key);
            if (failure != null) FlashbackActors.LOGGER.debug("Could not resolve username '{}'", key, failure);
        });
    }

    @Nullable
    private static UUID lookup(String username) {
        UUID id = lookupBulk(username);
        if (id != null) {
            return id;
        }
        return lookupLegacy(username);
    }

    @Nullable
    private static UUID lookupBulk(String username) {
        String payload = "[\"" + username.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]";
        SkinHttp.Response response =
            SkinHttp.postJson(SkinHttp.BULK_URL, payload, SkinHttp.UUID_TTL_SECONDS);
        if (response.body() == null) {
            return null;
        }
        try {
            JsonElement parsed =
                JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8));
            if (!parsed.isJsonArray()) {
                return null;
            }
            JsonArray array = parsed.getAsJsonArray();

            if (array.isEmpty()) {

                FlashbackActors.LOGGER.debug("SKINDBG lookupUnknown username={}", username);
                return null;
            }
            JsonObject first = array.get(0).getAsJsonObject();
            JsonElement id = first.get("id");
            if (id == null || !id.isJsonPrimitive()) {
                return null;
            }

            SkinId skinId = SkinId.tryParse(id.getAsString());
            UUID uuid = skinId == null ? null : skinId.profileId();

            FlashbackActors.LOGGER.debug("SKINDBG lookupResolved username={} uuid={} cached={}",
                username, uuid, response.fresh() ? "live" : "disk");
            return uuid;
        } catch (Exception e) {
            FlashbackActors.LOGGER.debug("Could not parse username lookup for '{}'", username, e);
            return null;
        }
    }

    @Nullable
    private static UUID lookupLegacy(String username) {
        SkinHttp.Response response =
            SkinHttp.get(SkinHttp.LEGACY_URL + username, SkinHttp.UUID_TTL_SECONDS);
        if (response.body() == null || response.body().length == 0) {
            return null;
        }
        try {
            JsonObject json = JsonParser
                .parseString(new String(response.body(), StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonElement id = json.get("id");
            if (id == null || !id.isJsonPrimitive()) {
                return null;
            }
            SkinId skinId = SkinId.tryParse(id.getAsString());
            UUID uuid = skinId == null ? null : skinId.profileId();
            FlashbackActors.LOGGER.debug("SKINDBG lookupResolved username={} uuid={} via=legacy",
                username, uuid);
            return uuid;
        } catch (Exception e) {
            FlashbackActors.LOGGER.debug("Could not parse legacy lookup for '{}'", username, e);
            return null;
        }
    }
}
