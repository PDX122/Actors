package pdx.dev.fbactors.filters;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.*;
import com.mojang.renderpearl.api.commands.*;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import com.mojang.renderpearl.util.TextureViewAndSampler;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import pdx.dev.fbactors.FlashbackActors;

public final class NativeActorMasks {
    public record DrawState(CompiledRenderPipeline pipeline, Map<String, Object> uniforms,
                            GpuBufferSlice[] vertices, GpuBuffer indices, IndexType indexType,
                            ByteBuffer constants, int[] scissor) {}
    private record Source(RenderPipeline pipeline, ShaderSource shaders) {}
    private record Draw(UUID actor, DrawState state, Consumer<RenderPass> draw) {}
    private record Capture(CommandEncoder encoder, List<Draw> draws) {}
    private record MaskProgram(CompiledRenderPipeline pipeline, List<GpuTexture> colors,
                               List<GpuTextureView> views) implements AutoCloseable {
        @Override public void close() { pipeline.close(); views.stream().filter(Objects::nonNull).forEach(GpuTextureView::close); colors.forEach(GpuTexture::close); }
    }
    private static final Set<GpuTexture> OCCLUDERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static DepthSurface entityDepth;
    private static final Map<GpuTexture, UUID> MATERIALS = new WeakHashMap<>();
    private static final Map<CompiledRenderPipeline, Source> SOURCES = new WeakHashMap<>();
    private static final Map<RenderPass, Capture> PASSES = new IdentityHashMap<>();
    private static final Map<UUID, Mask> MASKS = new HashMap<>();
    private static final Map<CompiledRenderPipeline, MaskProgram> PROGRAMS = new HashMap<>();
    private static PearlLookPipeline fullscreen;
    private static boolean replaying, failed;
    private static int width, height;
    private NativeActorMasks() {}

    public static void register(GpuTexture texture, UUID actor) { if (actor != null) MATERIALS.put(texture, actor); }
    public static void registerOccluder(GpuTexture texture) { if (texture != null) OCCLUDERS.add(texture); }
    public static void source(RenderPipeline pipeline, CompiledRenderPipeline compiled, ShaderSource shaders) {
        if (compiled != null && !FilterGpuPipeline.isGlAvailable()) SOURCES.put(compiled, new Source(pipeline, shaders));
    }

    public static void track(CommandEncoder encoder, RenderPass pass, RenderPassDescriptor descriptor) {
        if (replaying || failed || FilterGpuPipeline.isGlAvailable() || !ActorMosaic.needsOcclusion()) return;
        var target = FilterRenderBridge.target(Minecraft.getInstance());
        var depth = descriptor.depthAttachment();
        if (depth == null || depth.textureView().texture() != target.getDepthTexture()) return;
        if (width != target.width || height != target.height) {
            clearMasks(); width = target.width; height = target.height;
        }
        PASSES.put(pass, new Capture(encoder, new ArrayList<>()));
    }

    public static boolean tracked(RenderPass pass) { return !replaying && !failed && PASSES.containsKey(pass); }

    public static Map<String, Object> boundUniforms(com.mojang.renderpearl.frontend.FrontendRenderPipeline pipeline,
                                                    Map<String, Object> uniforms) {
        var bound = new HashMap<String, Object>();
        if (pipeline != null) for (var uniform : pipeline.uniforms()) {
            var value = uniforms.get(uniform.name());
            if (value != null) bound.put(uniform.name(), value);
        }
        return bound;
    }

    public static boolean candidate(Map<String, Object> uniforms) {
        for (Object uniform : uniforms.values())
            if (uniform instanceof TextureViewAndSampler sample && (MATERIALS.containsKey(sample.view().texture()) || OCCLUDERS.contains(sample.view().texture()))) return true;
        return false;
    }

    public static void capture(RenderPass pass, DrawState state, Consumer<RenderPass> draw) {
        var capture = PASSES.get(pass);
        if (capture == null || !SOURCES.containsKey(state.pipeline())) return;
        UUID actor = null;
        for (Object uniform : state.uniforms().values()) {
            if (uniform instanceof TextureViewAndSampler value) {
                actor = MATERIALS.get(value.view().texture());
                if (actor != null) break;
            }
        }
        var look = actor == null ? null : Effects.timeline().actorLook(Math.max(0, FlashbackActors.replayTick()), actor);
        if (look == null || !look.enabled || look.intensity <= 0 || !ActorEffectPlan.usesScreen(look)) actor = null;
        capture.draws().add(new Draw(actor, state, draw));
    }

