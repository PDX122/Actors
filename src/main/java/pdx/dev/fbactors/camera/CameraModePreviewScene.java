package pdx.dev.fbactors.camera;

public final class CameraModePreviewScene {
    public record Point(double x,double y,double z) {}
    public record Pixel(float x,float y,double depth) {}
    private CameraModePreviewScene() {}
    public static Point subject(double seconds) { return new Point(Math.sin(seconds*.55)*4,0,seconds*2); }
    public static boolean ice(int mode) { return mode>=14 && mode<=16; }
    public static boolean horse(int mode) { return mode>=17 && mode<=19; }
    public static Point subject(double seconds,int mode) {
        var p=subject(seconds);
        return ice(mode)?new Point(p.x()*2,0,p.z()*4):horse(mode)?new Point(p.x()*1.3,0,p.z()*1.8):p;
    }
    public static double heading(double seconds,int mode) {
        double scaleX=ice(mode)?2:horse(mode)?1.3:1,scaleZ=ice(mode)?4:horse(mode)?1.8:1;
        return Math.toDegrees(Math.atan2(-Math.cos(seconds*.55)*.11*scaleX,.1*scaleZ));
    }
    public static double eye(int mode) { return ice(mode)?1.15:horse(mode)?2.55:1.62; }
    public static Point partner(double seconds) { var p=subject(seconds); return new Point(p.x()+3+Math.sin(seconds*.65)*1.5,0,p.z()+2); }
    public static double heading(double seconds) { return Math.toDegrees(Math.atan2(-Math.cos(seconds*.55)*.11,.1)); }
    public static BoatCameraModel.Shot camera(BoatCam.Settings tuning,double seconds,double aspect) {
        var p=subject(seconds,tuning.mode());
        double vx=Math.cos(seconds*.55)*.11*(ice(tuning.mode())?2:horse(tuning.mode())?1.3:1),vz=.1*(ice(tuning.mode())?4:horse(tuning.mode())?1.8:1);
        double yaw=heading(seconds,tuning.mode());
        if(tuning.mode()==7) {
            var second=partner(seconds);
            return BoatCameraModel.battle(p.x(),CameraModeModel.aimHeight(1.62)-.6,p.z(),second.x(),CameraModeModel.aimHeight(1.62)-.6,second.z(),Math.toDegrees(Math.atan2(-vx,vz)),tuning.distance(),tuning.height(),tuning.fov(),aspect);
        }
        return CameraModeModel.shot(p.x(),0,p.z(),yaw,vx,vz,Math.sin(seconds*.55)*2,seconds,eye(tuning.mode()),tuning);
    }
    public static final class View {
        private final BoatCameraModel.Shot camera;
        private final double sy,cy,sp,cp,sr,cr,focal;
        private final float width,height;
        public View(BoatCameraModel.Shot camera,float width,float height) {
            this.camera=camera; this.width=width; this.height=height;
            double yaw=Math.toRadians(camera.yaw()),pitch=Math.toRadians(camera.pitch()),roll=Math.toRadians(camera.roll());
            sy=Math.sin(yaw); cy=Math.cos(yaw); sp=Math.sin(pitch); cp=Math.cos(pitch); sr=Math.sin(roll); cr=Math.cos(roll);
            focal=height*.5/Math.tan(Math.toRadians(camera.fov())*.5);
        }
        public double depth(Point p) { return -sy*cp*(p.x()-camera.x())-sp*(p.y()-camera.y())+cy*cp*(p.z()-camera.z()); }
        public boolean visible(java.util.List<CameraPreviewMesh.Vertex> vertices) {
            var a=vertices.get(0).point(); var b=vertices.get(1).point(); var c=vertices.get(2).point();
            double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z(),vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();

            return (uy*vz-uz*vy)*(camera.x()-a.x())+(uz*vx-ux*vz)*(camera.y()-a.y())+(ux*vy-uy*vx)*(camera.z()-a.z())<0;
        }
        public Pixel project(Point p) {
            if(!camera.valid() || width<=0 || height<=0) return null;
            double d=depth(p);
            if(!Double.isFinite(d) || d<.079) return null;
            double dx=p.x()-camera.x(),dy=p.y()-camera.y(),dz=p.z()-camera.z();
            double right=-cy*dx-sy*dz,up=-sy*sp*dx+cp*dy+cy*sp*dz;
            double x=right/d*focal,y=-up/d*focal;
            double px=width*.5+x*cr-y*sr,py=height*.5+x*sr+y*cr;
            return Double.isFinite(px)&&Double.isFinite(py)&&Math.abs(px)<1_000_000&&Math.abs(py)<1_000_000?new Pixel((float)px,(float)py,d):null;
        }
    }
    public static Pixel project(BoatCameraModel.Shot camera,Point p,float width,float height) { return new View(camera,width,height).project(p); }
}
