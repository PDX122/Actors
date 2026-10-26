package pdx.dev.fbactors.filters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

public final class FilterGpuPipeline implements AutoCloseable {
    /**
     * Whether raw GL calls are safe on this thread right now.
     *
     * <p>26.3 can run on non-GL backends (Vulkan via renderpearl), where there
     * is no current GL context and the first {@code glGet*} aborts the JVM
     * outright - nothing to catch. Every entry point that touches GL must bail
     * through here first and degrade to unfiltered rendering instead.
     */
    public static boolean isGlAvailable() {
        try {
            return GL.getCapabilities() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Boolean vulkanBackend;

    /**
     * Whether the game is running on the Vulkan backend (26.3+).
     *
     * <p>Detected reflectively so this shared file still compiles on older
     * targets whose {@code RenderSystem} has no {@code getDevice()}. Result is
     * cached: the backend cannot change at runtime.
     */
    public static boolean isVulkanBackend() {
        Boolean known = vulkanBackend;
        if (known != null) return known;
        boolean vulkan = detectVulkanBackend();
        vulkanBackend = vulkan;
        return vulkan;
    }

    private static boolean detectVulkanBackend() {
        try {
            Class<?> renderSystem = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
            final Object device;
            try {
                device = renderSystem.getMethod("getDevice").invoke(null);
            } catch (NoSuchMethodException e) {
                return false;
            }
            if (device == null) return false;
            return device.getClass().getName().toLowerCase(Locale.ROOT).contains("vulkan");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final String VERTEX = "#version 330 core\nout vec2 texCoord;\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=p;gl_Position=vec4(p*2.0-1.0,0.0,1.0);}";
    private static final int[] FLAGS = {GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL11.GL_CULL_FACE,
        GL11.GL_SCISSOR_TEST, GL11.GL_STENCIL_TEST, GL30.GL_FRAMEBUFFER_SRGB,
        GL30.GL_RASTERIZER_DISCARD, GL13.GL_SAMPLE_ALPHA_TO_COVERAGE, GL11.GL_COLOR_LOGIC_OP};
    private record Program(int id, Map<String, Integer> uniforms) {}
    private final Map<String, Program> programs = new HashMap<>();
    private final int[] textures = new int[3];
    private final int[] framebuffers = new int[4];
    private int testPattern;
    private int[] extentTextures = new int[0];
    private int extentFramebuffer;
    private int extentWidth;
    private int extentHeight;
    private int previewSource;
    private int previewAtlas;
    private final int[] previewBuffers = new int[3];
    private int previewHeight;
    public static final int PREVIEW_WIDTH = 256;
    public static final int PREVIEW_COLUMNS = 4;
    public int previewTexture() { return previewAtlas; }
    public int previewHeight() { return previewHeight; }
    private int vertex;
    private int vao;
    private int width;
    private int height;

    public static String source(String id) {
        if (!id.equals("compose") && !id.equals("actor_mosaic") && !id.equals("actor_extent") && FilterCatalog.find(id) == null) throw new IllegalArgumentException("Unknown filter: " + id);
        try (var stream = FilterGpuPipeline.class.getResourceAsStream("/assets/fbactors/filters/" + id + ".fsh")) {
            if (stream == null) throw new IllegalArgumentException("Missing shader: " + id);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    public void compileAll() {
        var failures = new java.util.ArrayList<String>();
        for (var filter : FilterCatalog.entries()) {
            try { program(filter.id()); } catch (RuntimeException error) { failures.add(error.getMessage()); }
        }
        try { program("actor_extent"); } catch (RuntimeException error) { failures.add(error.getMessage()); }
        try { program("actor_mosaic"); } catch (RuntimeException error) { failures.add(error.getMessage()); }
        try { program("compose"); } catch (RuntimeException error) { failures.add(error.getMessage()); }
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("\n", failures));
    }

    private Program program(String name) {
        Program cached = programs.get(name);
        if (cached != null) return cached;
        if (vertex == 0) vertex = shader(GL20.GL_VERTEX_SHADER, VERTEX, "fullscreen vertex");
        int fragment = shader(GL20.GL_FRAGMENT_SHADER, source(name), name);
        int id = GL20.glCreateProgram();
        try {
            GL20.glAttachShader(id, vertex);
            GL20.glAttachShader(id, fragment);
            GL20.glLinkProgram(id);
            if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(name + ": " + GL20.glGetProgramInfoLog(id));
            Program program = new Program(id, new HashMap<>());
            programs.put(name, program);
            return program;
        } catch (RuntimeException error) {
            GL20.glDeleteProgram(id);
            throw error;
        } finally { GL20.glDeleteShader(fragment); }
    }

    private static int shader(int type, String source, String name) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(id);
            GL20.glDeleteShader(id);
            throw new IllegalStateException(name + ": " + log);
        }
        return id;
    }

    private static void uniform(Program program, String name, float value) {
        int location = program.uniforms().computeIfAbsent(name, key -> GL20.glGetUniformLocation(program.id(), key));
        if (location >= 0) GL20.glUniform1f(location, value);
    }

    private static void sampler(Program program, String name, int texture, int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL33.glBindSampler(unit, 0);
        int location = program.uniforms().computeIfAbsent(name, key -> GL20.glGetUniformLocation(program.id(), key));
        if (location >= 0) GL20.glUniform1i(location, unit);
    }

    private void resize(int w, int h) {
        if (vao == 0) vao = GL30.glGenVertexArrays();
        if (framebuffers[0] == 0) for (int i = 0; i < framebuffers.length; i++) framebuffers[i] = GL30.glGenFramebuffers();
        if (w == width && h == height && textures[0] != 0) return;
        for (int texture : textures) if (texture != 0) GL11.glDeleteTextures(texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL33.glBindSampler(0, 0);
        for (int i = 0; i < textures.length; i++) {
            textures[i] = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[i]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, w, h, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            attach(framebuffers[i], textures[i]);
        }
        width = w;
        height = h;
    }

    private static void attach(int framebuffer, int texture) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Filter framebuffer is incomplete");
        }
    }

    public static int[] textureSize(int texture) {
        State state = new State();
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            return new int[] {GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH),
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT)};
        } finally { state.restore(); }
    }

    public void material(int source, int target, int w, int h, LookSettings look, float time) {
        State state = new State();
        int sourceBuffer = GL30.glGenFramebuffers();
        int targetBuffer = GL30.glGenFramebuffers();
        try {
            for (int flag : FLAGS) GL11.glDisable(flag);
            GL11.glColorMask(true, true, true, true);
            attach(sourceBuffer, source);
            attach(targetBuffer, target);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceBuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetBuffer);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            render(target, w, h, look, time);
        } finally {
            state.restore();
            GL30.glDeleteFramebuffers(sourceBuffer);
            GL30.glDeleteFramebuffers(targetBuffer);
        }
    }

    public void render(int colorTexture, int w, int h, LookSettings look, float time) {
        if (w <= 0 || h <= 0 || !look.enabled || look.intensity <= 0) return;
        State state = new State();
        try {
            for (int flag : FLAGS) GL11.glDisable(flag);
            GL11.glColorMask(true, true, true, true);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            resize(w, h);
            attach(framebuffers[3], colorTexture);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffers[3]);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[0]);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            GL11.glViewport(0, 0, w, h);
            GL30.glBindVertexArray(vao);
            int input = textures[0];
            int index = 0;
            for (String filter : look.filters) {
                int target = 1 + (index++ % 2);
                Program program = program(filter);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[target]);
                GL20.glUseProgram(program.id());
                sampler(program, "InSampler", input, 0);
                uniform(program, "Time", time);
                for (var param : FilterCatalog.params(filter)) uniform(program, param.id(), look.parameter(filter, param));
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                input = textures[target];
            }
            Program compose = program("compose");
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[3]);
            GL20.glUseProgram(compose.id());
            sampler(compose, "InSampler", input, 0);
            sampler(compose, "OriginalSampler", textures[0], 1);
            uniform(compose, "Intensity", look.intensity);
            uniform(compose, "Exposure", look.exposure);
            uniform(compose, "Contrast", look.contrast);
            uniform(compose, "Saturation", look.saturation);
            uniform(compose, "Temperature", look.temperature);
            uniform(compose, "Tint", look.tint);
            uniform(compose, "Vignette", look.vignette);
            uniform(compose, "Grain", look.grain);
            uniform(compose, "Time", time);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally { state.restore(); }
    }

    private int actorExtent(int depth, int w, int h, boolean reverse) {
        if (extentWidth != w || extentHeight != h || extentTextures.length == 0) {
            for (int texture : extentTextures) GL11.glDeleteTextures(texture);
            int levels = 0;
            for (int x = w, y = h; x > 1 || y > 1; levels++) { x = (x + 1) / 2; y = (y + 1) / 2; }
            extentTextures = new int[Math.max(1, levels)];
            int x = w, y = h;
            for (int i = 0; i < extentTextures.length; i++) {
                x = (x + 1) / 2;
                y = (y + 1) / 2;
                extentTextures[i] = previewTexture(x, y);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, x, y, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            }
            extentWidth = w;
            extentHeight = h;
        }
        if (extentFramebuffer == 0) extentFramebuffer = GL30.glGenFramebuffers();
        Program program = program("actor_extent");
        GL20.glUseProgram(program.id());
        int input = depth;
        int x = w, y = h;
        for (int i = 0; i < extentTextures.length; i++) {
            attach(extentFramebuffer, extentTextures[i]);
            GL11.glViewport(0, 0, (x + 1) / 2, (y + 1) / 2);
            sampler(program, "InSampler", input, 0);
            uniform(program, "FirstPass", i == 0 ? 1 : 0);
            uniform(program, "ReverseDepth", reverse ? 1 : 0);
            GL20.glUniform2f(GL20.glGetUniformLocation(program.id(), "InputSize"), x, y);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            input = extentTextures[i];
            x = (x + 1) / 2;
            y = (y + 1) / 2;
        }
        return input;
    }

    public void actorMosaic(int colorTexture, int actorDepth, int sceneDepth, int w, int h, float blockSize, float intensity, boolean reverse) {
        if (intensity <= 0 || w <= 0 || h <= 0) return;
        State state = new State();
        try {
            for (int flag : FLAGS) GL11.glDisable(flag);
            GL11.glColorMask(true, true, true, true);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            resize(w, h);
            GL30.glBindVertexArray(vao);
            int extent = actorExtent(actorDepth, w, h, reverse);
            attach(framebuffers[3], colorTexture);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffers[3]);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[0]);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[3]);
            GL11.glViewport(0, 0, w, h);
            GL30.glBindVertexArray(vao);
            Program program = program("actor_mosaic");
            GL20.glUseProgram(program.id());
            sampler(program, "InSampler", textures[0], 0);
            sampler(program, "ActorDepthSampler", actorDepth, 1);
            sampler(program, "SceneDepthSampler", sceneDepth, 2);
            sampler(program, "ExtentSampler", extent, 3);
            uniform(program, "BlockSize", blockSize);
            uniform(program, "Intensity", intensity);
            uniform(program, "ReverseDepth", reverse ? 1 : 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally { state.restore(); }
    }

    public void previews(int colorTexture, int w, int h, float time) { previews(colorTexture, w, h, time, false); }

    public void previews(int colorTexture, int w, int h, float time, boolean pattern) {
        if (w <= 0 || h <= 0) return;
        State state = new State();
        try {
            for (int flag : FLAGS) GL11.glDisable(flag);
            GL11.glColorMask(true, true, true, true);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            if (vao == 0) vao = GL30.glGenVertexArrays();
            if (previewBuffers[0] == 0) for (int i = 0; i < 3; i++) previewBuffers[i] = GL30.glGenFramebuffers();
            if (pattern) {
                if (testPattern == 0) testPattern = testPattern();
                colorTexture = testPattern;
                w = 256;
                h = 144;
            }
            int rows = (FilterCatalog.entries().size() + PREVIEW_COLUMNS - 1) / PREVIEW_COLUMNS;
            int ph = Math.max(45, Math.min(284, Math.round(PREVIEW_WIDTH * h / (float) w)));
            if (previewAtlas == 0 || previewHeight != ph) {
                if (previewAtlas != 0) GL11.glDeleteTextures(previewAtlas);
                if (previewSource != 0) GL11.glDeleteTextures(previewSource);
                previewSource = previewTexture(PREVIEW_WIDTH, ph);
                previewAtlas = previewTexture(PREVIEW_WIDTH * PREVIEW_COLUMNS, ph * rows);
                previewHeight = ph;
            }
            attach(previewBuffers[0], colorTexture);
            attach(previewBuffers[1], previewSource);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previewBuffers[0]);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previewBuffers[1]);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, PREVIEW_WIDTH, ph, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            attach(previewBuffers[2], previewAtlas);
            GL30.glBindVertexArray(vao);
            int index = 0;
            for (var entry : FilterCatalog.entries()) {
                GL11.glViewport((index % PREVIEW_COLUMNS) * PREVIEW_WIDTH, (index / PREVIEW_COLUMNS) * ph, PREVIEW_WIDTH, ph);
                Program program = program(entry.id());
                GL20.glUseProgram(program.id());
                sampler(program, "InSampler", previewSource, 0);
                uniform(program, "Time", time);
                for (var param : FilterCatalog.params(entry.id())) uniform(program, param.id(), param.value());
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                index++;
            }
        } finally { state.restore(); }
    }

    private static int testPattern() {
        java.nio.FloatBuffer pixels = org.lwjgl.BufferUtils.createFloatBuffer(256 * 144 * 4);
        for (int y = 0; y < 144; y++) for (int x = 0; x < 256; x++) {
            float r = x / 256f, g = y / 144f, b = 0.22f + 0.4f * (1 - r);
            if (x > 70 && x < 185 && y > 40 && y < 110) {
                float checker = (x / 9 + y / 9) % 2 == 0 ? 0.22f : 0.85f;
                r = g = b = checker;
            }
            if (y < 20) r = g = b = x / 255f;
            pixels.put(r).put(g).put(b).put(1);
        }
        pixels.flip();
        int texture = previewTexture(256, 144);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 256, 144, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
        return texture;
    }

    private static int previewTexture(int w, int h) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, w, h, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        return texture;
    }

    @Override public void close() {
        for (var program : programs.values()) GL20.glDeleteProgram(program.id());
        programs.clear();
        for (int texture : extentTextures) GL11.glDeleteTextures(texture);
        extentTextures = new int[0];
        if (extentFramebuffer != 0) GL30.glDeleteFramebuffers(extentFramebuffer);
        extentFramebuffer = extentWidth = extentHeight = 0;
        if (testPattern != 0) GL11.glDeleteTextures(testPattern);
        testPattern = 0;
        if (previewSource != 0) GL11.glDeleteTextures(previewSource);
        if (previewAtlas != 0) GL11.glDeleteTextures(previewAtlas);
        for (int buffer : previewBuffers) if (buffer != 0) GL30.glDeleteFramebuffers(buffer);
        java.util.Arrays.fill(previewBuffers, 0);
        previewSource = previewAtlas = previewHeight = 0;
        if (vertex != 0) GL20.glDeleteShader(vertex);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        for (int texture : textures) if (texture != 0) GL11.glDeleteTextures(texture);
        for (int framebuffer : framebuffers) if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        java.util.Arrays.fill(textures, 0);
        java.util.Arrays.fill(framebuffers, 0);
        vertex = vao = width = height = 0;
    }

    public static final class State {
        /** Texture units snapshotted per State. Entity rendering stays within these. */
        private static final int UNIT_COUNT = 8;
        /** GL_BLEND_COLOR. Kept as a literal: LWJGL files the enum under ARBImaging. */
        private static final int BLEND_COLOR = 0x8005;
        private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int[] polygon = new int[2];
        private final int[] viewport = new int[4];
        private final int[] bindings = new int[UNIT_COUNT];
        private final int[] samplers = new int[UNIT_COUNT];
        private final boolean[] flags = new boolean[FLAGS.length];
        private final boolean[] mask = new boolean[4];
        private final int blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha;
        private final int blendEqRgb, blendEqAlpha;
        private final float[] blendColor = new float[4];
        private final double[] depthRange = new double[2];
        private final int[] scissor = new int[4];
        private final int logicOp;
        private final int packAlignment, unpackAlignment;

        public State() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, polygon);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            GL11.glGetFloatv(BLEND_COLOR, blendColor);
            GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, depthRange);
            blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            blendEqRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            blendEqAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
            logicOp = GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE);
            packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            for (int i = 0; i < FLAGS.length; i++) flags[i] = GL11.glIsEnabled(FLAGS[i]);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var bytes = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, bytes);
                for (int i = 0; i < 4; i++) mask[i] = bytes.get(i) != 0;
            }
            for (int unit = 0; unit < UNIT_COUNT; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                bindings[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                samplers[unit] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit);
            }
            GL13.glActiveTexture(active);
        }

        public void restore() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            for (int unit = 0; unit < UNIT_COUNT; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, bindings[unit]);
                GL33.glBindSampler(unit, samplers[unit]);
            }
            GL13.glActiveTexture(active);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glColorMask(mask[0], mask[1], mask[2], mask[3]);
            GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            GL20.glBlendEquationSeparate(blendEqRgb, blendEqAlpha);
            GL14.glBlendColor(blendColor[0], blendColor[1], blendColor[2], blendColor[3]);
            GL11.glDepthRange(depthRange[0], depthRange[1]);
            GL11.glLogicOp(logicOp);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, packAlignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
            GL11.glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygon[0]);
            for (int i = 0; i < FLAGS.length; i++) {
                if (flags[i]) GL11.glEnable(FLAGS[i]); else GL11.glDisable(FLAGS[i]);
            }
        }
    }
}
