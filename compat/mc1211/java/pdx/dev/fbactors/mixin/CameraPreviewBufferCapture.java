package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.MultiBufferSource.BufferSource.class)
public abstract class CameraPreviewBufferCapture {
    @org.spongepowered.asm.mixin.injection.Inject(method="getBuffer",at=@org.spongepowered.asm.mixin.injection.At("RETURN"),cancellable=true)
    private void fbactors$capturePackBuffers(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<com.mojang.blaze3d.vertex.VertexConsumer> result,
        @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.minecraft.client.renderer.RenderType type) {
        result.setReturnValue(pdx.dev.fbactors.camera.CameraPreviewCapture.buffer(result.getReturnValue(),type));
    }
}
