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
    private static int textureId(AbstractTexture texture) { return texture.getTexture() instanceof com.mojang.blaze3d.opengl.GlTexture gl ? gl.glId() : -1; }

    public static Identifier apply(Identifier original) {
        if (original == null || original.getNamespace().equals("fbactors") && original.getPath().startsWith("actor_looks/")) return original;
        LookSettings look = ActorLooks.look();
        if (look == null || !look.enabled || look.intensity <= 0) return original;
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
        if (signature.equals(RENDERED.get(key))) return cached.id();
        int target = textureId(cached.texture());
        if (target > 0 && ActorLooks.render(source, target, size[0], size[1], look)) {
            RENDERED.put(key, signature);
            return cached.id();
        }
        return original;
    }

    public static void clear() {
        var manager = Minecraft.getInstance().getTextureManager();
        for (var cached : CACHE.values()) { ActorMosaic.unregisterMaterial(textureId(cached.texture())); manager.release(cached.id()); }
        CACHE.clear();
        RENDERED.clear();
    }
}
