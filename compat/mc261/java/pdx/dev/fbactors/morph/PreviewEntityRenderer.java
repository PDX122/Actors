package pdx.dev.fbactors.morph;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * A {@link GuiEntityRenderer} wired to render-pipeline objects owned by one
 * preview {@code Model} instead of the client's shared ones.
 *
 * <p>Vanilla's {@code renderToTexture} submits the mob into the <em>shared</em>
 * {@code FeatureRenderDispatcher} submit-node storage and then runs the shared
 * {@code renderAllFeatures}, which executes every node queued at overlay time
 * - including world nodes - into the PiP framebuffer, consuming them so the
 * world frame loses those draws (the player/actor flicker while the preview
 * animation re-renders). This override replicates vanilla's method body
 * line-for-line (lighting entry, translate/rotate, camera orientation from the
 * state's override angle, submit, execute) but routes the submit through this
 * Model's own storage, own feature dispatcher and own buffers. The inherited
 * {@code prepare} is untouched, so texture management, the
 * outputColorTextureOverride scoping and the trailing {@code endBatch} (which
 * flushes exactly these private buffers into the PiP target) behave exactly
 * as vanilla.
 *
 * <p>The private {@link FeatureRenderDispatcher} duplicates vanilla's own
 * construction (same dependency sources as {@code GameRenderer}, fresh
 * {@code EquipmentAssetManager} as vanilla news up inline; the preview mob
 * wears no armor so its empty asset set is never consulted). It owns no GL
 * resources beyond the Model's buffers; {@link #close} releases the vanilla
 * PiP textures, the feature dispatcher and the native builders.
 */
final class PreviewEntityRenderer extends GuiEntityRenderer {
    private final EntityRenderDispatcher dispatch;
    private final SubmitNodeStorage storage;
    private final FeatureRenderDispatcher features;
    private final com.mojang.blaze3d.vertex.ByteBufferBuilder mainBuilder;
    private final com.mojang.blaze3d.vertex.ByteBufferBuilder crumblingBuilder;

    static PreviewEntityRenderer create(Minecraft client) {
        var mainBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(262144);
        var mainBuffers = MultiBufferSource.immediate(mainBuilder);
        var crumblingBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096);
        var crumblingBuffers = MultiBufferSource.immediate(crumblingBuilder);
        var dispatch = new EntityRenderDispatcher(client, client.getTextureManager(),
            new net.minecraft.client.renderer.block.BlockModelResolver(client.getModelManager()),
            client.getItemModelResolver(), client.getMapRenderer(), client.getAtlasManager(),
            client.font, client.options, client.getModelManager().entityModels(),
            new net.minecraft.client.resources.model.EquipmentAssetManager(),
            client.playerSkinRenderCache());
        var storage = new SubmitNodeStorage();
        var features = new FeatureRenderDispatcher(storage, client.getModelManager(), mainBuffers,
            client.getAtlasManager(), new net.minecraft.client.renderer.OutlineBufferSource(),
            crumblingBuffers, client.font, new GameRenderState());
        return new PreviewEntityRenderer(mainBuffers, dispatch, storage, features, mainBuilder, crumblingBuilder);
    }

    private PreviewEntityRenderer(MultiBufferSource.BufferSource buffers, EntityRenderDispatcher dispatch,
            SubmitNodeStorage storage, FeatureRenderDispatcher features,
            com.mojang.blaze3d.vertex.ByteBufferBuilder mainBuilder,
            com.mojang.blaze3d.vertex.ByteBufferBuilder crumblingBuilder) {
        super(buffers, dispatch);
        this.dispatch = dispatch;
        this.storage = storage;
        this.features = features;
        this.mainBuilder = mainBuilder;
        this.crumblingBuilder = crumblingBuilder;
    }

    @Override
    protected void renderToTexture(GuiEntityRenderState state, PoseStack pose) {
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        var translation = state.translation();
        pose.translate(translation.x(), translation.y(), translation.z());
        pose.mulPose(state.rotation());
        var override = state.overrideCameraAngle();
        var camera = new CameraRenderState();
        if (override != null) {
            camera.orientation = override.conjugate(new org.joml.Quaternionf()).rotateY((float) Math.PI);
        }
        dispatch.submit(state.renderState(), camera, 0, 0, 0, pose, storage);
        features.renderAllFeatures();
    }

    /** Drops the nodes queued by the last prepare, mirroring the per-frame reset. */
    void endFrame() {
        storage.endFrame();
    }

    @Override
    public void close() {
        super.close();
        features.close();
        try { mainBuilder.close(); } catch (Exception ignored) {}
        try { crumblingBuilder.close(); } catch (Exception ignored) {}
    }
}
