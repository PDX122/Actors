package pdx.dev.fbactors.filters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ActorLookBridge {
    private record Cached(Identifier id, DynamicTexture texture, int width, int height) {}
    private static final Map<String, Cached> CACHE = new LinkedHashMap<>();
    private static int sequence;
    private static final Map<String, String> RENDERED = new LinkedHashMap<>();
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private ActorLookBridge() {}
    private static int textureId(AbstractTexture texture) { return texture.getTexture() instanceof com.mojang.renderpearl.backend.opengl.GlTexture gl ? gl.glId() : -1; }

    public static Identifier apply(Identifier original) {
        try {
            if (original == null || original.getNamespace().equals("fbactors") && original.getPath().startsWith("actor_looks/")) return original;
            LookSettings look = ActorLooks.look();
            if (look == null || !look.enabled || look.intensity <= 0) {
                // Vulkan (or any backend without raw GL): look() is null because
                // the GPU path is unavailable. Bake the grade on CPU instead.
                // On GL backends isGlAvailable() is true, so this branch never
                // runs there and behaviour is exactly as before.
                if (FilterGpuPipeline.isGlAvailable()) return original;
                look = ActorLooks.lookCpu();
                if (look == null || !look.enabled || look.intensity <= 0) return original;
                return applyCpu(original, look);
            }
            var manager = Minecraft.getInstance().getTextureManager();
            var input = manager.getTexture(original);
            if (input == null) return original;
            int source = textureId(input);
            if (source <= 0) return original;
            int[] size = FilterGpuPipeline.textureSize(source);
            if (size[0] <= 0 || size[1] <= 0 || size[0] > 4096 || size[1] > 4096) return original;
            String key = ActorLooks.current() + "|" + original;
            Cached cached = CACHE.get(key);
            if (cached != null && (cached.width() != size[0] || cached.height() != size[1])) {
                ActorMosaic.unregisterMaterial(textureId(cached.texture()));
                manager.release(cached.id());
                CACHE.remove(key);
                RENDERED.remove(key);
                cached = null;
            }
            if (cached == null) {
                if (CACHE.size() >= 128) { Effects.status = "Actor material cache is full. Reload shaders to clear it."; return original; }
                var texture = new DynamicTexture("Actors material look", size[0], size[1], true);
                var id = Identifier.fromNamespaceAndPath("fbactors", "actor_looks/" + sequence++);
                manager.register(id, texture);
                cached = new Cached(id, texture, size[0], size[1]);
                CACHE.put(key, cached);
            }
            String signature = source + "|" + pdx.dev.fbactors.FlashbackActors.replayTick() + "|" + GSON.toJson(look);
            if (pdx.dev.fbactors.debug.MosaicDiag.ENABLED)
                pdx.dev.fbactors.debug.MosaicDiag.logRare("apply-" + key,
                    "apply key=%s src=%d size=%dx%d cached=%s hit=%b",
                    key, source, size[0], size[1], cached.id(), signature.equals(RENDERED.get(key)));
            if (signature.equals(RENDERED.get(key))) return cached.id();
            int target = textureId(cached.texture());
            if (target > 0 && ActorLooks.render(source, target, size[0], size[1], look)) {
                RENDERED.put(key, signature);
                return cached.id();
            }
            return original;
        } catch (Throwable ignored) {
            return original;
        }
    }

    /**
     * CPU bake for backends without raw GL. Mirrors the GPU path above (same
     * cache, eviction and RENDERED bookkeeping) but reads the source pixels
     * without GL and grades them with {@link LookCpuBake}. Any failure -
     * unreadable source, unsupported filter, upload error - returns the
     * original texture, exactly today's Vulkan behaviour.
     */
    private static Identifier applyCpu(Identifier original, LookSettings look) {
        try {
            var manager = Minecraft.getInstance().getTextureManager();
            var input = manager.getTexture(original);
            if (input == null) return original;
            com.mojang.blaze3d.platform.NativeImage owned = null;
            com.mojang.blaze3d.platform.NativeImage source;
            if (input instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
                source = dynamic.getPixels();
            } else {
                var resource = Minecraft.getInstance().getResourceManager().getResource(original).orElse(null);
                if (resource == null) return original;
                try (var stream = resource.open()) {
                    owned = com.mojang.blaze3d.platform.NativeImage.read(stream);
                }
                source = owned;
            }
            try {
                int width = source.getWidth(), height = source.getHeight();
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return original;
                String key = ActorLooks.current() + "|" + original;
                Cached cached = CACHE.get(key);
                if (cached != null && (cached.width() != width || cached.height() != height)) {
                    ActorMosaic.unregisterMaterial(textureId(cached.texture()));
                    manager.release(cached.id());
                    CACHE.remove(key);
                    RENDERED.remove(key);
                    cached = null;
                }
                if (cached == null) {
                    if (CACHE.size() >= 128) { Effects.status = "Actor material cache is full. Reload shaders to clear it."; return original; }
                    var texture = new DynamicTexture("Actors material look", width, height, true);
                    var id = Identifier.fromNamespaceAndPath("fbactors", "actor_looks/" + sequence++);
                    manager.register(id, texture);
                    cached = new Cached(id, texture, width, height);
                    CACHE.put(key, cached);
                }
                String signature = "cpu" + width + "x" + height + "|" + pdx.dev.fbactors.FlashbackActors.replayTick() + "|" + GSON.toJson(look);
                if (signature.equals(RENDERED.get(key))) return cached.id();
                float time = Math.max(0, pdx.dev.fbactors.FlashbackActors.replayTick()) / 20f;
                if (LookCpuBake.bake(cached.texture(), source, look, time)) {
                    RENDERED.put(key, signature);
                    return cached.id();
                }
                return original;
            } finally {
                if (owned != null) owned.close();
            }
        } catch (Throwable ignored) {
            return original;
        }
    }

    public static void clear() {
        var manager = Minecraft.getInstance().getTextureManager();
        for (var cached : CACHE.values()) { ActorMosaic.unregisterMaterial(textureId(cached.texture())); manager.release(cached.id()); }
        CACHE.clear();
        RENDERED.clear();
    }
}
