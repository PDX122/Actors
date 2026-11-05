package pdx.dev.fbactors.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinBoatCamera {

    @Shadow protected abstract void setPosition(double x,double y,double z);
    @Shadow protected abstract void setRotation(float yaw,float pitch);

    @Group(name = "fbactors$boatCameraPose", min = 1)
    @Inject(method = {"setup", "alignWithEntity"}, at = @At("TAIL"), require = 0)
    private void fbactors$applyCameraPose(CallbackInfo ci) {
        if(!pdx.dev.fbactors.compat.Compat.isMainCamera(this))return;
        var shot=pdx.dev.fbactors.camera.BoatCam.renderedShot();
        if(shot!=null) {
            setPosition(shot.x(),shot.y(),shot.z());
            setRotation((float)shot.yaw(),(float)shot.pitch());
        }
    }

    @Group(name = "fbactors$boatCamera", min = 1)
    @Inject(method = {"setup", "update"}, at = @At("HEAD"), require = 0)
    private void fbactors$followBoat(CallbackInfo ci) {
        if(!pdx.dev.fbactors.compat.Compat.isMainCamera(this))return;
        pdx.dev.fbactors.camera.BoatCam.renderFrame();
    }
}
