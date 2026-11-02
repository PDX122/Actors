package pdx.dev.fbactors.skin;

import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinTextures {

    private static final Map<UUID, GameProfile> READY = new ConcurrentHashMap<>();

    private static final Map<UUID, Object> HEADS = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();

    private static final long COOLDOWN_MS = 5 * 60 * 1000L;

    private static final java.util.Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private SkinTextures() {
    }

    public static java.util.concurrent.CompletableFuture<GameProfile> findAsync(UUID id) {
        if (id == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
        GameProfile known = READY.get(id);
        if (known != null && HEADS.containsKey(id)) return java.util.concurrent.CompletableFuture.completedFuture(known);
        return SkinLookupExecutor.submit(() -> {
            GameProfile profile = known != null ? known : fetchLayered(id);
            if (profile == null) return null;
            READY.put(id, profile);
            Object head = Compat.loadHead(profile);
            if (head != null) HEADS.put(id, head);
            return profile;
        });
    }

    public static void ensureTextures(@Nullable GameProfile profile) {
        UUID id = profile == null ? null : Compat.profileId(profile);
        if (id == null || READY.containsKey(id) || !IN_FLIGHT.add(id)) {
            return;
        }
        if (Compat.profileHasTextures(profile)) {
            IN_FLIGHT.remove(id);
            return;
        }
        Long retryAt = COOLDOWN.get(id);
        if (retryAt != null && System.currentTimeMillis() < retryAt) {
            IN_FLIGHT.remove(id);
            return;
        }
        findAsync(id).whenComplete((fetched, failure) -> {
            if (failure == null && fetched != null) COOLDOWN.remove(id);
            else COOLDOWN.put(id, System.currentTimeMillis() + COOLDOWN_MS);
            IN_FLIGHT.remove(id);
            if (failure != null) FlashbackActors.LOGGER.debug("Could not fetch textures for '{}'", id, failure);
        });
    }

    @Nullable
    private static GameProfile fetchLayered(UUID id) {
        String undashed = id.toString().replace("-", "");
        SkinHttp.Response response = SkinHttp.get(
            SkinHttp.SESSION_URL + undashed, SkinHttp.SESSION_TTL_SECONDS);
        if (response.body() == null || response.body().length == 0) {
            return null;
        }
        if (!SkinLookupValidation.validSession(response.body(), id)) return null;
        GameProfile parsed = SkinHttp.parseSession(response.body());
        if (parsed != null && Compat.profileHasTextures(parsed)) {
            if (!response.fresh()) {

                FlashbackActors.LOGGER.debug("SKINDBG fetchStale account={}", id);
            }
            return parsed;
        }
        return null;
    }

    @Nullable
    public static GameProfile ready(UUID id) {
        return READY.get(id);
    }

    @Nullable
    public static Object headLocation(UUID id) {
        return HEADS.get(id);
    }
}