    public static void finish(RenderPass original) {
        var capture = PASSES.remove(original);
        if (capture == null || capture.draws().isEmpty() || failed) return;
        replaying = true;
        try {

            var encoder = capture.encoder();
            if (entityDepth == null) entityDepth = new DepthSurface();
            for (var draw : capture.draws()) {
                var program = maskProgram(draw.state().pipeline());
                replay(encoder, program, draw, entityDepth);
                if (draw.actor() == null || !MASKS.containsKey(draw.actor()) && MASKS.size() >= 32) continue;
                var mask = MASKS.computeIfAbsent(draw.actor(), ignored -> new Mask());
                replay(encoder, program, draw, mask);
            }
        } catch (RuntimeException error) { fail(error); }
        finally { replaying = false; }
    }

    private static void replay(CommandEncoder encoder, MaskProgram program, Draw draw, DepthSurface surface) {
        var descriptor = RenderPassDescriptor.builder(() -> "Actors depth");
        for (var view : program.views()) {
            if (view == null) descriptor.withUnusedColorAttachment(); else descriptor.withColorAttachment(view);
        }
        descriptor.withDepthAttachment(surface.view, surface.seen ? OptionalDouble.empty() : OptionalDouble.of(0));
        try (var pass = encoder.createRenderPass(descriptor.build())) {
            pass.setPipeline(program.pipeline());
            var state = draw.state();
            for (var entry : state.uniforms().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof TextureViewAndSampler sample) pass.setUniform(entry.getKey(), sample.view(), sample.sampler());
                else if (value instanceof GpuBufferSlice buffer) pass.setUniform(entry.getKey(), buffer);
                else if (value instanceof GpuBuffer buffer) pass.setUniform(entry.getKey(), buffer);
            }
            for (int i = 0; i < state.vertices().length; i++) if (state.vertices()[i] != null) pass.setVertexBuffer(i, state.vertices()[i]);
            if (state.indices() != null) pass.setIndexBuffer(state.indices(), state.indexType());
            if (state.constants() != null) pass.pushConstants(state.constants().duplicate());
            if (state.scissor() != null) {
                int[] rect = state.scissor(); pass.enableScissor(rect[0], rect[1], rect[2], rect[3]);
            }
            draw.draw().accept(pass);
        }
        surface.seen = true;
    }

    private static MaskProgram maskProgram(CompiledRenderPipeline compiled) {
        var cached = PROGRAMS.get(compiled);
        if (cached != null) return cached;
        var source = SOURCES.get(compiled);
        var original = source.pipeline();
        var builder = RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("fbactors", "mask/" + PROGRAMS.size()))
            .withVertexShader(original.getShaders().get(ShaderType.VERTEX))
            .withFragmentShader(original.getShaders().get(ShaderType.FRAGMENT))
            .withCull(original.isCull()).withPolygonMode(original.getPolygonMode())
            .withPrimitiveTopology(original.getPrimitiveTopology()).withPushConstantSize(original.pushConstantSize())
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true));
        original.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
        for (int i = 0; i < original.getVertexFormatBindings().size(); i++) builder.withVertexBinding(i, original.getVertexFormatBinding(i));
        original.getShaderDefines().flags().forEach(builder::withShaderDefine);
        original.getShaderDefines().values().forEach((key, value) -> builder.withShaderDefine(key + " " + value));
        if (!original.getShaderDefines().values().containsKey("ALPHA_CUTOUT"))
            builder.withShaderDefine("ALPHA_CUTOUT 0.01");
        var colors = new ArrayList<GpuTexture>();
        var views = new ArrayList<GpuTextureView>();
        var device = RenderSystem.getDevice();
        for (int i = 0; i < original.getColorTargetStates().size(); i++) {
            var state = original.getColorTargetStates().get(i);
            if (state == null) { builder.withUnusedColorTargetState(i); views.add(null); continue; }
            builder.withColorTargetState(i, new ColorTargetState(Optional.empty(), state.format(), ColorTargetState.WRITE_NONE));
            var texture = device.createTexture("Actors mask scratch", GpuTexture.USAGE_RENDER_ATTACHMENT, state.format(), width, height, 1, 1);
            colors.add(texture); views.add(device.createTextureView(texture));
        }
        try {
            var pipeline = device.compilePipeline(builder.build(), source.shaders(), Runnable::run).join().finishCompile();
            cached = new MaskProgram(pipeline, colors, views);
            PROGRAMS.put(compiled, cached);
            return cached;
        } catch (RuntimeException error) {
            views.stream().filter(Objects::nonNull).forEach(GpuTextureView::close);
            colors.forEach(GpuTexture::close);
            throw error;
        }
    }

    public static void render() {
        if (Looks.compare && !com.moulberry.flashback.Flashback.isExporting()) {
            MASKS.values().forEach(mask -> mask.seen = false);
            if (entityDepth != null) entityDepth.seen = false;
            return;
        }
        if (failed || MASKS.isEmpty()) {
            if (entityDepth != null) entityDepth.seen = false;
            return;
        }
        replaying = true;
        try {
            if (fullscreen == null) fullscreen = new PearlLookPipeline(RenderSystem.getDevice());
            var target = FilterRenderBridge.target(Minecraft.getInstance());
            var iterator = MASKS.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                var mask = entry.getValue();
                if (!mask.seen) { mask.close(); iterator.remove(); continue; }
                var look = Effects.timeline().actorLook(Math.max(0, FlashbackActors.replayTick()), entry.getKey());
                if (look != null && ActorEffectPlan.usesScreen(look)) {
                    GpuTextureView bounds = mask.view;
                    if (ActorEffectPlan.needsBounds(look)) {
                        var encoder = RenderSystem.getDevice().createCommandEncoder();
                        for (int i = 0; i < mask.extentViews.size(); i++) {
                            var output = mask.extentViews.get(i);
                            fullscreen.draw(encoder, "actor_extent", Map.of("InSampler", bounds), output, look, 0,
                                Map.of("FirstPass", i == 0 ? 1f : 0f, "ReverseDepth", 1f));
                            bounds = output;
                        }
                        encoder.submit();
                    }
                    fullscreen.renderActor(target.getColorTexture(), target.getColorTextureView(), mask.view,
                        target.getDepthTextureView(), entityDepth != null && entityDepth.seen ? entityDepth.view : target.getDepthTextureView(),
                        bounds, look, Math.max(0, FlashbackActors.replayTick()) / 20f);
                }
                mask.seen = false;
            }
        } catch (RuntimeException error) { fail(error); }
        finally { replaying = false; if (entityDepth != null) entityDepth.seen = false; }
    }

    private static void fail(RuntimeException error) {
        failed = true;
        FlashbackActors.LOGGER.warn("Native actor screen effects failed", error);
        Effects.status = "Actor screen effects failed. Reload shaders to retry.";
    }
    private static void clearMasks() {
        MASKS.values().forEach(Mask::close); MASKS.clear();
        if (entityDepth != null) entityDepth.close();
        entityDepth = null;
        PROGRAMS.values().forEach(MaskProgram::close); PROGRAMS.clear();
    }
    public static void close() {
        clearMasks(); MATERIALS.clear(); OCCLUDERS.clear(); SOURCES.clear(); PASSES.clear();
        if (fullscreen != null) fullscreen.close();
        fullscreen = null; failed = replaying = false;
    }

    private static class DepthSurface implements AutoCloseable {
        final GpuTexture depth;
        final GpuTextureView view;
        boolean seen;
        DepthSurface() {
            var device = RenderSystem.getDevice();
            depth = device.createTexture("Actors entity depth", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                GpuFormat.D32_FLOAT, width, height, 1, 1);
            view = device.createTextureView(depth);
        }
        @Override public void close() { view.close(); depth.close(); }
    }

    private static final class Mask extends DepthSurface {
        final List<GpuTexture> extents = new ArrayList<>();
        final List<GpuTextureView> extentViews = new ArrayList<>();
        Mask() {
            var device = RenderSystem.getDevice();
            int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
            int w = width, h = height;
            do {
                w = Math.max(1, (w + 1) / 2); h = Math.max(1, (h + 1) / 2);
                var extent = device.createTexture("Actors silhouette bounds", usage, GpuFormat.RGBA16_FLOAT, w, h, 1, 1);
                extents.add(extent); extentViews.add(device.createTextureView(extent));
            } while (w > 1 || h > 1);
        }
        @Override public void close() {
            super.close();
            extentViews.forEach(GpuTextureView::close); extents.forEach(GpuTexture::close);
        }
    }
}
