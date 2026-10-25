package pdx.dev.fbactors.filters;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlTexture;

public final class FilterRenderBridge {
    private FilterRenderBridge() {}
    public static long previewHandle(int texture, int width, int height) { return texture; }
    public static void closePreview() {}

    public static RenderTarget target(Minecraft client) { return client.getMainRenderTarget(); }

    public static boolean reverseDepth() { return false; }

    public static int depthTexture(Minecraft client) { return target(client).getDepthTexture() instanceof GlTexture texture ? texture.glId() : -1; }

    public static int colorTexture(Minecraft client) {
        return target(client).getColorTexture() instanceof GlTexture texture ? texture.glId() : -1;
    }
}
