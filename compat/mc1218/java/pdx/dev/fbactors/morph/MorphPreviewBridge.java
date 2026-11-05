package pdx.dev.fbactors.morph;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.mixin.MixinMorphPreviewTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class MorphPreviewBridge {
    private static net.minecraft.client.renderer.entity.EntityRenderDispatcher sharedDispatcher;
    private static Object sharedModels;
    private static net.minecraft.client.renderer.entity.EntityRenderDispatcher previewDispatcher(Minecraft client) {
        Object models=client.getResourceManager().listPacks().toList();
        if(sharedDispatcher==null || !models.equals(sharedModels)) {
            var created = new net.minecraft.client.renderer.entity.EntityRenderDispatcher(
                client, client.getTextureManager(), client.getItemModelResolver(), client.getItemRenderer(),
                client.getMapRenderer(), client.getBlockRenderer(), client.font, client.options,
                client.getModelManager().entityModels(),
                pdx.dev.fbactors.camera.CameraPreviewAssets.equipment(client.getEntityRenderDispatcher(),net.minecraft.client.resources.model.EquipmentAssetManager.class));
            created.onResourceManagerReload(client.getResourceManager());
            sharedDispatcher=created;sharedModels=models;
        }
        return sharedDispatcher;
    }
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
        private final com.mojang.blaze3d.vertex.ByteBufferBuilder previewBuilder;
        private final net.minecraft.client.renderer.MultiBufferSource.BufferSource previewBuffers;
        private final net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatch;
        private final net.minecraft.client.gui.render.pip.GuiEntityRenderer renderer;
        Model(net.minecraft.world.entity.LivingEntity mob, String id) {
            this.mob = mob;
            resolution = MorphPreviewLayout.resolution(id);
            animator = new MorphPreviewAnimator(mob, id);
            var custom=CustomMobs.asset(id);
            width = custom==null?animator.framingWidth():custom.model().width();
            height = custom==null?animator.framingHeight():custom.model().height();

            previewBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(262144);
            previewBuffers = net.minecraft.client.renderer.MultiBufferSource.immediate(previewBuilder);
            dispatch = previewDispatcher(client);
            renderer = new net.minecraft.client.gui.render.pip.GuiEntityRenderer(previewBuffers, dispatch);
        }
        @Override public java.util.List<String> stages() { var names=CustomMobs.animations(CustomMobs.entity(mob));return names.isEmpty()?animator.stages():names; }
        @Override public MorphThumbnail render(MorphPreviewPlayback.Frame frame) {
            return pdx.dev.fbactors.camera.CameraPreviewCapture.render(cameraOutput, frame.ticks(), () -> renderNow(frame));
        }
        private MorphThumbnail renderNow(MorphPreviewPlayback.Frame frame) {
            animator.apply(frame);
            CustomMobs.preview(mob,frame.stage(),frame.localTicks());
            var projection = RenderSystem.getProjectionMatrixBuffer();
            var projectionType = RenderSystem.getProjectionType();
            var lights = RenderSystem.getShaderLights();
            var model = RenderSystem.getModelViewStack();
            var originalModel = new org.joml.Matrix4f(model);
            var colorOverride = RenderSystem.outputColorTextureOverride;
            var depthOverride = RenderSystem.outputDepthTextureOverride;

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
                int scale = MorphPreviewLayout.scale(width, height, resolution);
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, 0, 0, resolution, resolution, scale, 0, cameraOutput==null?resolution/2f-26:resolution/2f, cameraOutput==null?resolution/2f-10:resolution/2f+(float)Math.tan(mob.getXRot()/20f)*40, mob);

                var worldOrientation = client.getEntityRenderDispatcher().cameraOrientation();
                if (worldOrientation != null) dispatch.overrideCameraOrientation(new org.joml.Quaternionf(worldOrientation));
                input.forEachPictureInPicture(value -> {
                    if (value instanceof net.minecraft.client.gui.render.state.pip.GuiEntityRenderState entity) {
                        entity.renderState().ageInTicks = frame.ticks();
                        entity.renderState().nameTag = null;
                        var draw = rotation == 0 ? entity : new net.minecraft.client.gui.render.state.pip.GuiEntityRenderState(
                            entity.renderState(), entity.translation(), new org.joml.Quaternionf(entity.rotation()).rotateY((float)Math.toRadians(rotation)),
                            entity.overrideCameraAngle(), entity.x0(), entity.y0(), entity.x1(), entity.y1(), entity.scale(), entity.scissorArea());
                        renderer.prepare(draw, output, 1);
                    }
                });
                var view = ((MixinMorphPreviewTarget) (Object) renderer).fbactors$previewTexture();
                if (view == null) return null;
                long handle = view.texture() instanceof com.mojang.blaze3d.opengl.GlTexture gl ? gl.glId() : 0;
                if (handle == 0) return null;

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
            CustomMobs.release(mob);
            renderer.close();
            try { previewBuilder.close(); } catch (Exception ignored) {}
        }
    }
}
