package pdx.dev.fbactors.filters;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class LookCpuBakeTest {
    private static int abgr(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int[] channel(int[] pixels, int index, int shift) {
        int[] out = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) out[i] = (pixels[i] >> shift) & 0xFF;
        return out;
    }

    private static LookSettings gradeOnly(float exposure) {
        LookSettings look = new LookSettings();
        look.enabled = true;
        look.intensity = 1;
        look.exposure = exposure;
        return look;
    }

    @Test void gradeBakesExactPixels() {
        // 2x2, exposure +2 (x4): every channel either clamps to 1 or stays 0,
        // so the float pipeline is absorbed by the clamp and the ints are exact.
        int[] src = {
            abgr(255, 128, 0, 255), abgr(0, 0, 0, 255),
            abgr(255, 255, 255, 0), abgr(0, 0, 255, 128),
        };
        int[] out = LookCpuBake.bakePixels(src, 2, 2, gradeOnly(2), 0f);
        assertNotNull(out);
        assertArrayEquals(new int[] {255, 0, 255, 0}, channel(out, 0, 0));
        assertArrayEquals(new int[] {255, 0, 255, 0}, channel(out, 0, 8));
        assertArrayEquals(new int[] {0, 0, 255, 255}, channel(out, 0, 16));
        assertArrayEquals(new int[] {255, 255, 0, 128}, channel(out, 0, 24));
    }

    @Test void neutralGradeKeepsPixelsWithinOneLsbAndAlphaExact() {
        int[] src = {abgr(10, 200, 77, 255), abgr(128, 64, 32, 1), abgr(1, 2, 3, 254), abgr(17, 34, 51, 0)};
        int[] out = LookCpuBake.bakePixels(src, 2, 2, gradeOnly(0), 0f);
        assertNotNull(out);
        for (int i = 0; i < src.length; i++) for (int shift : new int[] {0, 8, 16}) {
            int want = (src[i] >> shift) & 0xFF, got = (out[i] >> shift) & 0xFF;
            assertTrue(Math.abs(want - got) <= 1, "pixel " + i + " channel " + shift);
        }
        assertArrayEquals(new int[] {255, 1, 254, 0}, channel(out, 0, 24));
    }

    @Test void unknownAndNeighbourFiltersFallBack() {
        // tessera needs the screen-space mosaic pass; anamorphic/aurora/ember and
        // friends need neighbour sampling - none is reproducible per-pixel.
        List<String> unsupported = List.of("tessera", "anamorphic", "aurora", "blueprint",
            "cel_shade", "contour_light", "crosshatch", "diffusion", "ember", "halation",
            "inkwash", "luma_relief", "neon_wire", "opal", "pearl_bloom", "silver_screen", "solarize");
        for (String id : unsupported) assertFalse(LookCpuBake.supports(id), id);
        assertFalse(LookCpuBake.supports("missing"));
        assertFalse(LookCpuBake.supports(null));
        int[] src = {abgr(255, 128, 0, 255)};
        for (String id : unsupported) {
            LookSettings look = gradeOnly(0);
            look.filters.add(id);
            assertNull(LookCpuBake.bakePixels(src, 1, 1, look, 0f), id);
        }
        LookSettings unknown = gradeOnly(0);
        unknown.filters.add("missing");
        assertNull(LookCpuBake.bakePixels(src, 1, 1, unknown, 0f));
    }

    @Test void disabledLooksFallBack() {
        int[] src = {abgr(255, 128, 0, 255)};
        assertNull(LookCpuBake.bakePixels(src, 1, 1, new LookSettings(), 0f));
        LookSettings zero = gradeOnly(0);
        zero.intensity = 0;
        assertNull(LookCpuBake.bakePixels(src, 1, 1, zero, 0f));
        assertNull(LookCpuBake.bakePixels(src, 1, 1, gradeOnly(0), Float.NaN));
        assertNull(LookCpuBake.bakePixels(null, 1, 1, gradeOnly(0), 0f));
        assertNull(LookCpuBake.bakePixels(new int[3], 2, 2, gradeOnly(0), 0f));
        assertNull(LookCpuBake.bakePixels(new int[4097 * 2], 4097, 2, gradeOnly(0), 0f));
    }

    @Test void everySupportedFilterBakesWithoutThrowing() {
        LookSettings look = gradeOnly(0);
        for (var entry : FilterCatalog.entries()) {
            if (LookCpuBake.supports(entry.id())) look.filters.add(entry.id());
        }
        assertFalse(look.filters.isEmpty());
        int[] src = new int[16];
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
            src[y * 4 + x] = abgr(x * 64, y * 64, (x + y) * 32, 255);
        }
        int[] out = LookCpuBake.bakePixels(src, 4, 4, look, 0f);
        assertNotNull(out);
        assertArrayEquals(new int[] {255, 255, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255}, channel(out, 0, 24));
    }

    @Test void catalogIsFullyClassified() {
        for (var entry : FilterCatalog.entries()) {
            LookSettings look = gradeOnly(0);
            look.filters.add(entry.id());
            if (LookCpuBake.supports(entry.id())) {
                assertNotNull(LookCpuBake.bakePixels(new int[] {abgr(200, 100, 50, 255)}, 1, 1, look, 0f), entry.id());
            } else {
                assertNull(LookCpuBake.bakePixels(new int[] {abgr(200, 100, 50, 255)}, 1, 1, look, 0f), entry.id());
            }
        }
        assertEquals(44, FilterCatalog.entries().size());
    }

    @Test void carbonIsMonochromeAndMonotonic() {
        LookSettings look = gradeOnly(0);
        assertTrue(look.add("carbon"));
        int[] src = {abgr(0, 0, 0, 255), abgr(128, 100, 60, 255), abgr(255, 255, 255, 255)};
        int[] out = LookCpuBake.bakePixels(src, 3, 1, look, 0f);
        assertNotNull(out);
        for (int pixel : out) {
            int r = pixel & 0xFF, g = (pixel >> 8) & 0xFF, b = (pixel >> 16) & 0xFF;
            assertEquals(r, g);
            assertEquals(g, b);
        }
        int dark = out[0] & 0xFF, mid = out[1] & 0xFF, bright = out[2] & 0xFF;
        assertTrue(dark <= 2, "dark=" + dark);
        assertTrue(bright >= 253, "bright=" + bright);
        assertTrue(dark < mid && mid < bright);
    }
}
