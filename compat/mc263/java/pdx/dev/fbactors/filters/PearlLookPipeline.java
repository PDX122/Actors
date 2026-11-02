package pdx.dev.fbactors.filters;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.resources.Identifier;

public final class PearlLookPipeline implements AutoCloseable {
    private record Program(CompiledRenderPipeline pipeline, PortableLookShader shader) {}
    private final GpuDevice device;
    private final GpuSampler sampler;
    private final GpuSampler nearest;
    private final Map<String, Program> programs = new HashMap<>();
    private final GpuTexture[] textures = new GpuTexture[3];
    private final GpuTextureView[] views = new GpuTextureView[3];
    private int width, height;
    private GpuFormat format;

    public PearlLookPipeline(GpuDevice device) {
        this.device = device;
        sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
            FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
        nearest = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
            FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
    }

    public void render(GpuTexture source, GpuTextureView destination, LookSettings look, float time) {
        if (!look.enabled || look.intensity <= 0) return;
        resize(source.getWidth(0), source.getHeight(0), source.getFormat());
        var encoder = device.createCommandEncoder();
        try (var sourceView = device.createTextureView(source)) {
            draw(encoder, "copy", sourceView, sourceView, views[0], format, look, time);
            GpuTextureView input = views[0];
            int index = 0;
            for (String filter : look.filters) {
                int output = 1 + index++ % 2;
                draw(encoder, filter, input, views[0], views[output], format, look, time);
                input = views[output];
            }
            draw(encoder, "compose", input, views[0], destination, destination.texture().getFormat(), look, time);
            encoder.submit();
        }
    }

