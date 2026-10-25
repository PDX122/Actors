package pdx.dev.fbactors.filters;

import java.io.IOException;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class LookPreviewAtlas implements AutoCloseable {
    public static final int TILE_HEIGHT = 144;
    private int texture;
    private long handle;

    public long texture() {
        if (texture != 0) return handle;
        if (!FilterGpuPipeline.isGlAvailable())
            throw new IllegalStateException("Look previews require the OpenGL graphics backend");
        FilterGpuPipeline.State state = new FilterGpuPipeline.State();
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int unpackBuffer = GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try (var stream = LookPreviewAtlas.class.getResourceAsStream("/assets/fbactors/filters/demo-atlas.png")) {
            if (stream == null) throw new IllegalStateException("Missing look preview atlas");
            byte[] bytes = stream.readAllBytes();
            var encoded = MemoryUtil.memAlloc(bytes.length);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                encoded.put(bytes).flip();
                var width = stack.mallocInt(1);
                var height = stack.mallocInt(1);
                var channels = stack.mallocInt(1);
                var pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
                if (pixels == null) throw new IllegalStateException("Could not decode look previews");
                try {
                    int rows = (FilterCatalog.entries().size() + FilterGpuPipeline.PREVIEW_COLUMNS - 1) / FilterGpuPipeline.PREVIEW_COLUMNS;
                    if (width.get(0) != FilterGpuPipeline.PREVIEW_WIDTH * FilterGpuPipeline.PREVIEW_COLUMNS
                        || height.get(0) != TILE_HEIGHT * rows) throw new IllegalStateException("Invalid look preview atlas dimensions");
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    texture = GL11.glGenTextures();
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
                    org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                    GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width.get(0), height.get(0), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                    handle = FilterRenderBridge.previewHandle(texture, width.get(0), height.get(0));
                } finally { STBImage.stbi_image_free(pixels); }
            } finally { MemoryUtil.memFree(encoded); }
            return handle;
        } catch (IOException error) { throw new IllegalStateException(error); }
        catch (RuntimeException error) { close(); throw error; }
        finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            state.restore();
        }
    }

    @Override public void close() {
        FilterRenderBridge.closePreview();
        if (texture != 0) GL11.glDeleteTextures(texture);
        texture = 0;
        handle = 0;
    }
}
