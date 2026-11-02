package pdx.dev.fbactors.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class MixinBoatCamera {

    @Group(name = "fbactors$boatCamera", min = 1)
    @Inject(method = {"setup", "update"}, at = @At("HEAD"), require = 0)
    private void fbactors$followBoat(CallbackInfo ci) {
        pdx.dev.fbactors.camera.BoatCam.renderFrame();
    }
}
