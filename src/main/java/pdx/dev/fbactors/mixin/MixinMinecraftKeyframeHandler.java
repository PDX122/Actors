package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import pdx.dev.fbactors.actor.ActorMode;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftKeyframeHandler.class, remap = false)
public class MixinMinecraftKeyframeHandler {

    @Inject(method = "applyCameraPosition", at = @At("HEAD"), cancellable = true)
    public void fbactors$releaseCameraDuringTake(Vector3d position, double yaw, double pitch,
                                                 double roll, CallbackInfo ci) {
        if (ActorMode.suppressesCamera() || pdx.dev.fbactors.camera.BoatCam.ownsCamera()) {
            ci.cancel();
        }
    }
    @Inject(method = "applyFov", at = @At("HEAD"), cancellable = true)
    public void fbactors$boatFieldOfView(float fov, CallbackInfo ci) {
        if (pdx.dev.fbactors.camera.BoatCam.ownsCamera()) ci.cancel();
    }

}
