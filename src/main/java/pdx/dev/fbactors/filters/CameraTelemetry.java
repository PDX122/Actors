package pdx.dev.fbactors.filters;

import java.util.List;
import pdx.dev.fbactors.camera.BoatCameraModel;
import pdx.dev.fbactors.camera.BoatSubjects;

public final class CameraTelemetry {
    public static final List<String> UNIFORMS=List.of("LiveSpeed","LiveAltitude","LiveClimb","LiveHeading","LivePitch","LiveRoll","LiveX","LiveZ",
        "LiveRange","LiveFov","LiveAzimuth","LiveSensorAltitude","LiveTracking");
    public record Frame(float speed,float altitude,float climb,float heading,float pitch,float roll,float x,float z) {}
    private static volatile Frame current=new Frame(0,0,0,0,0,0,0,0);
    private static volatile BoatCameraModel.Shot view;
    private static volatile boolean tracking;
    private static volatile float range;
    private CameraTelemetry() {}
    public static boolean supports(String id) {
        return "fpv_analog".equals(id) || "action_camera".equals(id) || "fpv_digital".equals(id) || "uav_feed".equals(id);
    }
    public static Frame sample(BoatSubjects.Sample subject,BoatCameraModel.Shot camera) {
        return new Frame((float)(Math.hypot(subject.vx(),subject.vz())*20),(float)subject.y(),(float)(subject.vy()*20),
            (float)(((subject.yaw()+180)%360+360)%360),(float)(camera==null?subject.pitch():camera.pitch()),
            (float)(camera==null?0:camera.roll()),(float)subject.x(),(float)subject.z());
    }
    public static void update(BoatSubjects.Sample subject,BoatCameraModel.Shot camera) {
        current=subject==null?new Frame(0,0,0,0,0,0,0,0):sample(subject,camera);
        view=camera;
        tracking=subject!=null && camera!=null;
        range=tracking?(float)Math.hypot(Math.hypot(camera.x()-subject.x(),camera.z()-subject.z()),
            camera.y()-subject.y()-pdx.dev.fbactors.camera.CameraModeModel.aimHeight(BoatSubjects.eyeHeight(subject))):0;
    }
    public static float value(String name) {
        var frame=current;
        var camera=view;
        return switch(name) {
            case "LiveSpeed" -> frame.speed(); case "LiveAltitude" -> frame.altitude();
            case "LiveClimb" -> frame.climb(); case "LiveHeading" -> frame.heading();
            case "LivePitch" -> frame.pitch(); case "LiveRoll" -> frame.roll();
            case "LiveX" -> frame.x(); case "LiveZ" -> frame.z();
            case "LiveRange" -> range; case "LiveTracking" -> tracking?1:0;
            case "LiveFov" -> camera==null?70:camera.fov();
            case "LiveSensorAltitude" -> camera==null?frame.altitude():(float)camera.y();
            case "LiveAzimuth" -> camera==null?frame.heading():(float)(((camera.yaw()+180)%360+360)%360);
            default -> 0;
        };
    }
}
