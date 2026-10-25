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
        private final net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatch;
        private final net.minecraft.client.gui.render.pip.GuiEntityRenderer renderer;
        Model(net.minecraft.world.entity.LivingEntity mob, String id) {
            this.mob = mob;
            animator = new MorphPreviewAnimator(mob, id);
            width = animator.framingWidth();
            height = animator.framingHeight();
            // Own dispatcher. The PiP renderer already owns its submit-node
            // storage per instance, so only the dispatcher was shared with the
            // world; a per-Model one (same dependency sources as
            // Minecraft.<init>, minus the client reference this version drops,
            // plus the paletted texture manager; fresh EquipmentAssetManager as
            // vanilla news up inline - the preview mob wears no armor so its
            // empty asset set is never consulted) keeps preview submits off the
            // world's dispatcher state. It owns no GL resources, so Model.close
            // only drops it.
            dispatch = new net.minecraft.client.renderer.entity.EntityRenderDispatcher(
                client.getTextureManager(),
                new net.minecraft.client.renderer.block.BlockModelResolver(client.getModelManager()),
                client.getItemModelResolver(), client.getMapRenderer(), client.getAtlasManager(),
                client.font, client.options, client.getModelManager().entityModels(),
                new net.minecraft.client.resources.model.EquipmentAssetManager(),
                client.playerSkinRenderCache(), client.getPalettedTextureManager());
            renderer = new net.minecraft.client.gui.render.pip.GuiEntityRenderer(dispatch);
        }
        @Override public java.util.List<String> stages() { return animator.stages(); }
        @Override public MorphThumbnail render(MorphPreviewPlayback.Frame frame) {
            animator.apply(frame);
            var projection = RenderSystem.getProjectionMatrixBuffer();
            var projectionType = RenderSystem.getProjectionType();
            var lights = RenderSystem.getShaderLights();
            var model = RenderSystem.getModelViewStack();
            var originalModel = new org.joml.Matrix4f(model);
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
                var input = new net.minecraft.client.renderer.state.gui.GuiRenderState();
                var output = new net.minecraft.client.renderer.state.gui.GuiRenderState();
                var graphics = new net.minecraft.client.gui.GuiGraphicsExtractor(client, input, -1, -1);
                int scale = MorphPreviewLayout.scale(width, height);
                net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, 0, 0, 128, 128, scale, 0, 38, 54, mob);
                input.forEachPictureInPicture(value -> {
                    if (value instanceof net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState entity) {
                        entity.renderState().ageInTicks = frame.ticks();
                        renderer.prepare(entity, output, client.gameRenderer.featureRenderDispatcher(), 1);
                    }
                });
                var view = ((MixinMorphPreviewTarget) (Object) renderer).fbactors$previewTexture();
                if (view == null) return null;
                long handle = com.moulberry.flashback.editor.ui.ReplayUI.imguiRenderer.getTextureId(view);
                if (handle == 0) return null;
                // Borrowed, not owned: the id aliases the renderer's internal PiP
                // view (registered with the ImGui renderer), which lives until
                // Model.close. That is safe because MorphPreviewCache is
                // access-ordered (the displayed model is always
                // most-recently-used, so it is never evicted mid-display) and
                // MorphPreviews.beginFrame drops the thumbnail together with the
                // cache. Never sample the handle after close.
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
            }
        }
        @Override public void close() { renderer.close(); }
    }
}