    private Program program(String filter, GpuFormat outputFormat) {
        String key = filter + ":" + outputFormat;
        Program cached = programs.get(key);
        if (cached != null) return cached;
        cached = compile(filter,outputFormat,filter.equals("copy") ? """
            #version 330 core
            uniform sampler2D InSampler;
            in vec2 texCoord;
            out vec4 fragColor;
            void main(){fragColor=texture(InSampler,texCoord);}
            """ : FilterGpuPipeline.source(filter));
        programs.put(key,cached); return cached;
    }
    public static void validateCustom(String source) {
        try(var pipeline=new PearlLookPipeline(com.mojang.blaze3d.systems.RenderSystem.getDevice())) {
            var compiled=pipeline.compile("custom-validation",GpuFormat.RGBA8_UNORM,source);
            compiled.pipeline().close();
        }
    }
    private Program compile(String filter,GpuFormat outputFormat,String source) {
        var shader=PortableLookShader.from(source);
        var layout = BindGroupLayout.builder();
        for (String name : shader.samplers()) layout.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
        if (!shader.floats().isEmpty()) layout.withUniform("LookUniforms", UniformType.UNIFORM_BUFFER);
        var id = Identifier.fromNamespaceAndPath("fbactors", "portable/" + filter);
        var pipeline = RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id)
            .withBindGroupLayout(layout.build()).withCull(false).withDepthStencilState(Optional.empty())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(Optional.empty(), outputFormat, ColorTargetState.WRITE_ALL)).build();
        ShaderSource sources = new ShaderSource() {
            @Override public String getShader(Identifier location, ShaderType type) {
                return type == ShaderType.VERTEX ? PortableLookShader.VERTEX : shader.source();
            }
            @Override public CachedIncludeSource getInclude(Identifier location) { return null; }
            @Override public void close() {}
        };
        var compiled = device.compilePipeline(pipeline, sources, Runnable::run).join().finishCompile();
        return new Program(compiled, shader);
    }

    public void renderActor(GpuTexture source, GpuTextureView destination, GpuTextureView actorDepth,
                            GpuTextureView sceneDepth, GpuTextureView bounds, LookSettings look, float time) {
        renderActor(source, destination, actorDepth, sceneDepth, sceneDepth, bounds, look, time);
    }

    public void renderActor(GpuTexture source, GpuTextureView destination, GpuTextureView actorDepth,
                            GpuTextureView sceneDepth, GpuTextureView entityDepth, GpuTextureView bounds, LookSettings look, float time) {
        if (!look.enabled || look.intensity <= 0) return;
        resize(source.getWidth(0), source.getHeight(0), source.getFormat());
        var encoder = device.createCommandEncoder();
        var depth = Map.of("ReverseDepth", 1f);
        try (var sourceView = device.createTextureView(source)) {
            draw(encoder, "copy", Map.of("InSampler", sourceView), views[0], look, time, Map.of());
            draw(encoder, "actor_source", Map.of("InSampler", views[0], "ActorDepthSampler", actorDepth,
                "EntityDepthSampler", entityDepth, "SceneDepthSampler", sceneDepth), views[1], look, time, depth);
            int input = 1;
            for (String filter : look.filters) {
                int output = input == 1 ? 2 : 1;
                if (filter.equals("tessera")) {
                    draw(encoder, "copy", Map.of("InSampler", views[input]), views[output], look, time, Map.of());
                    draw(encoder, "actor_mosaic", Map.of("InSampler", views[input], "ActorDepthSampler", actorDepth,
                        "EntityDepthSampler", entityDepth, "SceneDepthSampler", sceneDepth, "ExtentSampler", bounds), views[output], look, time,
                        Map.of("ReverseDepth", 1f, "Intensity", 1f,
                            "BlockSize", look.parameter(filter, FilterCatalog.params(filter).getFirst())));
                } else {
                    draw(encoder, filter, Map.of("InSampler", views[input]), views[output], look, time, Map.of());
                }
                input = output;
            }
            int graded = input == 1 ? 2 : 1;
            draw(encoder, "compose", Map.of("InSampler", views[input], "OriginalSampler", views[0]), views[graded], look, time, Map.of());
            draw(encoder, "actor_composite", Map.of("InSampler", views[graded], "OriginalSampler", views[0],
                "ActorDepthSampler", actorDepth, "EntityDepthSampler", entityDepth, "SceneDepthSampler", sceneDepth), destination, look, time, depth);
            encoder.submit();
        }
    }

    private void draw(CommandEncoder encoder, String filter, GpuTextureView input, GpuTextureView original,
                      GpuTextureView output, GpuFormat outputFormat, LookSettings look, float time) {
        draw(encoder, filter, Map.of("InSampler", input, "OriginalSampler", original), output, look, time, Map.of());
    }

    public void draw(CommandEncoder encoder, String filter, Map<String, GpuTextureView> inputs,
                     GpuTextureView output, LookSettings look, float time, Map<String, Float> overrides) {
        var program = program(filter, output.texture().getFormat());
        var uniforms = program.shader().floats().isEmpty() ? null : encoder.transientMemory()
            .uploadGpu(program.shader().uniforms(filter, look, time, overrides), 256, GpuBuffer.USAGE_UNIFORM);
        try (var pass = encoder.createRenderPass(() -> "Actors " + filter, output, Optional.empty())) {
            pass.setPipeline(program.pipeline());
            for (String name : program.shader().samplers()) {
                var input = inputs.get(name);
                if (input == null && name.equals("EntityDepthSampler")) input = inputs.get("SceneDepthSampler");
                if (input == null) throw new IllegalArgumentException("Missing sampler: " + name);
                pass.setUniform(name, input, input.texture().getFormat() == GpuFormat.D32_FLOAT ? nearest : sampler);
            }
            if (uniforms != null) pass.setUniform("LookUniforms", uniforms);
            pass.draw(3, 1, 0, 0);
        }
    }

    private void resize(int w, int h, GpuFormat sourceFormat) {
        if (w == width && h == height && sourceFormat == format && textures[0] != null) return;
        closeTextures();
        width = w; height = h; format = sourceFormat;
        int usage = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_RENDER_ATTACHMENT;
        for (int i = 0; i < textures.length; i++) {
            textures[i] = device.createTexture("Actors look pass " + i, usage, GpuFormat.RGBA16_FLOAT, w, h, 1, 1);
            views[i] = device.createTextureView(textures[i]);
        }
    }

    private void closeTextures() {
        for (int i = 0; i < textures.length; i++) {
            if (views[i] != null) views[i].close();
            if (textures[i] != null) textures[i].close();
            views[i] = null; textures[i] = null;
        }
    }

    @Override public void close() {
        closeTextures();
        programs.values().forEach(p -> p.pipeline().close());
        programs.clear();
        sampler.close();
        nearest.close();
    }
}
