package pdx.dev.fbactors.camera;

import java.util.*;
import pdx.dev.fbactors.camera.CameraModePreviewScene.Point;
import pdx.dev.fbactors.camera.CameraModePreviewScene.Pixel;

/** A neutral, lit camera test set. It uses the same lens and camera math as playback. */
public final class CameraPreviewStudio {
    public record Triangle(Pixel a,Pixel b,Pixel c,int ca,int cb,int cc,boolean subject,long texture,float au,float av,float bu,float bv,float cu,float cv) {}
    private record Vertex(Point p,Point n,int color,float u,float v,long texture) {
        Vertex(Point p,Point n,int color) { this(p,n,color,0,0,0); }
    }
    private record Face(Vertex a,Vertex b,Vertex c,boolean subject,boolean twoSided) {
        Face(Vertex a,Vertex b,Vertex c,boolean subject) { this(a,b,c,subject,false); }
    }
    
    private CameraPreviewStudio() {}

    public static List<Triangle> render(BoatCam.Settings tuning,double seconds,float width,float height,int budget) {
        return render(tuning,seconds,width,height,budget,List.of());
    }
    public static List<Triangle> render(BoatCam.Settings tuning,double seconds,float width,float height,int budget,List<CameraPreviewNative.Subject> subjects) {
        if(budget<1 || width<=0 || height<=0) return List.of();
        var camera=CameraModePreviewScene.camera(tuning,seconds,width/height);
        var view=new CameraModePreviewScene.View(camera,width,height);
        var actor=CameraModePreviewScene.subject(seconds,tuning.mode());
        List<Triangle> result=List.of();
        for(int detail=0;detail<5;detail++) {
            var faces=new ArrayList<Face>();
            environment(faces,actor,seconds,tuning.mode(),detail);
            for(var subject:subjects) nativeSubject(faces,subject);
            faces.sort(Comparator.comparingInt((Face f)->ground(f)?0:1).thenComparingDouble(f->ground(f)?f.a().p().y():
                -(view.depth(f.a().p())+view.depth(f.b().p())+view.depth(f.c().p()))/3));
            var triangles=new ArrayList<Triangle>();
            for(var f:faces) project(triangles,f,view,camera,width,height,tuning);
            result=triangles;
            if(result.size()<=budget) return result;
        }
        // The subject takes priority if another UI panel has almost exhausted the draw list.
        var sorted=new ArrayList<>(result);
        sorted.sort(Comparator.comparing(Triangle::subject).reversed());
        return sorted.subList(0,Math.min(budget,sorted.size()));
    }
    private static void nativeSubject(List<Face> out,CameraPreviewNative.Subject subject) {
        var input=subject.mesh().vertices();
        double angle=Math.toRadians(subject.yaw()),c=Math.cos(angle),s=Math.sin(angle);
        var vertices=new ArrayList<Vertex>(input.size());
        var materials=new HashMap<String,Long>();
        for(var v:input) {
            long texture=v.texture()==null?subject.fallbackTexture():materials.computeIfAbsent(v.texture(),id->{
                long handle=pdx.dev.fbactors.compat.Compat.cameraPreviewTexture(id);
                if(handle!=0) pdx.dev.fbactors.compat.Compat.sampleNearest(handle);
                return handle;
            });
            if(texture==0) texture=subject.fallbackTexture();
            var p=subject.origin();
            double x=v.x(),y=v.y(),z=v.z(),nx=v.nx(),ny=v.ny(),nz=v.nz();
            vertices.add(new Vertex(new Point(p.x()+x*c-z*s,p.y()+y,p.z()+x*s+z*c),
                new Point(nx*c-nz*s,ny,nx*s+nz*c),v.color(),v.u(),v.v(),texture));
        }
        for(int i=0;i+3<vertices.size();i+=4) {
            String material=input.get(i).texture();
            boolean wings=material!=null && (material.contains("elytra") || material.contains("/wings/"));
            out.add(new Face(vertices.get(i),vertices.get(i+1),vertices.get(i+2),true,wings));
            out.add(new Face(vertices.get(i),vertices.get(i+2),vertices.get(i+3),true,wings));
        }
    }
    private static boolean ground(Face f) {
        return !f.subject() && f.a().p().y()<.02 && f.a().p().y()==f.b().p().y() && f.a().p().y()==f.c().p().y();
    }
    private static void environment(List<Face> out,Point actor,double time,int mode,int detail) {
        plane(out,new Point(-2048,-.025,-2048),new Point(2048,-.025,-2048),new Point(2048,-.025,2048),new Point(-2048,-.025,2048),0xffe4e7e9,false);
        if(CameraModePreviewScene.ice(mode)) {
            plane(out,new Point(-12,-.015,-48),new Point(12,-.015,-48),new Point(12,-.015,160),new Point(-12,-.015,160),0xffe4d4bb,false);
            for(int side:new int[]{-1,1}) plane(out,new Point(side*12-.04,.002,-48),new Point(side*12+.04,.002,-48),new Point(side*12+.04,.002,160),new Point(side*12-.04,.002,160),0xffc1a889,false);
        }
        int step=detail<3?4:8;
        double cx=Math.floor(actor.x()/4)*4,cz=Math.floor(actor.z()/4)*4;
        for(int axis=0;axis<2;axis++) for(int line=-32;line<=32;line+=step) for(int segment=-32;segment<32;segment+=16) {
            double coordinate=(axis==0?cz:cx)+line,half=coordinate%16==0?.035:.018;
            int color=coordinate%16==0?0x00aebcc5:0x00c6cfd4;
            var a=new Point(axis==0?cx+segment:coordinate-half,.001,axis==0?coordinate-half:cz+segment);
            var b=new Point(axis==0?cx+segment+16:coordinate+half,.001,axis==0?coordinate-half:cz+segment);
            var c=new Point(axis==0?cx+segment+16:coordinate+half,.001,axis==0?coordinate+half:cz+segment+16);
            var d=new Point(axis==0?cx+segment:coordinate-half,.001,axis==0?coordinate+half:cz+segment+16);
            var va=gridVertex(a,actor,color);var vb=gridVertex(b,actor,color);var vc=gridVertex(c,actor,color);var vd=gridVertex(d,actor,color);
            out.add(new Face(va,vb,vc,false));out.add(new Face(va,vc,vd,false));
        }
        shadow(out,actor,CameraModePreviewScene.horse(mode)?1.1:CameraModePreviewScene.ice(mode)?1:.55,CameraModePreviewScene.horse(mode)?1.6:CameraModePreviewScene.ice(mode)?1.4:.7,.26);
        if(mode==7) shadow(out,CameraModePreviewScene.partner(time),.55,.7,.26);
    }
    private static Vertex gridVertex(Point p,Point actor,int color) {
        double fade=Math.max(0,1-Math.hypot(p.x()-actor.x(),p.z()-actor.z())/32);
        return new Vertex(p,new Point(0,1,0),color|((int)(fade*fade*180)<<24));
    }
    private static void shadow(List<Face> out,Point p,double rx,double rz,double opacity) {
        for(int layer=0;layer<2;layer++) {
            double radius=layer==0?1:.45;
            var centre=new Vertex(new Point(p.x()+.2,.006+layer*.0001,p.z()+.15),new Point(0,1,0),(int)(opacity*255*.65)<<24|0x00484947);
            for(int i=0;i<16;i++) {
                double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
                out.add(new Face(centre,new Vertex(new Point(centre.p().x()+Math.cos(a)*rx*radius,centre.p().y(),centre.p().z()+Math.sin(a)*rz*radius),centre.n(),0x00484947),
                    new Vertex(new Point(centre.p().x()+Math.cos(b)*rx*radius,centre.p().y(),centre.p().z()+Math.sin(b)*rz*radius),centre.n(),0x00484947),false));
            }
        }
    }
    private static void plane(List<Face> out,Point a,Point b,Point c,Point d,int color,boolean subject) {
        var n=new Point(0,1,0);var va=new Vertex(a,n,color);var vb=new Vertex(b,n,color);var vc=new Vertex(c,n,color);var vd=new Vertex(d,n,color);
        out.add(new Face(va,vb,vc,subject));out.add(new Face(va,vc,vd,subject));
    }
    private static void project(List<Triangle> out,Face face,CameraModePreviewScene.View view,BoatCameraModel.Shot camera,float width,float height,BoatCam.Settings tuning) {
        var input=List.of(face.a(),face.b(),face.c());
        var clipped=new ArrayList<Vertex>();
        var before=input.get(2);double d0=view.depth(before.p());
        for(var v:input) {
            double d1=view.depth(v.p());
            if((d0>=.08)!=(d1>=.08)) clipped.add(interpolate(before,v,(.08-d0)/(d1-d0)));
            if(d1>=.08) clipped.add(v);
            before=v;d0=d1;
        }
        if(clipped.size()<3) return;
        var a=clipped.get(0);var pa=view.project(a.p());if(pa==null) return;
        for(int i=1;i<clipped.size()-1;i++) {
            var b=clipped.get(i);var c=clipped.get(i+1);var pb=view.project(b.p());var pc=view.project(c.p());
            if(pb==null || pc==null || Math.max(pa.x(),Math.max(pb.x(),pc.x()))<0 || Math.min(pa.x(),Math.min(pb.x(),pc.x()))>width
                || Math.max(pa.y(),Math.max(pb.y(),pc.y()))<0 || Math.min(pa.y(),Math.min(pb.y(),pc.y()))>height) continue;
            double cross=(pb.x()-pa.x())*(pc.y()-pa.y())-(pb.y()-pa.y())*(pc.x()-pa.x());
            if(Math.abs(cross)<.002) continue;
            double toward=a.n().x()*(camera.x()-a.p().x())+a.n().y()*(camera.y()-a.p().y())+a.n().z()*(camera.z()-a.p().z());
            if(face.subject() && !face.twoSided() && toward<=0) continue;
            out.add(new Triangle(pa,pb,pc,lit(a,camera,tuning),lit(b,camera,tuning),lit(c,camera,tuning),face.subject(),a.texture(),a.u(),a.v(),b.u(),b.v(),c.u(),c.v()));
        }
    }
    private static Vertex interpolate(Vertex a,Vertex b,double t) {
        return new Vertex(mix(a.p(),b.p(),t),mix(a.n(),b.n(),t),color(a.color(),b.color(),t),(float)(a.u()+(b.u()-a.u())*t),(float)(a.v()+(b.v()-a.v())*t),a.texture());
    }
    private static Point mix(Point a,Point b,double t) { return new Point(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t); }
    private static int lit(Vertex v,BoatCameraModel.Shot camera,BoatCam.Settings tuning) {
        double norm=Math.sqrt(v.n().x()*v.n().x()+v.n().y()*v.n().y()+v.n().z()*v.n().z());
        double sun=norm<1e-8?0:Math.max(0,(v.n().x()*-.45+v.n().y()*.83+v.n().z()*-.32)/norm);
        double shade=v.texture()==0?.94+.06*sun:.64+.36*sun;
        int c=v.color(),result=c&0xff000000;
        for(int shift=0;shift<24;shift+=8) result|=(int)((c>>>shift&255)*shade)<<shift;
        double range=Math.hypot(Math.hypot(v.p().x()-camera.x(),v.p().z()-camera.z()),v.p().y()-camera.y());
        double fog=Math.max(0,Math.min(.8,(range-Math.max(24,Math.hypot(tuning.distance(),tuning.height())*1.3))/80));
        return color(result,0xffe2e5e5,fog)&0x00ffffff | v.color()&0xff000000;
    }
    static int color(int a,int b,double t) {
        int result=0;
        for(int shift=0;shift<=24;shift+=8) result|=(int)((a>>>shift&255)*(1-t)+(b>>>shift&255)*t)<<shift;
        return result;
    }
}
