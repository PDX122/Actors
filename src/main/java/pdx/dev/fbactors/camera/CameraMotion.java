package pdx.dev.fbactors.camera;

import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorVehicleFrame;

public record CameraMotion(double x,double y,double z,double yaw,double pitch,double vx,double vy,double vz,double turn) {
    public static double liveFraction(double replayTick,int capturedTick) {
        // Flashback renders the interval ending at the captured server tick.
        return Math.max(0,Math.min(1,1+replayTick-capturedTick));
    }
    public static CameraMotion sample(ActorFrame prev,ActorFrame a,ActorFrame b,ActorFrame next,double fraction,double speed) {
        double t=Math.max(0,Math.min(1,fraction));
        if (!near(a,b)) {
            var frame=t<1?a:b;
            return new CameraMotion(frame.x(),frame.y(),frame.z(),frame.yRot(),frame.xRot(),0,0,0,0);
        }
        if (!near(prev,a)) prev=a;
        if (!near(b,next)) next=b;
        double yaw0=BoatCameraModel.angle(a.yRot(),prev.yRot(),1),yaw1=BoatCameraModel.angle(a.yRot(),b.yRot(),1);
        double yaw2=BoatCameraModel.angle(yaw1,next.yRot(),1);
        return new CameraMotion(curve(prev.x(),a.x(),b.x(),next.x(),t),curve(prev.y(),a.y(),b.y(),next.y(),t),curve(prev.z(),a.z(),b.z(),next.z(),t),
            curve(yaw0,a.yRot(),yaw1,yaw2,t),curve(prev.xRot(),a.xRot(),b.xRot(),next.xRot(),t),
            derivative(prev.x(),a.x(),b.x(),next.x(),t)*speed,derivative(prev.y(),a.y(),b.y(),next.y(),t)*speed,
            derivative(prev.z(),a.z(),b.z(),next.z(),t)*speed,derivative(yaw0,a.yRot(),yaw1,yaw2,t)*speed);
    }
    public static CameraMotion sample(ActorVehicleFrame prev,ActorVehicleFrame a,ActorVehicleFrame b,ActorVehicleFrame next,double fraction,double speed) {
        if (prev==null || !prev.uuid().equals(a.uuid()) || prev.tick()+1!=a.tick()) prev=a;
        if (next==null || !next.uuid().equals(b.uuid()) || b.tick()+1!=next.tick()) next=b;
        return sample(frame(prev),frame(a),frame(b),frame(next),fraction,speed);
    }
    private static ActorFrame frame(ActorVehicleFrame f) {
        return new ActorFrame(f.x(),f.y(),f.z(),f.yaw(),f.pitch(),f.yaw(),(byte)0,(byte)0);
    }
    private static boolean near(ActorFrame a,ActorFrame b) {
        return Math.hypot(Math.hypot(a.x()-b.x(),a.z()-b.z()),a.y()-b.y())<=64;
    }
    private static double tangent(double before,double at,double after) {
        double left=at-before,right=after-at;
        if(left*right<=0) return 0;
        return Math.copySign(Math.min(Math.abs((left+right)*.5),3*Math.min(Math.abs(left),Math.abs(right))),left);
    }
    static double curve(double prev,double a,double b,double next,double t) {
        double m0=tangent(prev,a,b),m1=tangent(a,b,next);
        return (2*t*t*t-3*t*t+1)*a+(t*t*t-2*t*t+t)*m0+(-2*t*t*t+3*t*t)*b+(t*t*t-t*t)*m1;
    }
    static double derivative(double prev,double a,double b,double next,double t) {
        double m0=tangent(prev,a,b),m1=tangent(a,b,next);
        return (6*t*t-6*t)*a+(3*t*t-4*t+1)*m0+(-6*t*t+6*t)*b+(3*t*t-2*t)*m1;
    }
}
