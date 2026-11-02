package pdx.dev.fbactors.morph;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.mixin.MixinMorphPreviewTarget;

public final class MorphPreviewBridge {
    private MorphPreviewBridge() {}
    public static boolean supportsNative() { return true; }
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
        private boolean closed;
        private MorphThumbnail image;
        private com.mojang.renderpearl.api.commands.GpuFence retirementFence;
        @Override public void rotation(float degrees) { rotation = degrees; }
        @Override public void pose(String pose) { animator.pose(pose); }
        private final Minecraft client = Minecraft.getInstance();
        private final net.minecraft.world.entity.LivingEntity mob;
        private final MorphPreviewAnimator animator;
        private final float width, height;
        private final int resolution;
        private final net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatch;
        private final net.minecraft.client.gui.render.pip.GuiEntityRenderer renderer;
        private final net.minecraft.client.renderer.RenderBuffers previewBuffers;
        private final net.minecraft.client.renderer.feature.FeatureRenderDispatcher previewFeatures;
        Model(net.minecraft.world.entity.LivingEntity mob, String id) {
            this.mob = mob;
            resolution = MorphPreviewLayout.resolution(id);
            animator = new MorphPreviewAnimator(mob, id);
            var custom=CustomMobs.asset(id);
            width = custom==null?animator.framingWidth():custom.model().width();
            height = custom==null?animator.framingHeight():custom.model().height();

            dispatch = new net.minecraft.client.renderer.entity.EntityRenderDispatcher(
                client.getTextureManager(),
                new net.minecraft.client.renderer.block.BlockModelResolver(client.getModelManager()),
                client.getItemModelResolver(), client.getMapRenderer(), client.getAtlasManager(),
                client.font, client.options, client.getModelManager().entityModels(),
                new net.minecraft.client.resources.model.EquipmentAssetManager(),
                client.playerSkinRenderCache(), client.getPalettedTextureManager());
            dispatch.onResourceManagerReload(client.getResourceManager());
            renderer = new net.minecraft.client.gui.render.pip.GuiEntityRenderer(dispatch);
            previewBuffers = new net.minecraft.client.renderer.RenderBuffers(0);
            previewFeatures = new net.minecraft.client.renderer.feature.FeatureRenderDispatcher(
                previewBuffers, client.getModelManager(), client.getAtlasManager(), client.font,
                new net.minecraft.client.renderer.state.GameRenderState());
        }
        @Override public java.util.List<String> stages() { var names=CustomMobs.animations(CustomMobs.entity(mob));return names.isEmpty()?animator.stages():names; }
        @Override public MorphThumbnail render(MorphPreviewPlayback.Frame frame) {
            if (closed) return null;
            MorphPreviews.renderQueue().schedule(this, () -> {
                if (!closed) pdx.dev.fbactors.debug.ErrorRecovery.run("Native morph preview", () ->
                    MorphPreviews.renderIsolated(() -> {
                        image = renderNow(frame);
                        return image;
                    }));
            });
            return image;
        }
        private MorphThumbnail renderNow(MorphPreviewPlayback.Frame frame) {
            animator.apply(frame);
            CustomMobs.preview(mob,frame.stage(),frame.localTicks());
            var projection = RenderSystem.getProjectionMatrixBuffer();
            var projectionType = RenderSystem.getProjectionType();
            var lights = RenderSystem.getShaderLights();
            var model = RenderSystem.getModelViewStack();
            var originalModel = new org.joml.Matrix4f(model);

            try {
                var input = new net.minecraft.client.renderer.state.gui.GuiRenderState();
                var output = new net.minecraft.client.renderer.state.gui.GuiRenderState();
                var graphics = new net.minecraft.client.gui.GuiGraphicsExtractor(client, input, -1, -1);
                int scale = MorphPreviewLayout.scale(width, height, resolution);
                net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, 0, 0, resolution, resolution, scale, 0, resolution/2f-26, resolution/2f-10, mob);
                input.forEachPictureInPicture(value -> {
                    if (value instanceof net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState entity) {
                        entity.renderState().ageInTicks = frame.ticks();
                        entity.renderState().nameTag = null;
                        var draw = rotation == 0 ? entity : new net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState(
                            entity.renderState(), entity.translation(), new org.joml.Quaternionf(entity.rotation()).rotateY((float)Math.toRadians(rotation)),
                            entity.overrideCameraAngle(), entity.x0(), entity.y0(), entity.x1(), entity.y1(), entity.scale(), entity.scissorArea());
                        renderer.prepare(draw, output, previewFeatures, 1);
                    }
                });
                var view = ((MixinMorphPreviewTarget) (Object) renderer).fbactors$previewTexture();
                if (view == null) return null;
                long handle = com.moulberry.flashback.editor.ui.ReplayUI.imguiRenderer.getTextureId(view);
                if (handle == 0) return null;

                return MorphThumbnail.view(handle);
            }
            finally {
                previewBuffers.endFrame();
                RenderSystem.setProjectionMatrix(projection, projectionType);
                RenderSystem.setShaderLights(lights);
                model.set(originalModel);
            }
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            MorphPreviews.renderQueue().retire(this, this::releaseAfterDraw);
        }
        private boolean releaseAfterDraw() {
            if (retirementFence == null) retirementFence = RenderSystem.getDevice().createCommandEncoder().createFence();
            if (!retirementFence.awaitCompletion(0)) return false;
            try {
                CustomMobs.release(mob);
                try { renderer.close(); }
                finally {
                    try { previewFeatures.close(); }
                    finally { previewBuffers.close(); }
                }
            } finally {
                retirementFence.close();
            }
            return true;
        }
    }
}
