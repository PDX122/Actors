package pdx.dev.fbactors.filters;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * CPU port of the actor-look bake for backends without raw GL (Vulkan).
 *
 * <p>The GPU path ({@link FilterGpuPipeline#material}) is unavailable when
 * {@link FilterGpuPipeline#isGlAvailable()} is false: there is no GL context
 * and the first {@code glGet*} would abort the JVM. {@link ActorLooks#look()}
 * therefore returns null there and actor materials previously fell back to
 * ungraded skins. This kernel bakes the same grade into the cached
 * {@link DynamicTexture} with plain pixel math instead, so no GL, renderpearl
 * or compat classes are touched and this file compiles unchanged on all nine
 * targets.
 *
 * <p>Faithfulness: the compose grade (exposure, temperature, tint,
 * saturation, contrast, vignette, grain) is ported formula-for-formula from
 * {@code compose.fsh} in the same order, and every supported filter slot is
 * ported from its {@code .fsh} main (same order, same constants, same
 * defaults from {@link FilterCatalog}). Filters that sample neighbouring
 * texels (blur/glow/edge/mosaic kernels) or remap the screen are not
 * reproducible per-pixel and make {@link #bake} return false, in which case
 * the caller keeps the original texture - exactly today's Vulkan behaviour.
 * Time-varying terms are evaluated at the caller's bake time (the bridge
 * passes {@code replayTick / 20}, matching the GPU path, and the RENDERED
 * signature rebakes per tick), so each bake is a frozen frame.
 *
 * <p>Alpha is never touched: every filter shader preserves
 * {@code original.a} and so does this kernel.
 */
public final class LookCpuBake {
    /** Mirrors the bridge guard: skins are tiny, never bake anything larger. */
    public static final int MAX_DIMENSION = 4096;
    private static final float[] LUMA = {0.2126f, 0.7152f, 0.0722f};

    private LookCpuBake() {}

    /**
     * Bakes {@code look} over {@code source} into {@code target} (same size).
     *
     * <p>Pixel I/O uses only APIs present on all nine targets. The raw-pixel
     * array getter and setter were renamed across versions ({@code
     * getPixelsRGBA}/{@code setPixelRGBA} on 1.21.1, {@code getPixelsABGR}/
     * {@code setPixelABGR} later, identical semantics), so both are resolved
     * once via reflection. ({@link NativeImage#makePixelArray()} would also
     * work but is deprecated.)
     *
     * @return true when the bake ran and the target was uploaded, false when
     *         the look cannot be reproduced on CPU (unknown or
     *         neighbour-sampling filter) or any image is unusable; never throws
     */
    public static boolean bake(DynamicTexture target, NativeImage source, LookSettings look, float time) {
        try {
            if (target == null || source == null || look == null) return false;
            if (!look.enabled || look.intensity <= 0) return false;
            int width = source.getWidth(), height = source.getHeight();
            if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) return false;
            NativeImage dest = target.getPixels();
            if (dest == null || dest.getWidth() != width || dest.getHeight() != height) return false;
            int[] raw = readRawPixels(source);
            if (raw == null) return false;
            int[] pixels = bakePixels(raw, width, height, look, time);
            if (pixels == null) return false;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                if (!writeRawPixel(dest, x, y, pixels[y * width + x])) return false;
            }
            target.upload();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Pure-Java core of {@link #bake}: grades raw pixels, no MC calls beyond
     * plain arrays. Input and output packing matches the raw native getters
     * ({@code getPixelsABGR}/{@code getPixelsRGBA}): red in the lowest byte,
     * alpha in the highest. Returns the graded raw pixels, or null when the
     * look cannot be reproduced on CPU.
     */
    public static int[] bakePixels(int[] src, int width, int height, LookSettings look, float time) {
        if (src == null || look == null || width <= 0 || height <= 0) return null;
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || src.length < width * height) return null;
        if (!look.enabled || look.intensity <= 0 || !Float.isFinite(time)) return null;
        for (String filter : look.filters) if (!supports(filter)) return null;
        int count = width * height;
        float[] original = new float[3 * count];
        float[] alpha = new float[count];
        for (int i = 0; i < count; i++) {
            int pixel = src[i];
            original[3 * i] = (pixel & 0xFF) / 255f;
            original[3 * i + 1] = ((pixel >> 8) & 0xFF) / 255f;
            original[3 * i + 2] = ((pixel >> 16) & 0xFF) / 255f;
            alpha[i] = (pixel >>> 24) / 255f;
        }
        float[] current = original.clone();
        float[] next = new float[3 * count];
        for (String filter : look.filters) {
            applyFilter(filter, current, next, width, height, look, time);
            float[] swap = current;
            current = next;
            next = swap;
        }
        int[] out = new int[count];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = y * width + x;
            float u = (x + 0.5f) / width, v = (y + 0.5f) / height;
            float r = current[3 * i], g = current[3 * i + 1], b = current[3 * i + 2];
            float or = original[3 * i], og = original[3 * i + 1], ob = original[3 * i + 2];
            // compose.fsh, same order: exposure, temperature/tint, saturation, contrast, vignette, grain.
            float exp = exp2(look.exposure);
            r *= exp;
            g *= exp;
            b *= exp;
            float wr = 1f + look.temperature * 0.12f + look.tint * 0.04f;
            float wg = 1f - look.tint * 0.08f;
            float wb = 1f - look.temperature * 0.12f + look.tint * 0.04f;
            r *= wr;
            g *= wg;
            b *= wb;
            float l = r * LUMA[0] + g * LUMA[1] + b * LUMA[2];
            r = l + (r - l) * look.saturation;
            g = l + (g - l) * look.saturation;
            b = l + (b - l) * look.saturation;
            r = (r - 0.5f) * look.contrast + 0.5f;
            g = (g - 0.5f) * look.contrast + 0.5f;
            b = (b - 0.5f) * look.contrast + 0.5f;
            float px = u * 2f - 1f, py = v * 2f - 1f;
            float dim = 1f - look.vignette * smoothstep(0.25f, 1.5f, px * px + py * py);
            r *= dim;
            g *= dim;
            b *= dim;
            float frame = (float) Math.floor(time * 24f);
            float grain = hash(x + 0.5f + frame * 17f, y + 0.5f + frame * 7f) - 0.5f;
            r += grain * look.grain;
            g += grain * look.grain;
            b += grain * look.grain;
            r = or + (clamp01(r) - or) * look.intensity;
            g = og + (clamp01(g) - og) * look.intensity;
            b = ob + (clamp01(b) - ob) * look.intensity;
            int a = Math.round(clamp01(alpha[i]) * 255f);
            out[i] = (a << 24) | (toByte(b) << 16) | (toByte(g) << 8) | toByte(r);
        }
        return out;
    }

    private static volatile java.lang.reflect.Method rawPixelReader;
    private static volatile boolean rawPixelReaderProbed;

    /** Reads the raw pixel array via the version-specific getter. */
    private static int[] readRawPixels(NativeImage image) {
        try {
            java.lang.reflect.Method reader = rawPixelReader;
            if (reader == null && !rawPixelReaderProbed) {
                reader = probeRawPixelReader();
                rawPixelReader = reader;
                rawPixelReaderProbed = true;
            }
            if (reader == null) return null;
            int[] pixels = (int[]) reader.invoke(image);
            return pixels == null ? null : pixels.clone();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static java.lang.reflect.Method probeRawPixelReader() {
        for (String name : new String[] {"getPixelsABGR", "getPixelsRGBA"}) {
            try {
                return NativeImage.class.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static volatile java.lang.reflect.Method rawPixelWriter;
    private static volatile boolean rawPixelProbed;

    /**
     * Writes one raw pixel without depending on the version-specific setter
     * name ({@code setPixelABGR} everywhere except 1.21.1, where it is
     * {@code setPixelRGBA} with identical semantics). The writer is probed
     * once; any failure returns false so the caller falls back.
     */
    private static boolean writeRawPixel(NativeImage image, int x, int y, int raw) {
        try {
            java.lang.reflect.Method writer = rawPixelWriter;
            if (writer == null && !rawPixelProbed) {
                writer = probeRawPixelWriter();
                rawPixelWriter = writer;
                rawPixelProbed = true;
            }
            if (writer == null) return false;
            writer.invoke(image, x, y, raw);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static java.lang.reflect.Method probeRawPixelWriter() {
        for (String name : new String[] {"setPixelABGR", "setPixelRGBA"}) {
            try {
                return NativeImage.class.getMethod(name, int.class, int.class, int.class);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /**
     * Whether the kernel reproduces this filter per-pixel. Neighbour-sampling
     * filters (blur/glow/edge/mosaic kernels: anamorphic, aurora, blueprint,
     * cel_shade, contour_light, crosshatch, diffusion, ember, halation,
     * inkwash, luma_relief, neon_wire, opal, pearl_bloom, silver_screen,
     * solarize, tessera) return false so the caller keeps the original.
     */
    public static boolean supports(String id) {
        return switch (id == null ? "" : id) {
            case "arcade", "bleach", "blue_hour", "candle", "carbon", "celestial", "chalk",
                "chromatic_ink", "copper", "daybreak", "duotone", "evergreen", "glacier",
                "halftone", "heatmap", "honey", "infrared", "lavender", "midnight",
                "night_vision", "prism", "risograph", "roseglass", "selective_red",
                "signal", "sunprint", "velvet" -> true;
            default -> false;
        };
    }

    private static void applyFilter(String id, float[] current, float[] next,
            int width, int height, LookSettings look, float time) {
        switch (id) {
            case "arcade" -> filterArcade(current, next, width, height, look);
            case "bleach" -> filterBleach(current, next, width, height, time);
            case "blue_hour" -> filterBlueHour(current, next, width, height);
            case "candle" -> filterCandle(current, next, width, height);
            case "carbon" -> filterCarbon(current, next, width, height);
            case "celestial" -> filterCelestial(current, next, width, height);
            case "chalk" -> filterChalk(current, next, width, height);
            case "chromatic_ink" -> filterChromaticInk(current, next, width, height);
            case "copper" -> filterCopper(current, next, width, height);
            case "daybreak" -> filterDaybreak(current, next, width, height);
            case "duotone" -> filterDuotone(current, next, width, height, look);
            case "evergreen" -> filterEvergreen(current, next, width, height);
            case "glacier" -> filterGlacier(current, next, width, height);
            case "halftone" -> filterHalftone(current, next, width, height, look);
            case "heatmap" -> filterHeatmap(current, next, width, height);
            case "honey" -> filterHoney(current, next, width, height);
            case "infrared" -> filterInfrared(current, next, width, height, look);
            case "lavender" -> filterLavender(current, next, width, height);
            case "midnight" -> filterMidnight(current, next, width, height);
            case "night_vision" -> filterNightVision(current, next, width, height, look, time);
            case "prism" -> filterPrism(current, next, width, height, look);
            case "risograph" -> filterRisograph(current, next, width, height, look);
            case "roseglass" -> filterRoseglass(current, next, width, height);
            case "selective_red" -> filterSelectiveRed(current, next, width, height, look);
            case "signal" -> filterSignal(current, next, width, height, time);
            case "sunprint" -> filterSunprint(current, next, width, height);
            case "velvet" -> filterVelvet(current, next, width, height);
            default -> throw new IllegalArgumentException("Unsupported CPU filter: " + id);
        }
    }

    // -- filter ports (each mirrors its .fsh main; inputs are the current stage,
    // -- direct fetches read the exact texel, offset fetches bilinearly sample) --

    private static void filterBleach(float[] in, float[] out, int w, int h, float time) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float[] c = linear(in[i], in[i + 1], in[i + 2]);
            float l = luma(c);
            c[0] = l + (c[0] - l) * 0.40f;
            c[1] = l + (c[1] - l) * 0.40f;
            c[2] = l + (c[2] - l) * 0.40f;
            c[0] = (float) Math.pow(c[0], 1.16) * 1.14f;
            c[1] = (float) Math.pow(c[1], 1.16) * 1.14f;
            c[2] = (float) Math.pow(c[2], 1.16) * 1.14f;
            splitTone(c, 0.93f, 0.99f, 1.05f, 1.04f, 1.02f, 0.97f, 0.40f);
            c[0] /= 1f + c[0] * 0.15f;
            c[1] /= 1f + c[1] * 0.15f;
            c[2] /= 1f + c[2] * 0.15f;
            finish(c, 0.005f, x, y, time);
            write(out, i, c);
        }
    }

    private static void filterBlueHour(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = in[i], g = in[i + 1], b = in[i + 2];
            float l = luma(r, g, b);
            float shadow = 1f - smoothstep(0.12f, 0.8f, l);
            r = (float) Math.pow(Math.max(r, 0f), 1.12);
            g = (float) Math.pow(Math.max(g, 0f), 1.12);
            b = (float) Math.pow(Math.max(b, 0f), 1.12);
            r = l * 0.85f + (r - l * 0.85f) * 0.9f;
            g = l * 0.85f + (g - l * 0.85f) * 0.9f;
            b = l * 0.85f + (b - l * 0.85f) * 0.9f;
            r += -0.055f * shadow;
            g += 0.025f * shadow;
            b += 0.18f * shadow;
            float t = smoothstep(0.6f, 1.0f, l);
            r *= 1f + (1.12f - 1f) * t;
            g *= 1f + (1.04f - 1f) * t;
            b *= 1f + (0.86f - 1f) * t;
            writeClamped(out, i, r, g, b);
        }
    }

    private static void filterCandle(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float[] c = linear(in[i], in[i + 1], in[i + 2]);
            float l = luma(c);
            splitTone(c, 0.88f, 0.75f, 0.68f, 1.35f, 1.04f, 0.67f, 0.8f);
            c[0] = display(c[0] / (1f + c[0] * 0.22f));
            c[1] = display(c[1] / (1f + c[1] * 0.22f));
            c[2] = display(c[2] / (1f + c[2] * 0.22f));
            c[0] += 0.012f * (1f - l);
            c[1] += 0.004f * (1f - l);
            c[2] += 0.001f * (1f - l);
            writeClamped(out, i, c[0], c[1], c[2]);
        }
    }

    private static void filterCarbon(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float v = smoothstep(0.035f, 0.965f, luma(in[i], in[i + 1], in[i + 2]));
            writeClamped(out, i, v, v, v);
        }
    }

    private static void filterCelestial(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = in[i], g = in[i + 1], b = in[i + 2];
            float l = luma(r, g, b);
            r = l + (r - l) * 1.24f;
            g = l + (g - l) * 1.24f;
            b = l + (b - l) * 1.24f;
            r = (float) Math.pow(Math.max(r, 0f), 1.13);
            g = (float) Math.pow(Math.max(g, 0f), 1.13);
            b = (float) Math.pow(Math.max(b, 0f), 1.13);
            float t = 1f - smoothstep(0.08f, 0.82f, l);
            r += -0.05f * t;
            g += 0.07f * t;
            b += 0.17f * t;
            float m = smoothstep(0.64f, 1.0f, l);
            r *= 1f + (1.12f - 1f) * m;
            g *= 1f + (1.04f - 1f) * m;
            b *= 1f + (0.87f - 1f) * m;
            writeClamped(out, i, r, g, b);
        }
    }

    private static void filterChalk(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float v = (float) Math.pow(luma(in[i], in[i + 1], in[i + 2]), 0.72) * 0.93f + 0.04f;
            writeClamped(out, i, v, v * 0.99f, v * 0.97f);
        }
    }

    private static void filterChromaticInk(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = in[i], g = in[i + 1], b = in[i + 2];
            float cr = 0.02f + smoothstep(0.25f, 0.8f, r) * 0.65f + smoothstep(0.25f, 0.8f, g) * 0.03f
                + smoothstep(0.25f, 0.8f, b) * 0.4f;
            float cg = 0.02f + smoothstep(0.25f, 0.8f, r) * 0.07f + smoothstep(0.25f, 0.8f, g) * 0.6f
                + smoothstep(0.25f, 0.8f, b) * 0.28f;
            float cb = 0.02f + smoothstep(0.25f, 0.8f, r) * 0.32f + smoothstep(0.25f, 0.8f, g) * 0.57f
                + smoothstep(0.25f, 0.8f, b) * 0.02f;
            writeClamped(out, i, cr, cg, cb);
        }
    }

    private static void filterCopper(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float[] c = linear(in[i], in[i + 1], in[i + 2]);
            splitTone(c, 0.66f, 1.03f, 1.12f, 1.29f, 0.98f, 0.73f, 0.83f);
            c[0] = display(c[0] / (1f + c[0] * 0.1f));
            c[1] = display(c[1] / (1f + c[1] * 0.1f));
            c[2] = display(c[2] / (1f + c[2] * 0.1f));
            writeClamped(out, i, c[0], c[1], c[2]);
        }
    }

    private static void filterDaybreak(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = in[i], g = in[i + 1], b = in[i + 2];
            float l = luma(r, g, b);
            r = (float) Math.pow(Math.max(r, 0f), 0.88);
            g = (float) Math.pow(Math.max(g, 0f), 0.88);
            b = (float) Math.pow(Math.max(b, 0f), 0.88);
            r = l + (r - l) * 1.15f;
            g = l + (g - l) * 1.15f;
            b = l + (b - l) * 1.15f;
            float warm = smoothstep(0.12f, 0.85f, l);
            r += 0.14f * warm;
            g += 0.045f * warm;
            b += -0.045f * warm;
            float cool = 1f - smoothstep(0.05f, 0.4f, l);
            r += 0.015f * cool;
            g += 0.03f * cool;
            b += 0.045f * cool;
            writeClamped(out, i, r, g, b);
        }
    }

    private static void filterDuotone(float[] in, float[] out, int w, int h, LookSettings look) {
        float contrast = param(look, "duotone", "Contrast");
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = clamp01(in[i]), g = clamp01(in[i + 1]), b = clamp01(in[i + 2]);
            float l = luma(r, g, b);
            float tone = smoothstep(0.05f, 0.88f, (float) Math.pow(Math.max(l, 0f), contrast));
            r = 0.035f + (1.0f - 0.035f) * tone;
            g = 0.045f + (0.35f - 0.045f) * tone;
            b = 0.22f + (0.25f - 0.22f) * tone;
            float m = smoothstep(0.8f, 1.0f, l) * 0.5f;
            r += (1.0f - r) * m;
            g += (0.88f - g) * m;
            b += (0.67f - b) * m;
            writeClamped(out, i, r, g, b);
        }
    }

    private static void filterEvergreen(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float[] c = linear(in[i], in[i + 1], in[i + 2]);
            float l = luma(c);
            c[0] = l + (c[0] - l) * 0.82f;
            c[1] = l + (c[1] - l) * 0.82f;
            c[2] = l + (c[2] - l) * 0.82f;
            splitTone(c, 0.8f, 1.14f, 0.93f, 1.12f, 1.07f, 0.92f, 0.85f);
            c[0] = display(c[0]);
            c[1] = display(c[1]);
            c[2] = display(c[2]);
            writeClamped(out, i, c[0], c[1], c[2]);
        }
    }

    private static void filterGlacier(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = (in[i] - 0.5f) * 1.10f + 0.5f;
            float g = (in[i + 1] - 0.5f) * 1.10f + 0.5f;
            float b = (in[i + 2] - 0.5f) * 1.10f + 0.5f;
            r *= 0.93f;
            g *= 1.02f;
            b *= 1.10f;
            float l = luma(r, g, b);
            r += (l - r) * 0.12f;
            g += (l - g) * 0.12f;
            b += (l - b) * 0.12f;
            writeClamped(out, i, r, g, b);
        }
    }

    private static void filterHalftone(float[] in, float[] out, int w, int h, LookSettings look) {
        float spacing = param(look, "halftone", "Spacing");
        float cell = Math.max(2f, spacing * Math.max(h / 540f, 0.5f));
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = clamp01(in[i]), g = clamp01(in[i + 1]), b = clamp01(in[i + 2]);
            float px = (x + 0.5f) / cell, py = (y + 0.5f) / cell;
            float rx = 0.866f * px + 0.5f * py, ry = -0.5f * px + 0.866f * py;
            float mx = 0.866f * px - 0.5f * py, my = 0.5f * px + 0.866f * py;
            float dc = dist(fract(rx) - 0.5f, fract(ry) - 0.5f);
            float dm = dist(fract(mx) - 0.5f, fract(my) - 0.5f);
            float dy = dist(fract(px) - 0.5f, fract(py) - 0.5f);
            float dotR = 1f - smoothstep((float) Math.sqrt(1f - r) * 0.58f - 0.045f,
                (float) Math.sqrt(1f - r) * 0.58f + 0.045f, dc);
            float dotG = 1f - smoothstep((float) Math.sqrt(1f - g) * 0.58f - 0.045f,
                (float) Math.sqrt(1f - g) * 0.58f + 0.045f, dm);
            float dotB = 1f - smoothstep((float) Math.sqrt(1f - b) * 0.58f - 0.045f,
                (float) Math.sqrt(1f - b) * 0.58f + 0.045f, dy);
            writeClamped(out, i, (1f - dotR * 0.91f), (1f - dotG * 0.91f) * 0.96f, (1f - dotB * 0.91f) * 0.86f);
        }
    }

    private static void filterHeatmap(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float l = luma(in[i], in[i + 1], in[i + 2]);
            float t0 = smoothstep(0.0f, 0.45f, l);
            float ar = 0.035f + (0.48f - 0.035f) * t0;
            float ag = 0.025f + (0.08f - 0.025f) * t0;
            float ab = 0.15f + (0.62f - 0.15f) * t0;
            float t1 = smoothstep(0.35f, 0.75f, l);
            float br = ar + (1.0f - ar) * t1;
            float bg = ag + (0.32f - ag) * t1;
            float bb = ab + (0.23f - ab) * t1;
            float t2 = smoothstep(0.7f, 1.0f, l);
            writeClamped(out, i, br + (1.0f - br) * t2, bg + (0.94f - bg) * t2, bb + (0.61f - bb) * t2);
        }
    }

    private static void filterHoney(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = in[i], g = in[i + 1], b = in[i + 2];
            float l = luma(r, g, b);
            r = l + (r - l) * 0.88f;
            g = l + (g - l) * 0.88f;
            b = l + (b - l) * 0.88f;
            r = r * 1.08f + 0.02f;
            g = g * 1.02f + 0.012f;
            b = b * 0.87f + 0.005f;
            r /= 0.96f + r * 0.04f;
            g /= 0.96f + g * 0.04f;
            b /= 0.96f + b * 0.04f;
            writeClamped(out, i, r, g, b);
        }
    }

    private static void filterInfrared(float[] in, float[] out, int w, int h, LookSettings look) {
        float foliage = param(look, "infrared", "Foliage");
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = clamp01(in[i]), g = clamp01(in[i + 1]), b = clamp01(in[i + 2]);
            float l = luma(r, g, b);
            float vegetation = smoothstep(-0.035f, 0.14f, g - Math.max(r, b) * 0.92f);
            float cr = (b * 0.45f + r * 0.16f) + ((0.5f + l * 0.8f) - (b * 0.45f + r * 0.16f)) * vegetation * foliage;
            float cg = (b * 0.85f + g * 0.14f) + ((l * 0.23f + 0.035f) - (b * 0.85f + g * 0.14f)) * vegetation * foliage;
            float cb = (g * 0.84f + b * 0.2f) + ((0.22f + l * 0.45f) - (g * 0.84f + b * 0.2f)) * vegetation * foliage;
            float m = smoothstep(0.76f, 1.0f, l) * 0.65f;
            writeClamped(out, i, cr + (1.0f - cr) * m, cg + (0.9f - cg) * m, cb + (0.85f - cb) * m);
        }
    }

    private static void filterLavender(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = in[i], g = in[i + 1], b = in[i + 2];
            float l = luma(r, g, b);
            r = l + (r - l) * 0.57f;
            g = l + (g - l) * 0.57f;
            b = l + (b - l) * 0.57f;
            float t = 1f - smoothstep(0.05f, 0.82f, l);
            r = r * 0.9f + 0.065f * t;
            g = g * 0.84f + 0.035f * t;
            b = b * 1.08f + 0.11f * t;
            r = (float) Math.pow(Math.max(r, 0f), 0.96);
            g = (float) Math.pow(Math.max(g, 0f), 0.96);
            b = (float) Math.pow(Math.max(b, 0f), 0.96);
            writeClamped(out, i, r, g, b);
        }
    }

    private static void filterMidnight(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float[] c = linear(in[i], in[i + 1], in[i + 2]);
            float l = luma(c);
            c[0] = c[0] * 0.86f + 0.003f * (1f - l);
            c[1] = c[1] * 0.88f + 0.002f * (1f - l);
            c[2] = c[2] * 1.15f + 0.014f * (1f - l);
            c[0] = display(c[0] / (1f + c[0] * 0.22f));
            c[1] = display(c[1] / (1f + c[1] * 0.22f));
            c[2] = display(c[2] / (1f + c[2] * 0.22f));
            writeClamped(out, i, c[0], c[1], c[2]);
        }
    }

    private static void filterNightVision(float[] in, float[] out, int w, int h, LookSettings look, float time) {
        float gain = param(look, "night_vision", "Gain");
        float frame = (float) Math.floor(time * 12f);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float l = luma(clamp01(in[i]), clamp01(in[i + 1]), clamp01(in[i + 2]));
            float signal = (float) Math.pow(clamp01(l * gain), 0.65);
            float scan = 0.93f + 0.07f * (float) Math.cos((y + 0.5f) * Math.PI);
            float r = (0.005f + (0.45f - 0.005f) * signal) * scan;
            float g = (0.025f + (1.0f - 0.025f) * signal) * scan;
            float b = (0.008f + (0.12f - 0.008f) * signal) * scan;
            float n = hash(x + 0.5f + frame, y + 0.5f + frame) - 0.5f;
            writeClamped(out, i, r + n * 0.025f, g + n * 0.025f, b + n * 0.025f);
        }
    }

    private static void filterPrism(float[] in, float[] out, int w, int h, LookSettings look) {
        float amount = param(look, "prism", "Amount");
        float[] rgb = new float[3];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float u = (x + 0.5f) / w, v = (y + 0.5f) / h;
            float dx = u - 0.5f, dy = v - 0.5f;
            float k = (dx * dx + dy * dy) * amount * 0.025f;
            float r = sample(in, w, h, clamp(u + dx * k, 0.001f, 0.999f), clamp(v + dy * k, 0.001f, 0.999f), 0);
            float b = sample(in, w, h, clamp(u - dx * k, 0.001f, 0.999f), clamp(v - dy * k, 0.001f, 0.999f), 2);
            rgb[0] = r;
            rgb[1] = in[i + 1];
            rgb[2] = b;
            writeClamped(out, i, rgb[0], rgb[1], rgb[2]);
        }
    }

    private static void filterRisograph(float[] in, float[] out, int w, int h, LookSettings look) {
        float misregister = param(look, "risograph", "Misregister");
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float u = (x + 0.5f) / w, v = (y + 0.5f) / h;
            float r = clamp01(in[i]), g = clamp01(in[i + 1]), b = clamp01(in[i + 2]);
            float l = luma(r, g, b);
            float su = clamp(u + misregister / w, 0.5f / w, 1f - 0.5f / w);
            float sv = clamp(v + 0.6f * misregister / h, 0.5f / h, 1f - 0.5f / h);
            float shifted = luma(sample(in, w, h, su, sv, 0), sample(in, w, h, su, sv, 1), sample(in, w, h, su, sv, 2));
            float cool = 1f - smoothstep(0.12f, 0.83f, l);
            float warm = smoothstep(0.25f, 0.75f, shifted) * (0.4f + 0.6f * Math.max(r - b, 0f));
            float grain = hash((float) Math.floor(u * w * 0.65f), (float) Math.floor(v * h * 0.65f));
            float screen = 0.78f + 0.22f * step(0.45f, fract((x + 0.5f + y + 0.5f) / 3f));
            float cr = 1f * (1f + (0.16f - 1f) * cool * screen);
            float cg = 0.93f * (1f + (0.19f - 1f) * cool * screen);
            float cb = 0.78f * (1f + (0.48f - 1f) * cool * screen);
            cr *= 1f + (1f - 1f) * warm * 0.85f;
            cg *= 1f + (0.3f - 1f) * warm * 0.85f;
            cb *= 1f + (0.13f - 1f) * warm * 0.85f;
            writeClamped(out, i, cr + (grain - 0.5f) * 0.075f, cg + (grain - 0.5f) * 0.075f, cb + (grain - 0.5f) * 0.075f);
        }
    }

    private static void filterRoseglass(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = in[i], g = in[i + 1], b = in[i + 2];
            float l = luma(r, g, b);
            r = l + (r - l) * 0.74f;
            g = l + (g - l) * 0.74f;
            b = l + (b - l) * 0.74f;
            r = (float) Math.pow(Math.max(r, 0f), 0.93);
            g = (float) Math.pow(Math.max(g, 0f), 0.93);
            b = (float) Math.pow(Math.max(b, 0f), 0.93);
            float s = (float) Math.sin(clamp01(l) * Math.PI);
            r += 0.12f * s + 0.025f;
            g += -0.015f * s + 0.005f;
            b += 0.045f * s + 0.022f;
            writeClamped(out, i, r, g, b);
        }
    }

    private static void filterSelectiveRed(float[] in, float[] out, int w, int h, LookSettings look) {
        float tolerance = param(look, "selective_red", "Tolerance");
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = clamp01(in[i]), g = clamp01(in[i + 1]), b = clamp01(in[i + 2]);
            float l = luma(r, g, b);
            float keep = smoothstep(0.025f, Math.max(0.04f, tolerance), r - Math.max(g, b));
            float mono = (float) Math.pow(Math.max(l, 0f), 1.13);
            float ar = (l + (r - l) * 1.45f) * 1.13f;
            float ag = (l + (g - l) * 1.45f) * 0.96f;
            float ab = (l + (b - l) * 1.45f) * 0.91f;
            writeClamped(out, i, mono * 0.96f + (ar - mono * 0.96f) * keep,
                mono * 0.99f + (ag - mono * 0.99f) * keep, mono * 1.03f + (ab - mono * 1.03f) * keep);
        }
    }

    private static void filterSignal(float[] in, float[] out, int w, int h, float time) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float u = (x + 0.5f) / w, v = (y + 0.5f) / h;
            float px = u * 2f - 1f, py = v * 2f - 1f;
            float d = px * px + py * py;
            float uu = (px * (1f + d * 0.008f)) * 0.5f + 0.5f;
            float vv = (py * (1f + d * 0.008f)) * 0.5f + 0.5f;
            float off = 0.65f / w;
            float r = sample(in, w, h, clamp(uu + off, 0f, 1f), clamp(vv, 0f, 1f), 0);
            float g = sample(in, w, h, clamp(uu, 0f, 1f), clamp(vv, 0f, 1f), 1);
            float b = sample(in, w, h, clamp(uu - off, 0f, 1f), clamp(vv, 0f, 1f), 2);
            float[] c = linear(r, g, b);
            float line = 1f - 0.045f * (0.5f + 0.5f * (float) Math.cos((y + 0.5f) * Math.PI));
            splitTone(c, 0.89f, 1.04f, 1.07f, 1.02f, 1.02f, 0.98f, 0.30f);
            c[0] *= line * (1f - d * 0.035f);
            c[1] *= line * (1f - d * 0.035f);
            c[2] *= line * (1f - d * 0.035f);
            finish(c, 0.006f, x, y, time);
            write(out, i, c);
        }
    }

    private static void filterSunprint(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = in[i], g = in[i + 1], b = in[i + 2];
            float l = luma(r, g, b);
            r = l + (r - l) * 0.48f;
            g = l + (g - l) * 0.48f;
            b = l + (b - l) * 0.48f;
            r = (float) Math.pow(Math.max(r, 0f), 0.88);
            g = (float) Math.pow(Math.max(g, 0f), 0.88);
            b = (float) Math.pow(Math.max(b, 0f), 0.88);
            r = r * 0.98f + 0.07f;
            g = g * 0.81f + 0.055f;
            b = b * 0.55f + 0.035f;
            float n = hash(x + 0.5f, y + 0.5f) - 0.5f;
            writeClamped(out, i, r + n * 0.025f, g + n * 0.025f, b + n * 0.025f);
        }
    }

    private static void filterVelvet(float[] in, float[] out, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            float r = in[i], g = in[i + 1], b = in[i + 2];
            float l = luma(r, g, b);
            r = l + (r - l) * 0.6f;
            g = l + (g - l) * 0.6f;
            b = l + (b - l) * 0.6f;
            r = (float) Math.pow(Math.max(r, 0f), 1.18);
            g = (float) Math.pow(Math.max(g, 0f), 1.18);
            b = (float) Math.pow(Math.max(b, 0f), 1.18);
            float t = 1f - smoothstep(0.08f, 0.75f, l);
            r += 0.11f * t;
            g += -0.024f * t;
            b += 0.09f * t;
            r *= 1.06f;
            g *= 0.94f;
            b *= 1.04f;
            writeClamped(out, i, r, g, b);
        }
    }

    private static void filterArcade(float[] in, float[] out, int w, int h, LookSettings look) {
        float pixelSize = param(look, "arcade", "PixelSize");
        float block = Math.max(1f, pixelSize * Math.max(h / 540f, 0.5f));
        int[] ranks = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        float[][] palette = {{0.055f, 0.11f, 0.15f}, {0.18f, 0.32f, 0.31f}, {0.53f, 0.64f, 0.39f}, {0.92f, 0.9f, 0.63f}};
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = 3 * (y * w + x);
            int cellX = (int) Math.floor((x + 0.5f) / block);
            int cellY = (int) Math.floor((y + 0.5f) / block);
            float tone = luma(sample(in, w, h, (cellX + 0.5f) * block / w, (cellY + 0.5f) * block / h, 0),
                sample(in, w, h, (cellX + 0.5f) * block / w, (cellY + 0.5f) * block / h, 1),
                sample(in, w, h, (cellX + 0.5f) * block / w, (cellY + 0.5f) * block / h, 2));
            int rank = ranks[Math.floorMod(cellY, 4) * 4 + Math.floorMod(cellX, 4)];
            tone = clamp(tone + (rank / 16f - 0.5f) * 0.22f, 0f, 0.999f);
            float[] pick = palette[(int) Math.floor(tone * 4f)];
            writeClamped(out, i, pick[0], pick[1], pick[2]);
        }
    }

    // -- GLSL helpers --

    private static float param(LookSettings look, String id, String name) {
        for (var param : FilterCatalog.params(id)) {
            if (param.id().equals(name)) return look.parameter(id, param);
        }
        return 0f;
    }

    /** Bilinear sample of one channel, mirroring linear filtering (edges clamp). */
    private static float sample(float[] stage, int w, int h, float u, float v, int channel) {
        float fx = clamp(u, 0f, 1f) * w - 0.5f;
        float fy = clamp(v, 0f, 1f) * h - 0.5f;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float tx = fx - x0, ty = fy - y0;
        int xa = clamp(x0, 0, w - 1), xb = clamp(x0 + 1, 0, w - 1);
        int ya = clamp(y0, 0, h - 1), yb = clamp(y0 + 1, 0, h - 1);
        float a = stage[3 * (ya * w + xa) + channel];
        float b = stage[3 * (ya * w + xb) + channel];
        float c = stage[3 * (yb * w + xa) + channel];
        float d = stage[3 * (yb * w + xb) + channel];
        return a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static int toByte(float v) {
        return Math.round(clamp01(v) * 255f);
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float step(float edge, float x) {
        return x < edge ? 0f : 1f;
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    private static float hash(float x, float y) {
        float s = (float) Math.sin(x * 127.1f + y * 311.7f) * 43758.5453f;
        return s - (float) Math.floor(s);
    }

    private static float exp2(float x) {
        return (float) Math.pow(2.0, x);
    }

    private static float luma(float r, float g, float b) {
        return r * LUMA[0] + g * LUMA[1] + b * LUMA[2];
    }

    private static float luma(float[] c) {
        return c[0] * LUMA[0] + c[1] * LUMA[1] + c[2] * LUMA[2];
    }

    private static float dist(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float[] linear(float r, float g, float b) {
        return new float[] {(float) Math.pow(Math.max(r, 0f), 2.2), (float) Math.pow(Math.max(g, 0f), 2.2),
            (float) Math.pow(Math.max(b, 0f), 2.2)};
    }

    private static float display(float c) {
        return (float) Math.pow(clamp01(c), 1.0 / 2.2);
    }

    private static void splitTone(float[] c, float sr, float sg, float sb, float hr, float hg, float hb, float strength) {
        float l = luma(c);
        float t = smoothstep(0.05f, 0.75f, l);
        float br = sr + (hr - sr) * t;
        float bg = sg + (hg - sg) * t;
        float bb = sb + (hb - sb) * t;
        c[0] *= 1f + (br - 1f) * strength;
        c[1] *= 1f + (bg - 1f) * strength;
        c[2] *= 1f + (bb - 1f) * strength;
    }

    private static void finish(float[] c, float grain, int x, int y, float time) {
        float frame = (float) Math.floor(time * 24f);
        float n = hash(x + 0.5f + frame * 19.7f, y + 0.5f + frame * 7.3f) - 0.5f;
        c[0] = clamp01(display(c[0]) + n * grain);
        c[1] = clamp01(display(c[1]) + n * grain);
        c[2] = clamp01(display(c[2]) + n * grain);
    }

    private static void write(float[] out, int i, float[] c) {
        out[i] = c[0];
        out[i + 1] = c[1];
        out[i + 2] = c[2];
    }

    private static void writeClamped(float[] out, int i, float r, float g, float b) {
        out[i] = clamp01(r);
        out[i + 1] = clamp01(g);
        out[i + 2] = clamp01(b);
    }
}
