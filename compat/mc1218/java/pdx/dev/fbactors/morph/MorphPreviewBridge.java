package pdx.dev.fbactors.morph;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.mixin.MixinMorphPreviewTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class MorphPreviewBridge {
    private MorphPreviewBridge() {}
    public static MorphThumbnail create(String id) {
        var model = open(id);
        if (model == null) return null;
        try {
            var image = model.render(new MorphPreviewPlayback.Frame(0, 0, 0, 0));
            if (image == null) { model.close(); return null; }
            // One-shot: transfer model ownership to the caller via the thumbnail.
            return new MorphThumbnail(image.texture(), model);
        } catch (RuntimeException error) { model.close(); throw error; }
    }
    public static MorphPreviewModel open(String id) {
        var mob = Compat.createMorphPreview(id);
        return mob == null ? null : new Model(mob, id);
    }
    private static final class Model implements MorphPreviewModel {
        private final Minecraft client = Minecraft.getInstance();
        private final net.minecraft.world.entity.LivingEntity mob;
        private final MorphPreviewAnimator animator;
        private final float width, height;
        private final com.mojang.blaze3d.vertex.ByteBufferBuilder previewBuilder;
        private final net.minecraft.client.renderer.MultiBufferSource.BufferSource previewBuffers;
        private final net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatch;
        private final net.minecraft.client.gui.render.pip.GuiEntityRenderer renderer;
        Model(net.minecraft.world.entity.LivingEntity mob, String id) {
            this.mob = mob;
            animator = new MorphPreviewAnimator(mob, id);
            width = animator.framingWidth();
            height = animator.framingHeight();
            // Own vertex sink and own dispatcher. GuiEntityRenderer.renderToTexture
            // renders the mob straight into the constructor bufferSource
            // (dispatcher.render(state, ..., this.bufferSource, ...) then
            // prepare's trailing endBatch flushes that same buffer into the PiP
            // target): with the world's bufferSource that flush would also sweep
            // up any world vertices queued at overlay time (consuming them, so
            // the world loses those draws for the frame - the player/actor
            // flicker), and the shared dispatcher's camera orientation would be
            // overwritten without restore. Per-Model instances keep both out of
            // the world's pipeline. Deps mirror vanilla's own construction in
            // Minecraft.<init> (same field sources, fresh EquipmentAssetManager
            // as vanilla news up inline; the preview mob wears no armor so its
            // empty asset set is never consulted).
            previewBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(262144);
            previewBuffers = net.minecraft.client.renderer.MultiBufferSource.immediate(previewBuilder);
            dispatch = new net.minecraft.client.renderer.entity.EntityRenderDispatcher(
                client, client.getTextureManager(), client.getItemModelResolver(), client.getItemRenderer(),
                client.getMapRenderer(), client.getBlockRenderer(), client.font, client.options,
                client.getModelManager().entityModels(),
                new net.minecraft.client.resources.model.EquipmentAssetManager());
            renderer = new net.minecraft.client.gui.render.pip.GuiEntityRenderer(previewBuffers, dispatch);
        }
        @Override public java.util.List<String> stages() { return animator.stages(); }
        @Override public MorphThumbnail render(MorphPreviewPlayback.Frame frame) {
            animator.apply(frame);
            var projection = RenderSystem.getProjectionMatrixBuffer();
            var projectionType = RenderSystem.getProjectionType();
            var lights = RenderSystem.getShaderLights();
            var model = RenderSystem.getModelViewStack();
            var originalModel = new org.joml.Matrix4f(model);
            var colorOverride = RenderSystem.outputColorTextureOverride;
            var depthOverride = RenderSystem.outputDepthTextureOverride;
            // GuiEntityRenderer.prepare renders into its own PiP framebuffer
            // through raw GL. Snapshot viewport/scissor/framebuffer bindings so a
            // throw or an early return cannot leak them into world mob draws.
            // (Blend/depth/sampler state is covered by MorphPreviews.guarded.)
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int[] scissor = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            boolean scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            try {
                var input = new net.minecraft.client.gui.render.state.GuiRenderState();
                var output = new net.minecraft.client.gui.render.state.GuiRenderState();
                var graphics = new net.minecraft.client.gui.GuiGraphics(client, input);
                int scale = MorphPreviewLayout.scale(width, height);
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, 0, 0, 128, 128, scale, 0, 38, 54, mob);
                // Seed the private dispatcher's camera orientation from the
                // world's. The private state render path reads it unconditionally
                // and a fresh dispatcher would otherwise render with null; the
                // world's value is exactly what a world frame would see.
                var worldOrientation = client.getEntityRenderDispatcher().cameraOrientation();
                if (worldOrientation != null) dispatch.overrideCameraOrientation(new org.joml.Quaternionf(worldOrientation));
                input.forEachPictureInPicture(value -> {
                    if (value instanceof net.minecraft.client.gui.render.state.pip.GuiEntityRenderState entity) {
                        entity.renderState().ageInTicks = frame.ticks();
                        renderer.prepare(entity, output, 1);
                    }
                });
                var view = ((MixinMorphPreviewTarget) (Object) renderer).fbactors$previewTexture();
                if (view == null) return null;
                long handle = view.texture() instanceof com.mojang.blaze3d.opengl.GlTexture gl ? gl.glId() : 0;
                if (handle == 0) return null;
                // Borrowed, not owned: the glId aliases the renderer's internal
                // PiP framebuffer, which lives until Model.close. That is safe
                // because MorphPreviewCache is access-ordered (the displayed model
                // is always most-recently-used, so it is never evicted
                // mid-display) and MorphPreviews.beginFrame drops the thumbnail
                // together with the cache. Never sample the handle after close.
                return MorphThumbnail.view(handle);
            }
            finally {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                if (scissorTest) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                else GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
                RenderSystem.setProjectionMatrix(projection, projectionType);
                RenderSystem.setShaderLights(lights);
                model.set(originalModel);
                RenderSystem.outputColorTextureOverride = colorOverride;
                RenderSystem.outputDepthTextureOverride = depthOverride;
            }
        }
        @Override public void close() {
            renderer.close();
            try { previewBuilder.close(); } catch (Exception ignored) {}
        }
    }
}
