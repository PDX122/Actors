package pdx.dev.fbactors.filters;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlTexture;

public final class FilterRenderBridge {
    private static com.mojang.blaze3d.textures.GpuTexture preview;
    private static com.mojang.blaze3d.textures.GpuTexture customPreview;
    private static com.mojang.blaze3d.textures.GpuTextureView view;
    private static com.mojang.blaze3d.textures.GpuTextureView customView;
    private static long handle;
    private static long customHandle;
    private FilterRenderBridge() {}

    public static void synchronizeGlState() {
        boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        boolean scissor=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean depthMask=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        int depthFunc=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
        int srcRgb=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int dstRgb=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        int eqRgb=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_RGB);
        int eqAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_ALPHA);
        boolean logic=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_COLOR_LOGIC_OP);
        int logicOp=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_LOGIC_OP_MODE);
        int colorMask=0;
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            var mask=stack.malloc(4);
            org.lwjgl.opengl.GL11.glGetBooleanv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK,mask);
            for(int i=0;i<4;i++) if(mask.get(i)!=0) colorMask|=1<<i;
        }
        com.mojang.blaze3d.opengl.GlStateManager._enableDepthTest();
        com.mojang.blaze3d.opengl.GlStateManager._disableDepthTest();
        if(depth) com.mojang.blaze3d.opengl.GlStateManager._enableDepthTest();
        com.mojang.blaze3d.opengl.GlStateManager._enableCull();
        com.mojang.blaze3d.opengl.GlStateManager._disableCull();
        if(cull) com.mojang.blaze3d.opengl.GlStateManager._enableCull();
        com.mojang.blaze3d.opengl.GlStateManager._enableScissorTest();
        com.mojang.blaze3d.opengl.GlStateManager._disableScissorTest();
        if(scissor) com.mojang.blaze3d.opengl.GlStateManager._enableScissorTest();
        com.mojang.blaze3d.opengl.GlStateManager._enableBlend(0);
        com.mojang.blaze3d.opengl.GlStateManager._disableBlend(0);
        if(blend) com.mojang.blaze3d.opengl.GlStateManager._enableBlend(0);
        com.mojang.blaze3d.opengl.GlStateManager._depthMask(!depthMask);
        com.mojang.blaze3d.opengl.GlStateManager._depthMask(depthMask);
        com.mojang.blaze3d.opengl.GlStateManager._depthFunc(depthFunc==org.lwjgl.opengl.GL11.GL_ALWAYS?org.lwjgl.opengl.GL11.GL_LESS:org.lwjgl.opengl.GL11.GL_ALWAYS);
        com.mojang.blaze3d.opengl.GlStateManager._depthFunc(depthFunc);
        com.mojang.blaze3d.opengl.GlStateManager._blendFuncSeparate(1,0,1,0);
        com.mojang.blaze3d.opengl.GlStateManager._blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
        com.mojang.blaze3d.opengl.GlStateManager._blendEquationSeparate(org.lwjgl.opengl.GL14.GL_FUNC_SUBTRACT,org.lwjgl.opengl.GL14.GL_FUNC_SUBTRACT);
        com.mojang.blaze3d.opengl.GlStateManager._blendEquationSeparate(eqRgb,eqAlpha);
        com.mojang.blaze3d.opengl.GlStateManager._colorMask(0,colorMask^15);
        com.mojang.blaze3d.opengl.GlStateManager._colorMask(0,colorMask);
        com.mojang.blaze3d.opengl.GlStateManager._enableColorLogicOp();
        com.mojang.blaze3d.opengl.GlStateManager._disableColorLogicOp();
        if(logic) com.mojang.blaze3d.opengl.GlStateManager._enableColorLogicOp();
        com.mojang.blaze3d.opengl.GlStateManager._logicOp(logicOp==org.lwjgl.opengl.GL11.GL_COPY?org.lwjgl.opengl.GL11.GL_XOR:org.lwjgl.opengl.GL11.GL_COPY);
        com.mojang.blaze3d.opengl.GlStateManager._logicOp(logicOp);
        int active = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        int[] textures = new int[8];
        for (int unit=0; unit<textures.length; unit++) {
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0+unit);
            textures[unit]=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        }

        com.mojang.blaze3d.opengl.GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0+1);
        com.mojang.blaze3d.opengl.GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        for (int unit=0; unit<textures.length; unit++) {
            com.mojang.blaze3d.opengl.GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0+unit);
            com.mojang.blaze3d.opengl.GlStateManager._bindTexture(0);
            com.mojang.blaze3d.opengl.GlStateManager._bindTexture(textures[unit]);
        }
        com.mojang.blaze3d.opengl.GlStateManager._activeTexture(active);
        int read=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        com.mojang.blaze3d.opengl.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER,0);
        com.mojang.blaze3d.opengl.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,read);
        com.mojang.blaze3d.opengl.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER,draw);
    }
    public static void validateCustom(String source) { throw new IllegalStateException("Custom shaders require the OpenGL renderer on this Minecraft version."); }

    public static boolean renderNative(LookSettings look, float time) { return false; }
    public static long nativeCustomPreview(String id,float time) { return 0; }
    public static long nativePreviews() { return 0; }
    public static void closeNative() {}

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

    public static long customPreviewHandle(int texture, int width, int height) {
        if (customPreview == null || customPreview.getWidth(0) != width || customPreview.getHeight(0) != height) {
            closeCustomPreview();
            var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
            customPreview = device.createTexture("Actors look previews",
                com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING | com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST | com.mojang.blaze3d.textures.GpuTexture.USAGE_RENDER_ATTACHMENT,
                com.mojang.blaze3d.GpuFormat.RGBA16_FLOAT, width, height, 1, 1);
            customView = device.createTextureView(customPreview);
            customHandle = com.moulberry.flashback.editor.ui.ReplayUI.imguiRenderer.getTextureId(customView);
            com.moulberry.flashback.editor.ui.ReplayUI.imguiRenderer.setSampleLinear(customHandle);
        }
        if (!(customPreview instanceof GlTexture target)) return 0;
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
        return customHandle;
    }

    public static void closeCustomPreview() {
        if (customView != null) customView.close();
        if (customPreview != null) customPreview.close();
        customView = null;
        customPreview = null;
        customHandle = 0;
    }

    public static RenderTarget target(Minecraft client) { return client.gameRenderer.mainRenderTarget(); }

    public static boolean reverseDepth() { return true; }

    public static int depthTexture(Minecraft client) { return target(client).getDepthTexture() instanceof GlTexture texture ? texture.glId() : -1; }

    public static int colorTexture(Minecraft client) {
        return target(client).getColorTexture() instanceof GlTexture texture ? texture.glId() : -1;
    }
}
