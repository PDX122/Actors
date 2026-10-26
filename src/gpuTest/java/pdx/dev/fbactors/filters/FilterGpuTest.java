package pdx.dev.fbactors.filters;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

class FilterGpuTest {
    @Test void compilesAndRendersEveryShaderAndRestoresState() throws Exception {
        assumeTrue("1".equals(System.getenv("FBACTORS_GL_TESTS")));
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(256, 144, "Filter GPU verification", 0, 0);
        assertNotEquals(0, window);
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        int texture = GL11.glGenTextures();
        int framebuffer = GL30.glGenFramebuffers();
        int w = 256, h = 144;
        FloatBuffer pixels = BufferUtils.createFloatBuffer(w * h * 4);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float r = x / (float) w, g = y / (float) h, b = 0.22f + 0.4f * (1 - r);
            if (x > 70 && x < 185 && y > 40 && y < 110) {
                float checker = ((x / 9 + y / 9) % 2) == 0 ? 0.22f : 0.85f;
                r = g = b = checker;
            }
            if (y < 20) r = g = b = x / (float) (w - 1);
            pixels.put(r).put(g).put(b).put(0.75f);
        }
        pixels.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, w, h, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
        GL11.glViewport(2, 3, 100, 80);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(3, 4, 8, 12);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColorMask(true, false, true, false);
        BufferedImage montage = new BufferedImage(w * 4, (h + 24) * ((FilterCatalog.entries().size() + 4) / 4), BufferedImage.TYPE_INT_RGB);
        int panel = 0;
        try (FilterGpuPipeline pipeline = new FilterGpuPipeline()) {
            pipeline.compileAll();
            for (var entry : FilterCatalog.entries()) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                pixels.rewind();
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
                LookSettings look = new LookSettings();
                look.add(entry.id());
                pipeline.render(texture, w, h, look, 12.5f);
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), entry.id());
                assertEquals(framebuffer, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
                assertEquals(0, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
                assertTrue(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
                assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));
                int[] viewport = new int[4];
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                assertArrayEquals(new int[] {2, 3, 100, 80}, viewport);
                FloatBuffer output = BufferUtils.createFloatBuffer(w * h * 4);
                GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, output);
                double total = 0, difference = 0;
                for (int i = 0; i < w * h; i++) {
                    for (int channel = 0; channel < 3; channel++) {
                        float value = output.get(i * 4 + channel);
                        assertTrue(Float.isFinite(value), entry.id());
                        total += value;
                        difference += Math.abs(value - pixels.get(i * 4 + channel));
                    }
                    assertEquals(0.75f, output.get(i * 4 + 3), 0.002f, entry.id());
                }
                assertTrue(total > 1, entry.id());
                boolean expanded = java.util.Set.of("cel_shade", "halftone", "crosshatch", "risograph", "night_vision", "infrared", "duotone", "solarize", "arcade", "selective_red", "neon_wire", "anamorphic").contains(entry.id());
                if (expanded) assertTrue(difference / (w * h * 3) > 0.015, entry.id() + " must visibly change the source at its default settings");
                {
                    BufferedImage image = image(output, w, h);
                    var graphics = montage.createGraphics();
                    graphics.drawImage(image, (panel % 4) * w, (panel / 4) * (h + 24), null);
                    graphics.setColor(java.awt.Color.WHITE);
                    graphics.drawString(entry.name(), (panel % 4) * w + 8, (panel / 4) * (h + 24) + h + 16);
                    graphics.dispose();
                    panel++;
                }
                if (expanded) {
                    for (var param : FilterCatalog.params(entry.id())) for (float bound : new float[] {param.min(), param.max()}) {
                        LookSettings tuned = look.copy();
                        tuned.parameter(entry.id(), param, bound);
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                        pixels.rewind();
                        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
                        pipeline.render(texture, w, h, tuned, 12.5f);
                        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, output);
                        for (int i = 0; i < w * h; i++) {
                            for (int c = 0; c < 3; c++) assertTrue(Float.isFinite(output.get(i * 4 + c)), entry.id() + " " + param.id());
                            assertEquals(0.75f, output.get(i * 4 + 3), 0.002f, entry.id());
                        }
                        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), entry.id());
                    }
                    look.intensity = 0;
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                    pixels.rewind();
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
                    pipeline.render(texture, w, h, look, 12.5f);
                    GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, output);
                    for (int i = 0; i < pixels.limit(); i++) assertEquals(pixels.get(i), output.get(i), 0.00001f, entry.id());
                }
            }
            int material = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, material);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, w, h, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            pixels.rewind();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            LookSettings actorLook = new LookSettings();
            actorLook.add("carbon");
            pipeline.material(texture, material, w, h, actorLook, 0);
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            assertEquals(framebuffer, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            FloatBuffer isolated = BufferUtils.createFloatBuffer(w * h * 4);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, isolated);
            for (int i = 0; i < pixels.limit(); i++) assertEquals(pixels.get(i), isolated.get(i), 0.00001f);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, material);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, isolated);
            for (int i = 0; i < w * h; i++) {
                assertEquals(isolated.get(i * 4), isolated.get(i * 4 + 1), 0.002f);
                assertEquals(isolated.get(i * 4 + 1), isolated.get(i * 4 + 2), 0.002f);
                assertEquals(0.75f, isolated.get(i * 4 + 3), 0.002f);
            }
            assertArrayEquals(new int[] {w, h}, FilterGpuPipeline.textureSize(material));
            GL11.glDeleteTextures(material);
            LookSettings off = new LookSettings();
            off.enabled = true;
            off.intensity = 0;
            pixels.rewind();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            pipeline.render(texture, w, h, off, 0);
            FloatBuffer output = BufferUtils.createFloatBuffer(w * h * 4);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, output);
            for (int i = 0; i < pixels.limit(); i++) assertEquals(pixels.get(i), output.get(i), 0.00001f);
            pipeline.previews(texture, w, h, 12.5f);
            assertNotEquals(0, pipeline.previewTexture());
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            assertEquals(framebuffer, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));
            assertTrue(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
            FloatBuffer unchanged = BufferUtils.createFloatBuffer(w * h * 4);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, unchanged);
            for (int i = 0; i < pixels.limit(); i++) assertEquals(pixels.get(i), unchanged.get(i), 0.00001f);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, pipeline.previewTexture());
            FloatBuffer atlas = BufferUtils.createFloatBuffer(FilterGpuPipeline.PREVIEW_WIDTH * 4 * pipeline.previewHeight() * ((FilterCatalog.entries().size() + 3) / 4) * 4);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, atlas);
            for (int cell = 0; cell < FilterCatalog.entries().size(); cell++) {
                double sum = 0;
                for (int y = 0; y < pipeline.previewHeight(); y++) for (int x = 0; x < FilterGpuPipeline.PREVIEW_WIDTH; x++) {
                    int i = (((cell / 4) * pipeline.previewHeight() + y) * (FilterGpuPipeline.PREVIEW_WIDTH * 4) + (cell % 4) * FilterGpuPipeline.PREVIEW_WIDTH + x) * 4;
                    for (int c = 0; c < 3; c++) { assertTrue(Float.isFinite(atlas.get(i + c))); sum += atlas.get(i + c); }
                    assertEquals(0.75f, atlas.get(i + 3), 0.002f);
                }
                assertTrue(sum > 1, FilterCatalog.entries().get(cell).id());
            }
            pipeline.previews(texture, w, h, 12.5f, true);
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            assertEquals(framebuffer, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, unchanged);
            for (int i = 0; i < pixels.limit(); i++) assertEquals(pixels.get(i), unchanged.get(i), 0.00001f);
            String preview = System.getenv("FBACTORS_GL_PREVIEW");
            var graphics = montage.createGraphics();
            graphics.drawImage(image(pixels, w, h), (panel % 4) * w, (panel / 4) * (h + 24), null);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.drawString("Original", (panel % 4) * w + 8, (panel / 4) * (h + 24) + h + 16);
            graphics.dispose();
            if (preview != null) javax.imageio.ImageIO.write(montage, "png", Path.of(preview).toFile());
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(framebuffer);
            GL11.glDeleteTextures(texture);
            GL.setCapabilities(null);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static BufferedImage image(FloatBuffer pixels, int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int index = ((h - y - 1) * w + x) * 4;
            int r = Math.round(Math.max(0, Math.min(1, pixels.get(index))) * 255);
            int g = Math.round(Math.max(0, Math.min(1, pixels.get(index + 1))) * 255);
            int b = Math.round(Math.max(0, Math.min(1, pixels.get(index + 2))) * 255);
            image.setRGB(x, y, r << 16 | g << 8 | b);
        }
        return image;
    }
}
