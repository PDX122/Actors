package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.entity.LivingEntityRenderer.class)
public abstract class CameraPreviewEntityCapture {
    @org.spongepowered.asm.mixin.injection.Inject(method={"render","submit"},at=@org.spongepowered.asm.mixin.injection.At("HEAD"),require=0)
    private void fbactors$reference(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci,
        @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) com.mojang.blaze3d.vertex.PoseStack pose) {
        pdx.dev.fbactors.camera.CameraPreviewCapture.reference(pose);
    }
}
