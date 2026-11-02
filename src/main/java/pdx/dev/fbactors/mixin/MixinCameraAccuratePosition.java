package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.camera.BoatCam;

@Mixin(value=AccurateEntityPositionHandler.class,remap=false)
public class MixinCameraAccuratePosition {
    @Inject(method="getAccuratePosition",at=@At("HEAD"),cancellable=true)
    private static void fbactors$cameraPosition(Entity entity,float partial,CallbackInfoReturnable<Vector3d> cir) {
        var shot=BoatCam.renderedShot();
        if(shot!=null && entity==Minecraft.getInstance().player) {
            cir.setReturnValue(new Vector3d(shot.x(),shot.y()-entity.getEyeHeight(),shot.z()));
            pdx.dev.fbactors.selftest.ReleaseExportChecks.accurateCameraUsed(shot);
        }
    }
    @Inject(method="getAccurateRotation",at=@At("HEAD"),cancellable=true)
    private static void fbactors$cameraRotation(Entity entity,float partial,CallbackInfoReturnable<Vector2f> cir) {
        var shot=BoatCam.renderedShot();
        if(shot!=null && entity==Minecraft.getInstance().player)
            cir.setReturnValue(new Vector2f((float)shot.pitch(),(float)shot.yaw()));
    }
}
