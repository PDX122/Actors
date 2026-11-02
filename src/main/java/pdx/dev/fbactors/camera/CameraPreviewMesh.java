package pdx.dev.fbactors.camera;

import java.util.*;
import pdx.dev.fbactors.camera.CameraModePreviewScene.Point;

public final class CameraPreviewMesh {
    public record Vertex(Point point,float u,float v) {}
    public record Face(long texture,List<Vertex> vertices,int color) {}
    private static final double UNIT=1.8/32;
    private CameraPreviewMesh() {}
    public static List<Face> player(long skin,boolean slim,Point origin,double yaw,double seconds) {
        var faces=new ArrayList<Face>();

        double phase=gaitPhase(seconds),swing=Math.sin(phase)*.65;
        double lift=(12*Math.cos(swing)+2*Math.abs(Math.sin(swing))-12)*UNIT;
        origin=new Point(origin.x(),origin.y()+lift,origin.z());
        cube(faces,skin,origin,yaw,0,24,0,8,8,8,0,0,0,24,0,0);
        cube(faces,skin,origin,yaw,0,12,0,8,12,4,16,16,0,12,0,0);
        double arm=slim?3:4,center=4+arm/2;
        cube(faces,skin,origin,yaw,-center,12,0,arm,12,4,40,16,-center,24,swing,0);
        cube(faces,skin,origin,yaw,center,12,0,arm,12,4,32,48,center,24,-swing,0);
        cube(faces,skin,origin,yaw,-2,0,0,4,12,4,0,16,-2,12,-swing,0);
        cube(faces,skin,origin,yaw,2,0,0,4,12,4,16,48,2,12,swing,0);
        cube(faces,skin,origin,yaw,0,24,0,8,8,8,32,0,0,24,0,.5);
        cube(faces,skin,origin,yaw,0,12,0,8,12,4,16,32,0,12,0,.25);
        cube(faces,skin,origin,yaw,-center,12,0,arm,12,4,40,32,-center,24,swing,.25);
        cube(faces,skin,origin,yaw,center,12,0,arm,12,4,48,48,center,24,-swing,.25);
        cube(faces,skin,origin,yaw,-2,0,0,4,12,4,0,32,-2,12,-swing,.25);
        cube(faces,skin,origin,yaw,2,0,0,4,12,4,0,48,2,12,swing,.25);

        return faces.stream().map(f -> new Face(f.texture(),f.vertices(),lighting(f.vertices()))).toList();
    }
    public static double gaitPhase(double seconds) {

        double period=Math.PI/.55,cycles=Math.floor(seconds/period),remainder=seconds-cycles*period;
        double distance=0,cycleDistance=0;
        int steps=64;
        double dt=remainder/steps;
        for(int i=0;i<steps;i++) {
            distance+=Math.hypot(Math.cos((i+.5)*dt*.55)*2.2,2)*dt;
            cycleDistance+=Math.hypot(Math.cos((i+.5)*Math.PI/steps)*2.2,2)*period/steps;
        }
        return (cycles*cycleDistance+distance)*2.8;
    }
    public static int lighting(List<Vertex> vertices) {
        var a=vertices.get(0).point(); var b=vertices.get(1).point(); var c=vertices.get(2).point();
        double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z();
        double vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        double sun=length==0?0:Math.max(0,(nx*.45-ny*.82+nz*.35)/length);
        int r=(int)(255*(.65+.35*sun)),g=(int)(255*(.69+.31*sun)),bcol=(int)(255*(.75+.25*sun));
        return 0xff000000 | bcol<<16 | g<<8 | r;
    }
    private static void cube(List<Face> out,long skin,Point origin,double yaw,double cx,double bottom,double cz,
                             double w,double h,double d,int u,int v,double pivotX,double pivotY,double swing,double inflate) {
        double heading=Math.toRadians(yaw),cy=Math.cos(heading),sy=Math.sin(heading),cs=Math.cos(swing),ss=Math.sin(swing);
        var p=new Point[8];
        for(int i=0;i<8;i++) {
            double x=cx+((i&1)==0?-w/2-inflate:w/2+inflate),y=bottom+((i&2)==0?-inflate:h+inflate),z=cz+((i&4)==0?-d/2-inflate:d/2+inflate);
            double ry=pivotY+(y-pivotY)*cs-z*ss,rz=(y-pivotY)*ss+z*cs;
            p[i]=new Point(origin.x()+(x*cy-rz*sy)*UNIT,origin.y()+ry*UNIT,origin.z()+(x*sy+rz*cy)*UNIT);
        }
        face(out,skin,p,new int[]{6,7,5,4},u+d,v+d,w,h,0xffeeeeee);
        face(out,skin,p,new int[]{3,2,0,1},u+d+w+d,v+d,w,h,0xffd9d9d9);
        face(out,skin,p,new int[]{2,6,4,0},u,v+d,d,h,0xffbdbdbd);
        face(out,skin,p,new int[]{7,3,1,5},u+d+w,v+d,d,h,0xffc9c9c9);
        face(out,skin,p,new int[]{7,6,2,3},u+d,v,w,d,0xffffffff);
        face(out,skin,p,new int[]{0,4,5,1},u+d+w,v,w,d,0xff969696);
    }
    private static void face(List<Face> out,long texture,Point[] points,int[] indices,double u,double v,double w,double h,int color) {
        out.add(new Face(texture,List.of(new Vertex(points[indices[0]],(float)u/64,(float)v/64),new Vertex(points[indices[1]],(float)(u+w)/64,(float)v/64),
            new Vertex(points[indices[2]],(float)(u+w)/64,(float)(v+h)/64),new Vertex(points[indices[3]],(float)u/64,(float)(v+h)/64)),color));
    }
    public static List<Vertex> clip(List<Vertex> input,CameraModePreviewScene.View view) {
        var result=new ArrayList<Vertex>();
        if(input.isEmpty()) return result;
        Vertex previous=input.get(input.size()-1); double before=view.depth(previous.point());
        for(var current:input) {
            double now=view.depth(current.point()); boolean a=Double.isFinite(before)&&before>=.08,b=Double.isFinite(now)&&now>=.08;
            if(a!=b && Double.isFinite(before) && Double.isFinite(now)) {
                double t=(.08-before)/(now-before); var p=previous.point();var q=current.point();
                result.add(new Vertex(new Point(p.x()+(q.x()-p.x())*t,p.y()+(q.y()-p.y())*t,p.z()+(q.z()-p.z())*t),
                    (float)(previous.u()+(current.u()-previous.u())*t),(float)(previous.v()+(current.v()-previous.v())*t)));
            }
            if(b) result.add(current);
            previous=current; before=now;
        }
        return result;
    }
}
