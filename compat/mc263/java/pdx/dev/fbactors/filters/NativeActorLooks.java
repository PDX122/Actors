package pdx.dev.fbactors.filters;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import pdx.dev.fbactors.FlashbackActors;

final class NativeActorLooks {
    private record Cached(Identifier id, Material texture, Object source, String signature) {}
    private static final Map<String, Cached> CACHE = new HashMap<>();
    private static final Map<String, PearlLookPipeline> PIPELINES = new HashMap<>();
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static int sequence;
    private static boolean failed;
    private NativeActorLooks() {}

    static Identifier apply(Identifier original, LookSettings look) {
        if (failed) return original;
        try { return applyInternal(original, look); }
        catch (RuntimeException error) {
            failed = true;
            Effects.status = "Actor look rendering failed. Reload shaders to retry.";
            FlashbackActors.LOGGER.warn("Native actor look failed", error);
            return original;
        }
    }

    private static Identifier applyInternal(Identifier original, LookSettings look) {
        if (look == null || !look.enabled || look.intensity <= 0) return original;
        var manager = Minecraft.getInstance().getTextureManager();
        var input = manager.getTexture(original);
        if (input == null || input.getTexture() == null) return original;
        var source = input.getTexture();
        int width = source.getWidth(0), height = source.getHeight(0);
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return original;
        String key = ActorLooks.current() + "|" + original;
        String signature = FlashbackActors.replayTick() + "|" + GSON.toJson(look);
        var cached = CACHE.get(key);
        if (cached != null && cached.source() == source && signature.equals(cached.signature())) return cached.id();
        if (cached != null && (cached.texture().getTexture().getWidth(0) != width
            || cached.texture().getTexture().getHeight(0) != height)) {
            manager.release(cached.id()); CACHE.remove(key); cached = null;
        }
        if (cached == null) {
            if (CACHE.size() >= 128) return original;
            var texture = new Material(width, height, source.getFormat(), input.getSampler());
            var id = Identifier.fromNamespaceAndPath("fbactors", "actor_looks/native_" + sequence++);
            manager.register(id, texture);
            cached = new Cached(id, texture, null, "");
            CACHE.put(key, cached);
        }
        String dimensions = width + "x" + height + ":" + source.getFormat();
        var pipeline = PIPELINES.computeIfAbsent(dimensions, ignored -> new PearlLookPipeline(RenderSystem.getDevice()));
        var material = ActorEffectPlan.material(look);
        pipeline.render(source, cached.texture().getTextureView(), material, Math.max(0, FlashbackActors.replayTick()) / 20f);
        NativeActorMasks.register(cached.texture().getTexture(), ActorLooks.current());
        CACHE.put(key, new Cached(cached.id(), cached.texture(), source, signature));
        return cached.id();
    }

    static void close() {
        var manager = Minecraft.getInstance().getTextureManager();
        CACHE.values().forEach(c -> manager.release(c.id()));
        CACHE.clear();
        PIPELINES.values().forEach(PearlLookPipeline::close);
        PIPELINES.clear();
        NativeActorMasks.close();
        failed = false;
    }

    static final class Material extends AbstractTexture {
        Material(int width, int height, GpuFormat format, com.mojang.renderpearl.api.textures.GpuSampler sourceSampler) {
            var device = RenderSystem.getDevice();
            sampler = sourceSampler;
            texture = device.createTexture("Actors private material", GpuTexture.USAGE_TEXTURE_BINDING
                | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                format, width, height, 1, 1);
            textureView = device.createTextureView(texture);
        }
    }
}
