package pdx.dev.fbactors.camera;

public final class CameraModeModel {
    private CameraModeModel() {}
    public static double aimHeight(double eye) { return Math.max(.35,Math.min(4,eye*.72)); }
    public static BoatCameraModel.Shot shot(double x,double y,double z,double yaw,double vx,double vz,double turn,
                                            double seconds,double eye,BoatCam.Settings s) {
        double speed=Math.hypot(vx,vz);
        double travel=speed>.01?Math.toDegrees(Math.atan2(-vx,vz)):yaw;
        double heading=s.mode()==6?travel:s.mode()==1?BoatCameraModel.angle(yaw,travel,speed>.01?.65:0):yaw;
        if(s.mode()==2) heading=seconds*s.orbit();
        double radians=Math.toRadians(heading),fx=-Math.sin(radians),fz=Math.cos(radians),rx=Math.cos(radians),rz=Math.sin(radians);
        double distance=s.distance(),cx=x-fx*distance,cy=y+s.height(),cz=z-fz*distance;
        double leadX=vx*s.ahead(),leadZ=vz*s.ahead(),lead=Math.hypot(leadX,leadZ),limit=distance*.65;
        if(lead>limit && lead>0) { leadX*=limit/lead; leadZ*=limit/lead; }
        double ax=x+leadX,ay=y+aimHeight(eye),az=z+leadZ;
        double roll=Math.max(-s.bank(),Math.min(s.bank(),-turn*s.bank()/8));
        float fov=(float)Math.max(30,Math.min(110,s.fov()+Math.min(12,speed*s.speedFov())));
        if(s.mode()==1) {
            double side=Math.copySign(Math.max(distance,Math.abs(s.side())),s.side()==0?1:s.side());
            cx=x+rx*side-fx*distance*.15; cz=z+rz*side-fz*distance*.15;
            ax=x+leadX*.4; az=z+leadZ*.4; roll*=.35;
        } else if(s.mode()==2) { ax=x; az=z; roll=0; }
        else if(s.mode()==3) {
            cx=x+fx*.4; cy=y+eye; cz=z+fz*.4;
            ax=cx+fx*8; ay=cy; az=cz+fz*8; roll=0; fov=(float)s.fov();
        } else if(s.mode()==4) {
            cx=x+fx*distance; cz=z+fz*distance; ax=x; az=z; roll*=.25;
        } else if(s.mode()==5) {
            return new BoatCameraModel.Shot(x,y+Math.max(2,distance+s.height()),z,0,90,0,(float)s.fov());
        } else if(s.mode()>=8) {

            double t=Math.max(0,Math.min(1,seconds/8)),progress=t*t*(3-2*t);
            ax=x; az=z; roll=0; fov=(float)s.fov();
            if(s.mode()==8) {
                cx+=rx*s.side(); cz+=rz*s.side(); ay=y+eye*.94;
            } else if(s.mode()==10) {
                double d=distance*(.75+.55*progress);
                cx=x-fx*d; cz=z-fz*d; cy=y+.5+(s.height()-.5)*progress;
            } else if(s.mode()==11 || s.mode()==12) {
                double factor=s.mode()==11?1.4-.85*progress:.55+1.05*progress;
                cx=x-fx*distance*factor; cz=z-fz*distance*factor;
                if(s.mode()==12) cy+=progress*distance*.35;
            } else if(s.mode()==13) {
                double factor=.7+.65*progress;
                cx=x-fx*distance*factor; cz=z-fz*distance*factor;
                cy=ay+(y+s.height()-ay)*factor;
                fov=(float)Math.max(30,Math.min(110,Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(s.fov())*.5)/factor))));
            }
        }
        return lookAt(cx,cy,cz,ax,ay,az,roll,fov);
    }
    public static BoatCameraModel.Shot lookAt(double x,double y,double z,double ax,double ay,double az,double roll,float fov) {
        double dx=ax-x,dy=ay-y,dz=az-z;
        return new BoatCameraModel.Shot(x,y,z,Math.toDegrees(Math.atan2(-dx,dz)),-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))),roll,fov);
    }
}
