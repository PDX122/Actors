package pdx.dev.fbactors.camera;

public final class FpvCameraModel {
    private FpvCameraModel() {}
    public static BoatCameraModel.Shot shot(double x,double y,double z,double yaw,double pitch,double vx,double vz,double turn,
                                            double eye,BoatCam.Settings s) {
        double heading=Math.toRadians(yaw),tilt=Math.toRadians(Math.max(-90,Math.min(90,pitch)));
        double fx=-Math.sin(heading)*Math.cos(tilt),fy=-Math.sin(tilt),fz=Math.cos(heading)*Math.cos(tilt);
        double rx=Math.cos(heading),rz=Math.sin(heading);
        double ux=-Math.sin(heading)*Math.sin(tilt),uy=Math.cos(tilt),uz=Math.cos(heading)*Math.sin(tilt);
        double bank=-Math.toDegrees(Math.atan(Math.hypot(vx,vz)*20*Math.toRadians(turn*20)/9.81));
        bank=Math.toRadians(Math.max(-s.bank(),Math.min(s.bank(),bank)));
        double bx=ux*Math.cos(bank)+rx*Math.sin(bank),by=uy*Math.cos(bank),bz=uz*Math.cos(bank)+rz*Math.sin(bank);
        double mount=Math.toRadians(Math.max(0,Math.min(60,s.orbit())));
        double dx=fx*Math.cos(mount)+bx*Math.sin(mount),dy=fy*Math.cos(mount)+by*Math.sin(mount),dz=fz*Math.cos(mount)+bz*Math.sin(mount);
        double ax=bx*Math.cos(mount)-fx*Math.sin(mount),ay=by*Math.cos(mount)-fy*Math.sin(mount),az=bz*Math.cos(mount)-fz*Math.sin(mount);
        double viewYaw=Math.atan2(-dx,dz),viewPitch=-Math.atan2(dy,Math.hypot(dx,dz));
        double roll=Math.atan2(ax*Math.cos(viewYaw)+az*Math.sin(viewYaw),
            -ax*Math.sin(viewYaw)*Math.sin(viewPitch)+ay*Math.cos(viewPitch)+az*Math.cos(viewYaw)*Math.sin(viewPitch));
        return new BoatCameraModel.Shot(x+fx*.4,y+eye+fy*.4,z+fz*.4,
            Math.toDegrees(viewYaw),Math.toDegrees(viewPitch),Math.toDegrees(roll),(float)s.fov());
    }
}
