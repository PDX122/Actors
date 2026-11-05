package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Pseudo
@org.spongepowered.asm.mixin.Mixin(targets="traben.entity_model_features.models.animation.math.EMFMath",remap=false)
public abstract class CameraPreviewEmfClock {
    @org.spongepowered.asm.mixin.injection.Inject(method="getFrameTime",at=@org.spongepowered.asm.mixin.injection.At("HEAD"),cancellable=true,require=0,remap=false)
    private static void fbactors$previewTime(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> result) {
        float seconds=pdx.dev.fbactors.camera.CameraPreviewCapture.animationFrameTime();
        if(Float.isFinite(seconds)) result.setReturnValue(seconds);
    }
}
