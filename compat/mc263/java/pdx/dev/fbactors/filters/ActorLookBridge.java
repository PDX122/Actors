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
        observeOccluder(original);
            LookSettings look = ActorLooks.look();
            if (look == null || !look.enabled || look.intensity <= 0) {

                if (FilterGpuPipeline.isGlAvailable()) return original;
                look = ActorLooks.lookCpu();
                if (look == null || !look.enabled || look.intensity <= 0) return original;
                return NativeActorLooks.apply(original, look);
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
                var texture = new DynamicTexture("Actors material look", size[0], size[1], true) {
                @Override public com.mojang.renderpearl.api.textures.GpuSampler getSampler() { return input.getSampler(); }
            };
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

    private static void observeOccluder(Identifier original) {
        if (!ActorLooks.needsOcclusion()) return;
        var texture = Minecraft.getInstance().getTextureManager().getTexture(original);
        if (texture == null) return;
        if (FilterGpuPipeline.isGlAvailable()) ActorMosaic.registerOccluder(textureId(texture));
        else NativeActorMasks.registerOccluder(texture.getTexture());
    }

    public static void clear() {
        NativeActorLooks.close();
        var manager = Minecraft.getInstance().getTextureManager();
        for (var cached : CACHE.values()) { ActorMosaic.unregisterMaterial(textureId(cached.texture())); manager.release(cached.id()); }
        CACHE.clear();
        RENDERED.clear();
    }
}
