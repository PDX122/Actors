package pdx.dev.fbactors.skin;

import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.compat.Compat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class SkinThumbnails {


    public static final Object NO_TEXTURE = 0L;


    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();

    private SkinThumbnails() {
    }


    public static Object thumbnailFor(@Nullable SavedSkin skin) {
        if (skin == null) {
            return NO_TEXTURE;
        }

        Object cached = CACHE.get(skin.name());
        if (cached != null) {
            return cached;
        }

        Object handle = resolve(skin);


        if (!isNoTexture(handle)) {
            CACHE.put(skin.name(), handle);
        }
        return handle;
    }


    public static boolean isNoTexture(@Nullable Object handle) {
        return handle == null || !(handle instanceof Number number) || number.longValue() == 0L;
    }


    public static Object thumbnailForSource(@Nullable SkinId source) {
        return source == null ? NO_TEXTURE : resolve(source);
    }


    private static Object resolve(SavedSkin skin) {
        return resolve(skin.source());
    }

    private static Object resolve(SkinId source) {


        if (source.isBuiltin()) {
            return NO_TEXTURE;
        }

        UUID id = source.profileId();
        if (id == null) {


            id = SkinNames.cached(source.value());
            if (id == null) {
                return NO_TEXTURE;
            }
        }


        SkinTextures.ensureTextures(new com.mojang.authlib.GameProfile(id, source.value()));

        try {
            long handle = Compat.headTexture(id);
            return handle == 0L ? NO_TEXTURE : handle;
        } catch (Exception e) {


            return NO_TEXTURE;
        }
    }


    public static boolean hasThumbnail(@Nullable SavedSkin skin) {
        return !isNoTexture(thumbnailFor(skin));
    }


    public static void invalidate(String name) {
        if (name != null) {
            CACHE.remove(name);
        }
    }


    public static void invalidate() {
        CACHE.clear();
    }


    public static int cachedCount() {
        return CACHE.size();
    }
}
