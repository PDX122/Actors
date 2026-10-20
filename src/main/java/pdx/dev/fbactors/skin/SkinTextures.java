package pdx.dev.fbactors.skin;

import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public final class SkinTextures {


    private static final Map<UUID, GameProfile> READY = new ConcurrentHashMap<>();


    private static final Map<UUID, Object> HEADS = new ConcurrentHashMap<>();


    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();


    private static final long COOLDOWN_MS = 5 * 60 * 1000L;


    private static final java.util.Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();


    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fbactors-skin-textures");
        thread.setDaemon(true);
        return thread;
    });

    private SkinTextures() {
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
        EXECUTOR.execute(() -> {
            try {
                GameProfile fetched = fetchLayered(id);
                if (fetched != null) {
                    READY.put(id, fetched);
                    Object head = Compat.loadHead(fetched);
                    if (head != null) {
                        HEADS.put(id, head);
                    }

                    FlashbackActors.LOGGER.debug(
                        "SKINDBG fetchLanded account={} hasHead={} hasTextures={}", id,
                        head != null, Compat.profileHasTextures(fetched));
                    COOLDOWN.remove(id);
                } else {
                    COOLDOWN.put(id, System.currentTimeMillis() + COOLDOWN_MS);
                }
            } catch (Exception e) {
                COOLDOWN.put(id, System.currentTimeMillis() + COOLDOWN_MS);
                FlashbackActors.LOGGER.debug("Could not fetch textures for '{}'", id, e);
            } finally {
                IN_FLIGHT.remove(id);
            }
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

