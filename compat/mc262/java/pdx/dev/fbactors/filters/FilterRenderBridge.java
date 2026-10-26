package pdx.dev.fbactors.filters;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlTexture;

public final class FilterRenderBridge {
    private static com.mojang.blaze3d.textures.GpuTexture preview;
    private static com.mojang.blaze3d.textures.GpuTextureView view;
    private static long handle;
    private FilterRenderBridge() {}

    public static long previewHandle(int texture, int width, int height) {
        if (preview == null || preview.getWidth(0) != width || preview.getHeight(0) != height) {
            closePreview();
            var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
            preview = device.createTexture("Actors look previews",
                com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING | com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST | com.mojang.blaze3d.textures.GpuTexture.USAGE_RENDER_ATTACHMENT,
                com.mojang.blaze3d.GpuFormat.RGBA16_FLOAT, width, height, 1, 1);
            view = device.createTextureView(preview);
            handle = com.moulberry.flashback.editor.ui.ReplayUI.imguiRenderer.getTextureId(view);
            com.moulberry.flashback.editor.ui.ReplayUI.imguiRenderer.setSampleLinear(handle);
        }
        if (!(preview instanceof GlTexture target)) return 0;
        int read = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        int sourceBuffer = org.lwjgl.opengl.GL30.glGenFramebuffers();
        int targetBuffer = org.lwjgl.opengl.GL30.glGenFramebuffers();
        try {
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, sourceBuffer);
            org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texture, 0);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, targetBuffer);
            org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL11.GL_TEXTURE_2D, target.glId(), 0);
            org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
        } finally {
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, read);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, draw);
            org.lwjgl.opengl.GL30.glDeleteFramebuffers(sourceBuffer);
            org.lwjgl.opengl.GL30.glDeleteFramebuffers(targetBuffer);
            if (scissor) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        }
        return handle;
    }

    public static void closePreview() {
        if (view != null) view.close();
        if (preview != null) preview.close();
        view = null;
        preview = null;
        handle = 0;
    }

    public static RenderTarget target(Minecraft client) { return client.gameRenderer.mainRenderTarget(); }

    public static boolean reverseDepth() { return true; }

    public static int depthTexture(Minecraft client) { return target(client).getDepthTexture() instanceof GlTexture texture ? texture.glId() : -1; }

    public static int colorTexture(Minecraft client) {
        return target(client).getColorTexture() instanceof GlTexture texture ? texture.glId() : -1;
    }
}
