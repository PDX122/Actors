package pdx.dev.fbactors.camera;

public final class UavCameraModel {
    public record State(BoatCameraModel.Shot platform,BoatCameraModel.Shot aim,BoatCameraModel.Shot view) {}
    private UavCameraModel() {}

    public static State step(State before,BoatCameraModel.Shot from,BoatCameraModel.Shot desired,
                             double ax,double ay,double az,double delta,double lag) {
        var platform=BoatCam.smooth(before==null?null:before.platform(),from,desired,delta,Math.max(.8,lag));
        var aim=CameraModeModel.lookAt(platform.x(),platform.y(),platform.z(),ax,ay,az,0,platform.fov());
        // Let a jump move inside the tracking gate before the gimbal catches up.
        var gimbal=BoatCam.smooth(before==null?null:before.view(),before==null?null:before.aim(),aim,delta,.45);
        var view=new BoatCameraModel.Shot(platform.x(),platform.y(),platform.z(),gimbal.yaw(),gimbal.pitch(),0,platform.fov());
        return new State(platform,aim,view);
    }
}
