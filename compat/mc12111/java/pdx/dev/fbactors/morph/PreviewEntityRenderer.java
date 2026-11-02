package pdx.dev.fbactors.morph;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;

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
            client.getItemModelResolver(), client.getMapRenderer(), client.getBlockRenderer(),
            client.getAtlasManager(), client.font, client.options,
            client.getModelManager().entityModels(),
            new net.minecraft.client.resources.model.EquipmentAssetManager(),
            client.playerSkinRenderCache());
        dispatch.onResourceManagerReload(client.getResourceManager());
        var storage = new SubmitNodeStorage();
        var features = new FeatureRenderDispatcher(storage, client.getBlockRenderer(), mainBuffers,
            client.getAtlasManager(), new net.minecraft.client.renderer.OutlineBufferSource(),
            crumblingBuffers, client.font);
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
        pose.translate(translation.x, translation.y, translation.z);
        pose.mulPose(state.rotation());
        var override = state.overrideCameraAngle();
        var camera = new CameraRenderState();
        if (override != null) {
            camera.orientation = override.conjugate(new org.joml.Quaternionf()).rotateY((float) Math.PI);
        }
        dispatch.submit(state.renderState(), camera, 0, 0, 0, pose, storage);
        features.renderAllFeatures();
    }

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
