package pdx.dev.fbactors.morph;

import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.mixin.MixinMorphPreviewTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

public final class MorphPreviewBridge {
    private MorphPreviewBridge() {}
    public static boolean supportsNative() { return false; }
    public static MorphThumbnail create(String id) {
        var model = open(id);
        if (model == null) return null;
        try {
            var image = model.render(new MorphPreviewPlayback.Frame(0, 0, 0, 0));
            if (image == null) { model.close(); return null; }

            return new MorphThumbnail(image.texture(), model);
        } catch (RuntimeException error) { model.close(); throw error; }
    }
    public static MorphPreviewModel open(String id) {
        var mob = Compat.createMorphPreview(id);
        return mob == null ? null : new Model(mob, id);
    }
    public static MorphPreviewModel openSkin(com.mojang.authlib.GameProfile profile) {
        var player = Compat.createSkinPreview(profile);
        return player == null ? null : new Model(player, "skin_preview");
    }
    private static final class Model implements MorphPreviewModel {
        private float rotation;
        private java.util.function.Consumer<pdx.dev.fbactors.camera.CameraPreviewCapture.Mesh> cameraOutput;
        @Override public void capture(java.util.function.Consumer<pdx.dev.fbactors.camera.CameraPreviewCapture.Mesh> output) { cameraOutput=output; }
        @Override public void rotation(float degrees) { rotation = degrees; }
        @Override public void pose(String pose) { animator.pose(pose); }
        @Override public void cameraAction(String action) { animator.cameraAction(action); }
        @Override public net.minecraft.world.entity.LivingEntity cameraEntity() { return mob; }
        @Override public void cameraMount(net.minecraft.world.entity.Entity mount) { animator.cameraMount(mount); }
        private final Minecraft client = Minecraft.getInstance();
        private final net.minecraft.world.entity.LivingEntity mob;
        private final MorphPreviewAnimator animator;
        private final float width, height;
        private final int resolution;
        private final com.mojang.blaze3d.pipeline.RenderTarget target;
        private final com.mojang.blaze3d.vertex.ByteBufferBuilder previewBuilder;
        private final net.minecraft.client.renderer.MultiBufferSource.BufferSource previewBuffers;
        Model(net.minecraft.world.entity.LivingEntity mob, String id) {
            this.mob = mob;
            resolution = MorphPreviewLayout.resolution(id);
            animator = new MorphPreviewAnimator(mob, id);
            var custom=CustomMobs.asset(id);
            width = custom==null?animator.framingWidth():custom.model().width();
            height = custom==null?animator.framingHeight():custom.model().height();
            target = new com.mojang.blaze3d.pipeline.TextureTarget(resolution, resolution, true);

            previewBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(262144);
            previewBuffers = net.minecraft.client.renderer.MultiBufferSource.immediate(previewBuilder);
        }
        @Override public java.util.List<String> stages() { var names=CustomMobs.animations(CustomMobs.entity(mob));return names.isEmpty()?animator.stages():names; }
        @Override public MorphThumbnail render(MorphPreviewPlayback.Frame frame) {
            return pdx.dev.fbactors.camera.CameraPreviewCapture.render(cameraOutput, frame.ticks(), () -> renderNow(frame));
        }
        private MorphThumbnail renderNow(MorphPreviewPlayback.Frame frame) {
            animator.apply(frame);
            CustomMobs.preview(mob,frame.stage(),frame.localTicks());
            var original = client.getMainRenderTarget();
            var model = RenderSystem.getModelViewStack();

            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int[] scissor = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            boolean scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            RenderSystem.backupProjectionMatrix();
            model.pushMatrix();

            var dispatch = client.getEntityRenderDispatcher();
            var orientation = dispatch.cameraOrientation();
            var savedOrientation = orientation == null ? null : new org.joml.Quaternionf(orientation);
            try {
                ((MixinMorphPreviewTarget) client).fbactors$previewTarget(target);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                target.setClearColor(0, 0, 0, 0);
                target.clear();
                target.bindWrite(true);
                RenderSystem.viewport(0, 0, resolution, resolution);
                RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0, resolution, resolution, 0, 1000, 21000), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
                model.identity().translate(0, 0, -11000);
                var graphics = new net.minecraft.client.gui.GuiGraphics(client, previewBuffers);
                int scale = MorphPreviewLayout.scale(width, height, resolution);
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(graphics, resolution/2f, resolution*116f/128, scale,
                    new org.joml.Vector3f(), new org.joml.Quaternionf().rotateZ((float) Math.PI).rotateY(-0.4f + (float)Math.toRadians(rotation)), null, mob);
                graphics.flush();
                long handle = target.getColorTextureId();
                if (handle == 0) return null;

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
            CustomMobs.release(mob);
            target.destroyBuffers();
            try { previewBuilder.close(); } catch (Exception ignored) {}
        }
    }
}
