package pdx.dev.fbactors.camera;

import net.minecraft.world.entity.Entity;
import pdx.dev.fbactors.camera.CameraModePreviewScene.Point;

public final class CameraPreviewRiding {
    private CameraPreviewRiding() {}
    public static Point offset(Entity rider,Entity mount) {
        var position=mount.getPassengerRidingPosition(rider).subtract(rider.getVehicleAttachmentPoint(mount)).subtract(mount.position());
        return local(position.x,position.y,position.z,mount.getYRot());
    }
    static Point local(double x,double y,double z,double yaw) {
        double angle=Math.toRadians(yaw),c=Math.cos(angle),s=Math.sin(angle);
        return new Point(x*c+z*s,y,-x*s+z*c);
    }
}
