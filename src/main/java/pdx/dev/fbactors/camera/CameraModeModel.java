package pdx.dev.fbactors.camera;

public final class CameraModeModel {
    private CameraModeModel() {}
    public static double aimHeight(double eye) { return Math.max(.35,Math.min(4,eye*.72)); }
    public static BoatCameraModel.Shot shot(double x,double y,double z,double yaw,double vx,double vz,double turn,
                                            double seconds,double eye,BoatCam.Settings s) {
        return shot(x,y,z,yaw,vx,0,vz,turn,seconds,eye,s);
    }
    public static BoatCameraModel.Shot shot(double x,double y,double z,double yaw,double vx,double vy,double vz,double turn,
                                            double seconds,double eye,BoatCam.Settings s) {
        double pitch=-Math.toDegrees(Math.atan2(vy,Math.hypot(vx,vz)));
        return shot(x,y,z,yaw,pitch,vx,vy,vz,turn,seconds,eye,s);
    }
    public static BoatCameraModel.Shot shot(double x,double y,double z,double yaw,double pitch,double vx,double vy,double vz,double turn,
                                            double seconds,double eye,BoatCam.Settings s) {
        if(CameraModesCatalog.uav(s.mode())) {
            double heading=Math.max(0,seconds)*(s.mode()==39 && s.orbit()==0?3:s.orbit());
            double angle=Math.toRadians(heading);
            double depth=s.mode()==39?.7+.3*Math.cos(angle):Math.cos(angle);
            return lookAt(x+Math.sin(angle)*s.distance(),y+s.height(),z-depth*s.distance(),x,y+aimHeight(eye),z,0,(float)s.fov());
        }
        if(CameraModesCatalog.fpv(s.mode())) return FpvCameraModel.shot(x,y,z,yaw,pitch,vx,vz,turn,eye,s);
        double speed=Math.hypot(vx,vz);
        double confidence=Math.min(1,speed/.15);
        confidence=confidence*confidence*(3-2*confidence);
        double travel=speed>0?BoatCameraModel.angle(yaw,Math.toDegrees(Math.atan2(-vx,vz)),confidence):yaw;
        double heading=CameraModesCatalog.usesTravel(s.mode())?travel:s.mode()==1?BoatCameraModel.angle(yaw,travel,speed>.01?.65:0):yaw;
        if(s.mode()==2) heading=seconds*s.orbit();
        double radians=Math.toRadians(heading),fx=-Math.sin(radians),fz=Math.cos(radians),rx=Math.cos(radians),rz=Math.sin(radians);
        double distance=s.distance(),cx=x-fx*distance,cy=y+s.height(),cz=z-fz*distance;
        double leadX=vx*s.ahead(),leadZ=vz*s.ahead(),lead=Math.hypot(leadX,leadZ),limit=distance*.65;
        if(lead>limit && lead>0) { leadX*=limit/lead; leadZ*=limit/lead; }
        double ax=x+leadX,ay=y+aimHeight(eye),az=z+leadZ;
        double roll=Math.max(-s.bank(),Math.min(s.bank(),-turn*s.bank()/8));
        float fov=(float)Math.max(30,Math.min(110,s.fov()+Math.min(12,speed*s.speedFov())));
        if(CameraModesCatalog.flight(s.mode())) {
            double flightSpeed=Math.hypot(speed,vy);
            double vertical=flightSpeed>.01?vy/flightSpeed:0,horizontal=flightSpeed>.01?speed/flightSpeed:1;
            double dx=fx*horizontal,dz=fz*horizontal,ux=-fx*vertical,uz=-fz*vertical;
            double lift=s.height(),side=s.side(),back=distance;
            if(s.mode()==31) { side+=distance; back=distance*.2; }
            if(s.mode()==32) back=-distance;
            if(s.mode()==35) {
                double phase=Math.toRadians(Math.max(0,seconds)*s.orbit());
                side+=Math.sin(phase)*distance*.65;
                lift+=Math.cos(phase)*distance*.4;
                roll+=Math.sin(phase)*s.bank();
            }
            cx=x-dx*back+ux*lift+rx*side;
            cy=y+aimHeight(eye)-vertical*back+horizontal*lift;
            cz=z-dz*back+uz*lift+rz*side;
            if(s.mode()==34) { cy=y+s.height(); cx=x-fx*distance; cz=z-fz*distance; }
            double ahead=Math.min(distance*.65,flightSpeed*s.ahead());
            ax=x+dx*ahead; ay=y+aimHeight(eye)+vertical*ahead; az=z+dz*ahead;
            if(s.mode()==32 || s.mode()==35) { ax=x; ay=y+aimHeight(eye); az=z; }
            roll=Math.max(-s.bank(),Math.min(s.bank(),roll));
            fov=(float)Math.max(30,Math.min(110,s.fov()+Math.min(12,flightSpeed*s.speedFov())));
        } else if(s.mode()==1) {
            double side=s.side();
            cx=x+rx*side-fx*distance*.15; cz=z+rz*side-fz*distance*.15;
            ax=x+leadX*.4; az=z+leadZ*.4; roll*=.35;
        } else if(s.mode()==2) { ax=x; az=z; roll=0; }
        else if(CameraModesCatalog.pov(s.mode())) {
            cx=x+fx*.4; cy=y+eye; cz=z+fz*.4;
            double tilt=Math.toRadians(pitch);
            ax=cx+fx*Math.cos(tilt)*8; ay=cy-Math.sin(tilt)*8; az=cz+fz*Math.cos(tilt)*8; roll=0; fov=(float)s.fov();
        } else if(s.mode()==4) {
            cx=x+fx*distance; cz=z+fz*distance; ax=x; az=z; roll*=.25;
        } else if(s.mode()==5) {
            return new BoatCameraModel.Shot(x,y+Math.max(2,distance+s.height()),z,0,90,0,(float)s.fov());
        } else if((s.mode()>=8 && s.mode()<=13) || s.mode()>=21) {

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
            } else if(s.mode()==21 || s.mode()==23) {
                double angle=Math.toRadians(s.mode()==21?yaw-65+170*progress:seconds*s.orbit());
                cx=x+Math.sin(angle)*distance; cz=z-Math.cos(angle)*distance;
                cy=y+s.height()+(s.mode()==23?progress*distance*.35:0);
            } else if(s.mode()>=24 && s.mode()<=27) {
                double phase=Math.toRadians(Math.max(0,seconds)*s.orbit());
                if(s.mode()==26) {
                    cx=x+Math.sin(phase)*distance;
                    cz=z-Math.sin(phase*2)*distance*.6;
                    cy=y+s.height()+distance*.2*(1-Math.cos(phase*2));
                    roll=Math.sin(phase)*s.bank();
                } else {
                    double angle=Math.toRadians(yaw)+(s.mode()==27?Math.sin(phase)*1.3:phase);
                    cx=x+Math.sin(angle)*distance; cz=z-Math.cos(angle)*distance;
                    if(s.mode()==24) roll=Math.toDegrees(phase)%360;
                    else if(s.mode()==25) {
                        cy=y+s.height()+Math.sin(phase)*Math.min(s.height()*.7,distance*.4);
                        roll=Math.sin(phase)*s.bank();
                    } else roll=Math.cos(phase)*s.bank();
                }
            } else if(s.mode()==28) {
                double zoom=Math.max(0,Math.min(1,seconds/2));
                zoom=1-Math.pow(1-zoom,3);
                double d=distance*(1.8-1.45*zoom);
                cx=x-fx*d; cz=z-fz*d;
                cy=ay+(y+s.height()-ay)*(d/distance);
            } else if(s.mode()==29) {
                cx=x-fx*distance*(.1+.9*progress); cz=z-fz*distance*(.1+.9*progress);
                cy=y+s.height()*(1-progress)+.65*progress;
            } else if(s.mode()==22) {
                cx=x-fx*distance*(1-progress); cz=z-fz*distance*(1-progress);
                cy=y+s.height()+distance*progress;
            }
        } else if(s.mode()>=14 && s.mode()<=20) {
            if(s.mode()==15) {
                cx=x+fx*distance; cz=z+fz*distance; ax=x; az=z; roll=0;
            } else if(s.mode()==16 || s.mode()==18) {
                double side=distance+s.side();
                cx=x+rx*side; cz=z+rz*side; ax=x; az=z; roll=0;
            } else if(s.mode()==17) {
                cx+=rx*s.side(); cz+=rz*s.side(); ay=y+aimHeight(eye);
            } else if(s.mode()==19) {
                cx+=rx*s.side(); cz+=rz*s.side(); cy=y+eye+(s.height()-2.6);
                ax=x+fx*Math.max(4,s.ahead()*2); az=z+fz*Math.max(4,s.ahead()*2); ay=y+eye;
                roll=0;
            } else if(s.mode()==20) { roll=0; }
        }
        return lookAt(cx,cy,cz,ax,ay,az,roll,fov);
    }
    public static BoatCameraModel.Shot lookAt(double x,double y,double z,double ax,double ay,double az,double roll,float fov) {
        double dx=ax-x,dy=ay-y,dz=az-z;
        return new BoatCameraModel.Shot(x,y,z,Math.toDegrees(Math.atan2(-dx,dz)),-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))),roll,fov);
    }
}
