package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.model.Model.class)
public abstract class MixinCameraPreviewCapture {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
    private void fbactors$capture(com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer vertices,int light,int overlay,int color,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        pdx.dev.fbactors.camera.CameraPreviewCapture.model(this);
        original.call(pose,pdx.dev.fbactors.camera.CameraPreviewCapture.wrap(pose,vertices),light,overlay,color);
    }
}
