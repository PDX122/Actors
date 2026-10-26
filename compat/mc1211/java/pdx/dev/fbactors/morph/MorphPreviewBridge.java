package pdx.dev.fbactors.morph;

import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.mixin.MixinMorphPreviewTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

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
        private final com.mojang.blaze3d.pipeline.RenderTarget target;
        private final com.mojang.blaze3d.vertex.ByteBufferBuilder previewBuilder;
        private final net.minecraft.client.renderer.MultiBufferSource.BufferSource previewBuffers;
        Model(net.minecraft.world.entity.LivingEntity mob, String id) {
            this.mob = mob;
            animator = new MorphPreviewAnimator(mob, id);
            width = animator.framingWidth();
            height = animator.framingHeight();
            target = new com.mojang.blaze3d.pipeline.TextureTarget(128, 128, true, false);
            // Own vertex sink. renderEntityInInventory draws the mob through the
            // GuiGraphics bufferSource and graphics.flush() ends its batch: with
            // the world's bufferSource that flush would also sweep up any world
            // vertices queued at overlay time into this 128x128 target (and
            // consume them, so the world loses those draws for the frame - the
            // player/actor flicker). An immediate buffer per Model keeps preview
            // vertices out of the world's buffers entirely.
            previewBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(262144);
            previewBuffers = net.minecraft.client.renderer.MultiBufferSource.immediate(previewBuilder);
        }
        @Override public java.util.List<String> stages() { return animator.stages(); }
        @Override public MorphThumbnail render(MorphPreviewPlayback.Frame frame) {
            animator.apply(frame);
            var original = client.getMainRenderTarget();
            var model = RenderSystem.getModelViewStack();
            // bindWrite + viewport below dirty raw GL state the matrix backup does
            // not cover. Snapshot it so a throw or an early return cannot leave the
            // 128x128 viewport and preview framebuffer behind for world mob draws.
            // (Shader lights need no snapshot here: renderEntityInInventory wraps
            // itself in setupForEntityInInventory/setupFor3DItems, and this target
            // has no RenderSystem.getShaderLights to read them back with anyway.)
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int[] scissor = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            boolean scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            RenderSystem.backupProjectionMatrix();
            model.pushMatrix();
            // renderEntityInInventory hardcodes Minecraft.getEntityRenderDispatcher
            // (a dedicated dispatcher cannot be injected), and it overwrites the
            // shared dispatcher's camera orientation without restoring it
            // (EntityRenderDispatcher.overrideCameraOrientation has no matching
            // reset; world LevelRenderer.prepare rebuilds it every frame, but a
            // throw between override and the next world prepare would leave the
            // preview orientation behind). Snapshot and restore it here, plus the
            // shadow flag vanilla itself restores on the happy path.
            var dispatch = client.getEntityRenderDispatcher();
            var orientation = dispatch.cameraOrientation();
            var savedOrientation = orientation == null ? null : new org.joml.Quaternionf(orientation);
            try {
                ((MixinMorphPreviewTarget) client).fbactors$previewTarget(target);
                target.setClearColor(0, 0, 0, 0);
                target.clear(false);
                target.bindWrite(true);
                RenderSystem.viewport(0, 0, 128, 128);
                RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0, 128, 128, 0, 1000, 21000), com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
                model.identity();
                var graphics = new net.minecraft.client.gui.GuiGraphics(client, previewBuffers);
                int scale = MorphPreviewLayout.scale(width, height);
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(graphics, 64, 116, scale,
                    new org.joml.Vector3f(), new org.joml.Quaternionf().rotateZ((float) Math.PI).rotateY(-0.4f), null, mob);
                graphics.flush();
                long handle = target.getColorTextureId();
                if (handle == 0) return null;
                // Owned, not borrowed: the glId aliases this Model's own
                // TextureTarget, which lives until Model.close, so the thumbnail
                // can never dangle while the cache holds the model.
                return MorphThumbnail.view(handle);
            }
            finally {
                if (savedOrientation != null) dispatch.overrideCameraOrientation(savedOrientation);
                dispatch.setRenderShadow(true);
                ((MixinMorphPreviewTarget) client).fbactors$previewTarget(original);
                original.bindWrite(true);
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                if (scissorTest) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                else GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
                model.popMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
        }
        @Override public void close() {
            target.destroyBuffers();
            try { previewBuilder.close(); } catch (Exception ignored) {}
        }
    }
}
