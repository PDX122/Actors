package pdx.dev.fbactors.filters;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import pdx.dev.fbactors.FlashbackActors;

public final class FilterPearlSpike {
    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath("fbactors", "pearl_passthrough");
    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("minecraft", "core/screenquad");
    private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("fbactors", "post/pearl_passthrough");

    private static volatile RenderPipeline pipeline;

    private FilterPearlSpike() {
    }

    public static boolean runPassthrough(int w, int h) {
        if (w <= 0 || h <= 0) {
            FlashbackActors.LOGGER.warn("[FilterPearlSpike] refusing passthrough with extent {}x{}", w, h);
            return false;
        }
        if (!RenderSystem.isOnRenderThread()) {
            FlashbackActors.LOGGER.warn("[FilterPearlSpike] passthrough must run on the render thread");
            return false;
        }
        GpuTexture src = null;
        GpuTexture dst = null;
        GpuTextureView srcView = null;
        GpuTextureView dstView = null;
        try {
            RenderPipeline compiled = pipeline;
            if (compiled == null) {
                compiled = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(LOCATION)
                    .withVertexShader(VERTEX)
                    .withFragmentShader(FRAGMENT)
                    .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                    .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT,
                        ColorTargetState.WRITE_ALL))
                    .build();
                pipeline = compiled;
            }
            GpuDevice device = RenderSystem.getDevice();
            CompiledRenderPipeline spike = RenderSystem.getCompiledPipeline(compiled);
            int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
                | GpuTexture.USAGE_RENDER_ATTACHMENT;
            src = device.createTexture("Actors pearl spike source", usage, GpuFormat.RGBA16_FLOAT, w, h, 1, 1);
            dst = device.createTexture("Actors pearl spike target", usage, GpuFormat.RGBA16_FLOAT, w, h, 1, 1);
            srcView = device.createTextureView(src);
            dstView = device.createTextureView(dst);
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            CommandEncoder encoder = device.createCommandEncoder();
            encoder.clearColorTexture(src, new Vector4f(0.2f, 0.4f, 0.8f, 1.0f));
            Optional<Vector4fc> clear = Optional.of(new Vector4f(0.0f, 0.0f, 0.0f, 1.0f));
            try (RenderPass pass =
                encoder.createRenderPass(() -> "Actors pearl passthrough", dstView, clear, null, OptionalDouble.empty())) {
                pass.setPipeline(spike);
                pass.setUniform("InSampler", srcView, sampler);
                pass.draw(3, 1, 0, 0);
            }
            encoder.submit();
            return true;
        } catch (Throwable error) {
            FlashbackActors.LOGGER.warn("[FilterPearlSpike] passthrough failed", error);
            return false;
        } finally {
            if (srcView != null) {
                srcView.close();
            }
            if (dstView != null) {
                dstView.close();
            }
            if (src != null) {
                src.close();
            }
            if (dst != null) {
                dst.close();
            }
        }
    }
}
