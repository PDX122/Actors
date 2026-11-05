package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.entity.LivingEntityRenderer.class)
public abstract class MixinCameraPreviewCapture {
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void fbactors$capture(net.minecraft.client.model.EntityModel<?> model,com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer vertices,int light,int overlay,int color,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        pdx.dev.fbactors.camera.CameraPreviewCapture.model(model);
        original.call(model,pose,pdx.dev.fbactors.camera.CameraPreviewCapture.wrap(pose,vertices),light,overlay,color);
    }
}
