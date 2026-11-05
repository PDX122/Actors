package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.model.geom.ModelPart.class)
public abstract class CameraPreviewRowing {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="translateAndRotate")
    private void fbactors$rowing(com.mojang.blaze3d.vertex.PoseStack pose,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if(!pdx.dev.fbactors.camera.CameraPreviewCapture.rowingActive()) { original.call(pose);return; }
        pdx.dev.fbactors.camera.CameraPreviewCapture.arm((net.minecraft.client.model.geom.ModelPart)(Object)this,()->original.call(pose));
    }
}
