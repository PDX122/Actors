package pdx.dev.fbactors.filters;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

class DemoPreviewGpuTest {
    @Test void bakesTheRealPhotoThroughEveryLookWithoutChangingItsSource() throws Exception {
        assumeTrue("1".equals(System.getenv("FBACTORS_GL_TESTS")));
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(256, 144, "Demo preview bake", 0, 0);
        assertNotEquals(0, window);
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        int texture = GL11.glGenTextures();
        try (FilterGpuPipeline pipeline = new FilterGpuPipeline();
             var stream = getClass().getResourceAsStream("/assets/fbactors/filters/demo.jpg")) {
            assertNotNull(stream);
            BufferedImage original = ImageIO.read(stream);
            BufferedImage photo = new BufferedImage(256, 144, BufferedImage.TYPE_INT_RGB);
            var graphics = photo.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(original, 0, 0, 256, 144, null);
            graphics.dispose();
            var pixels = BufferUtils.createFloatBuffer(256 * 144 * 4);
            for (int y = 143; y >= 0; y--) for (int x = 0; x < 256; x++) {
                int rgb = photo.getRGB(x, y);
                pixels.put((rgb >> 16 & 255) / 255f).put((rgb >> 8 & 255) / 255f).put((rgb & 255) / 255f).put(1);
            }
            pixels.flip();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 256, 144, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            pipeline.previews(texture, 256, 144, 12.5f);
            int width = 256 * FilterGpuPipeline.PREVIEW_COLUMNS;
            int rows = (FilterCatalog.entries().size() + FilterGpuPipeline.PREVIEW_COLUMNS - 1) / FilterGpuPipeline.PREVIEW_COLUMNS;
            int height = 144 * rows;
            var atlas = BufferUtils.createFloatBuffer(width * height * 4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, pipeline.previewTexture());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, atlas);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int sourceY = y / 144 * 144 + 143 - y % 144;
                int i = (sourceY * width + x) * 4;
                int rgb = 0;
                for (int c = 0; c < 3; c++) {
                    assertTrue(Float.isFinite(atlas.get(i + c)));
                    rgb = rgb << 8 | Math.round(Math.clamp(atlas.get(i + c), 0, 1) * 255);
                }
                image.setRGB(x, y, rgb);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            var unchanged = BufferUtils.createFloatBuffer(pixels.limit());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, unchanged);
            for (int i = 0; i < pixels.limit(); i++) assertEquals(pixels.get(i), unchanged.get(i), 0.004f);
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            Path root = Path.of("").toAbsolutePath();
            while (!Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
            ImageIO.write(image, "png", root.resolve("build/demo-atlas.png").toFile());
        } finally {
            GL11.glDeleteTextures(texture);
            GL.setCapabilities(null);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
