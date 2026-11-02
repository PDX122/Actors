package pdx.dev.fbactors.filters;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlTexture;

public final class FilterRenderBridge {
    private FilterRenderBridge() {}
    public static void synchronizeGlState() {}
    public static void validateCustom(String source) { throw new IllegalStateException("Custom shaders require the OpenGL renderer on this Minecraft version."); }


    public static boolean renderNative(LookSettings look, float time) { return false; }
    public static long nativeCustomPreview(String id,float time) { return 0; }
    public static long nativePreviews() { return 0; }
    public static void closeNative() {}
    public static long customPreviewHandle(int texture,int width,int height) { return texture; }
    public static void closeCustomPreview() {}
    public static long previewHandle(int texture, int width, int height) { return texture; }
    public static void closePreview() {}

    public static RenderTarget target(Minecraft client) { return client.getMainRenderTarget(); }

    public static boolean reverseDepth() { return false; }

    public static int depthTexture(Minecraft client) { return target(client).getDepthTexture() instanceof GlTexture texture ? texture.glId() : -1; }

    public static int colorTexture(Minecraft client) {
        return target(client).getColorTexture() instanceof GlTexture texture ? texture.glId() : -1;
    }
}
