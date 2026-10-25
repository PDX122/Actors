package pdx.dev.fbactors.filters;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;

public final class FilterRenderBridge {
    private FilterRenderBridge() {}
    public static long previewHandle(int texture, int width, int height) { return texture; }
    public static void closePreview() {}

    public static RenderTarget target(Minecraft client) { return client.getMainRenderTarget(); }

    public static boolean reverseDepth() { return false; }

    public static int depthTexture(Minecraft client) { return target(client).getDepthTextureId(); }

    public static int colorTexture(Minecraft client) {
        return target(client).getColorTextureId();
    }
}
