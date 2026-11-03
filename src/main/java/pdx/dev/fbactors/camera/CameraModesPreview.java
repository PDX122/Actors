package pdx.dev.fbactors.camera;

import imgui.moulberry90.ImGui;
import java.util.*;
import pdx.dev.fbactors.camera.CameraModePreviewScene.Point;
import pdx.dev.fbactors.camera.CameraPreviewMesh.Vertex;
import pdx.dev.fbactors.camera.CameraPreviewMesh.Face;

public final class CameraModesPreview {
    record Key(BoatCam.Settings settings,long skin,long partner,long grass,long stone,long mount,boolean slim,boolean partnerSlim,float width,float height) {}
    record Triangle(long texture,CameraModePreviewScene.Pixel a,CameraModePreviewScene.Pixel b,CameraModePreviewScene.Pixel c,
                            Vertex ua,Vertex ub,Vertex uc,int color,int fog) {}
    private record Cached(Key key,int budget,List<Triangle> triangles,long time) {}

    static final int VERTICES_PER_TRIANGLE=10;
    static final int DRAW_LIST_CEILING=48_000;
    static final int CHROME_RESERVE=512;
    static int triangleBudget(int currentVertices,boolean animated) {
        return Math.max(0,Math.min(animated?1200:120,(DRAW_LIST_CEILING-currentVertices-CHROME_RESERVE)/VERTICES_PER_TRIANGLE));
    }
    private static double previewStarted=Double.NaN;
    public static void restart() { previewStarted=Double.NaN; }
    private static final Map<Integer,Cached> cache=new HashMap<>();
    private CameraModesPreview() {}
    public static void reopen() { CameraPreviewAssets.retryFailed(); restart(); }
    public static boolean draw(String id,BoatCam.Settings tuning,float width,float height,boolean animated) {
        width=Math.max(1,width); height=Math.max(1,height);
        boolean clicked=ImGui.invisibleButton(id,width,height);
        if(!ImGui.isItemVisible()) return clicked;
        float x=ImGui.getItemRectMinX(),y=ImGui.getItemRectMinY();
        var draw=ImGui.getWindowDrawList();
        int budget=triangleBudget(draw.getVtxCurrentIdx(),animated);
        if(budget==0) return clicked;

        for(int band=0;band<32;band++) {
            int color=mix(0xffc4a27e,0xffe6d6c2,band/31.0);
            draw.addRectFilled(x,y+height*band/32,x+width,y+height*(band+1)/32,color);
        }
        draw.pushClipRect(x,y,x+width,y+height,true);
        try {
            if(!pdx.dev.fbactors.debug.ErrorRecovery.FAILURES.stopped("Camera preview")) {
                var subject=CameraPreviewAssets.subject();
                var second=BoatStudioState.get().rival; if(second==null) second=subject;
                long skin=CameraPreviewAssets.skin(subject),partner=CameraPreviewAssets.skin(second);
                long grass=CameraPreviewAssets.floor(CameraModePreviewScene.ice(tuning.mode())?"blue_ice":"grass_block_top"),stone=CameraPreviewAssets.floor("stone");
                long mount=CameraModePreviewScene.ice(tuning.mode()) || CameraModePreviewScene.horse(tuning.mode())?CameraPreviewAssets.floor("oak_planks"):0;
                var key=new Key(tuning,skin,partner,grass,stone,mount,CameraPreviewAssets.slim(subject),CameraPreviewAssets.slim(second),width,height);
                if(animated && !Double.isFinite(previewStarted)) previewStarted=ImGui.getTime();
                int slot=tuning.mode()*2+(animated?1:0); long now=System.nanoTime();
                var previous=cache.get(slot);
                if(previous==null || !previous.key().equals(key) || previous.budget()!=budget || animated && now-previous.time()>16_666_666L) {
                    previous=new Cached(key,budget,scene(key,animated?(ImGui.getTime()-previewStarted)%(CameraModesCatalog.timed(tuning.mode()) && tuning.mode()!=2?12:3600):3.5,budget),now); cache.put(slot,previous);
                }
                for(var t:previous.triangles()) {
                    draw.addImageQuad(t.texture(),
                    x+t.a().x(),y+t.a().y(),x+t.b().x(),y+t.b().y(),x+t.c().x(),y+t.c().y(),x+t.c().x(),y+t.c().y(),
                    t.ua().u(),t.ua().v(),t.ub().u(),t.ub().v(),t.uc().u(),t.uc().v(),t.uc().u(),t.uc().v(),t.color());
                    if(t.fog()!=0) draw.addTriangleFilled(x+t.a().x(),y+t.a().y(),x+t.b().x(),y+t.b().y(),x+t.c().x(),y+t.c().y(),t.fog());
                }
                if(skin==0 || grass==0 || stone==0) draw.addText(x+8,y+8,0xffeeeeee,"Loading Minecraft textures...");
            } else draw.addText(x+8,y+8,0xffeeeeee,"Preview paused after an error");
        } catch(RuntimeException | LinkageError | AssertionError error) {
            cache.clear(); pdx.dev.fbactors.debug.ErrorRecovery.report("Camera preview",error);
        } finally { draw.popClipRect(); }
        draw.addRect(x,y,x+width,y+height,0xff65594d,6);
        return clicked;
    }
    static List<Triangle> scene(Key key,double seconds,int budget) {
        if(budget<=0) return List.of();
        List<Triangle> base=List.of();

        for(int detail=1;detail<=64;detail*=2) {
            base=baseScene(key,seconds,detail);
            if(base.size()<=budget) break;
        }
        if(base.size()>budget) return List.of();
        var view=new CameraModePreviewScene.View(CameraModePreviewScene.camera(key.settings(),seconds,key.width()/key.height()),key.width(),key.height());
        var result=new ArrayList<Triangle>();
        for(int i=0;i<base.size();i++) {
            var t=base.get(i);
            int allowance=(budget-result.size())/(base.size()-i);
            tessellate(result,view,t.texture(),t.ua(),t.ub(),t.uc(),t.color(),0,key.width(),key.height(),allowance);
        }
        return result;
    }
    private static List<Triangle> baseScene(Key key,double seconds,int detail) {
        var player=CameraModePreviewScene.subject(seconds,key.settings().mode());
        var camera=CameraModePreviewScene.camera(key.settings(),seconds,key.width()/key.height());
        var view=new CameraModePreviewScene.View(camera,key.width(),key.height());
        var faces=new ArrayList<Face>();
        int ox=(int)Math.floor(player.x()/4)*4,oz=(int)Math.floor(player.z()/4)*4;
        var partner=CameraModePreviewScene.partner(seconds);
        int radius=(int)Math.ceil(Math.max(32,key.settings().distance()+28));
        radius=Math.min(48,radius);
        radius=(radius+3)/4*4;
        int cell=4*Math.min(detail,16);
        for(int z=Math.floorDiv(oz-radius,cell)*cell;z<oz+radius;z+=cell) for(int x=Math.floorDiv(ox-radius,cell)*cell;x<ox+radius;x+=cell) {
            boolean iceTrack=CameraModePreviewScene.ice(key.settings().mode()),horseTrail=CameraModePreviewScene.horse(key.settings().mode());
            double pathX=Math.sin((z+cell*.5)*.275/(iceTrack?4:horseTrail?1.8:1))*(iceTrack?8:horseTrail?5.2:4);
            boolean path=iceTrack?Math.abs(x+cell*.5-pathX)>5:Math.abs(x+cell*.5-pathX)<1.2;
            long texture=path?key.stone():key.grass(); if(texture==0) continue;
            boolean nearby=Math.hypot(x+cell*.5-player.x(),z+cell*.5-player.z())<6
                || key.settings().mode()==7 && Math.hypot(x+cell*.5-partner.x(),z+cell*.5-partner.z())<6;
            int divisions=nearby?Math.max(1,8/detail):1;
            for(int row=0;row<divisions;row++) for(int col=0;col<divisions;col++) {
                float u=(float)col/divisions,v=(float)row/divisions,step=1f/divisions;
                double shadow=shadow(x+(u+step/2)*cell,z+(v+step/2)*cell,player);
                if(key.settings().mode()==7) shadow=Math.max(shadow,shadow(x+(u+step/2)*cell,z+(v+step/2)*cell,partner));
                int tint=mix(path?0xffe2e6e8:CameraModePreviewScene.ice(key.settings().mode())?0xffffd8aa:0xff80a785,0xff384438,shadow*.58);
                faces.add(new Face(texture,List.of(groundVertex(x,z,u,v,cell),groundVertex(x,z,u+step,v,cell),
                    groundVertex(x,z,u+step,v+step,cell),groundVertex(x,z,u,v+step,cell)),tint));
            }
        }
        boolean ice=CameraModePreviewScene.ice(key.settings().mode()),horse=CameraModePreviewScene.horse(key.settings().mode());
        double yaw=CameraModePreviewScene.heading(seconds,key.settings().mode());
        if(key.mount()!=0 && (ice || horse)) faces.addAll(CameraPreviewMesh.mount(key.mount(),player,yaw+(ice?Math.sin(seconds*.8)*30:0),seconds,horse));
        var rider=new Point(player.x(),horse?1:ice?-.25:0,player.z());
        if(key.skin()!=0) {
            var mesh=CameraPreviewMesh.player(key.skin(),key.slim(),rider,yaw+(ice?Math.sin(seconds*.8)*30:0),seconds,ice || horse);
            faces.addAll(detail>=2?mesh.subList(0,36):mesh);
        }
        if(key.settings().mode()==7 && key.partner()!=0) {
            var mesh=CameraPreviewMesh.player(key.partner(),key.partnerSlim(),CameraModePreviewScene.partner(seconds),CameraModePreviewScene.heading(seconds),seconds+.5);
            faces.addAll(detail>=2?mesh.subList(0,36):mesh);
        }
        faces.sort(Comparator.comparingDouble((Face f) -> f.vertices().stream().mapToDouble(v -> view.depth(v.point())).average().orElse(0)).reversed());
        var triangles=new ArrayList<Triangle>();
        for(var face:faces) {
            if(!view.visible(face.vertices())) continue;
            var clipped=CameraPreviewMesh.clip(face.vertices(),view);
            if(clipped.size()<3) continue;
            var a=view.project(clipped.get(0).point()); if(a==null) continue;
            for(int i=1;i<clipped.size()-1;i++) {
                var b=view.project(clipped.get(i).point()); var c=view.project(clipped.get(i+1).point());
                if(b==null || c==null) continue;
                if(Math.max(a.x(),Math.max(b.x(),c.x()))<0 || Math.min(a.x(),Math.min(b.x(),c.x()))>key.width()
                    || Math.max(a.y(),Math.max(b.y(),c.y()))<0 || Math.min(a.y(),Math.min(b.y(),c.y()))>key.height()) continue;
                triangles.add(new Triangle(face.texture(),a,b,c,clipped.get(0),clipped.get(i),clipped.get(i+1),face.color(),0));
            }
        }
        return triangles;
    }
    private static Vertex groundVertex(int x,int z,float u,float v,int cell) {
        int repeat=CameraPreviewAssets.FLOOR_REPEAT;
        return new Vertex(new Point(x+u*cell,0,z+v*cell),(Math.floorMod(x,repeat)+u*cell)/repeat,(Math.floorMod(z,repeat)+v*cell)/repeat);
    }
    private static double shadow(double x,double z,Point p) {
        double dx=x-p.x(),dz=z-p.z();
        return Math.max(Math.exp(-(dx*dx+dz*dz)*9),.65*Math.exp(-((dx-.35)*(dx-.35)*2.5+(dz-.25)*(dz-.25)*4)));
    }
    private static int mix(int a,int b,double t) {
        t=Math.max(0,Math.min(1,t)); int color=0xff000000;
        for(int shift=0;shift<24;shift+=8) color|=(int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t)<<shift;
        return color;
    }
    private static Vertex midpoint(Vertex a,Vertex b) {
        var p=a.point(); var q=b.point();
        return new Vertex(new Point((p.x()+q.x())*.5,(p.y()+q.y())*.5,(p.z()+q.z())*.5),(a.u()+b.u())*.5f,(a.v()+b.v())*.5f);
    }
    private static double error(CameraModePreviewScene.Pixel a,CameraModePreviewScene.Pixel b,CameraModePreviewScene.Pixel mid) {
        return Math.hypot(mid.x()-(a.x()+b.x())*.5,mid.y()-(a.y()+b.y())*.5);
    }
    private static void tessellate(List<Triangle> out,CameraModePreviewScene.View view,long texture,Vertex a,Vertex b,Vertex c,int color,int level,float width,float height,int allowance) {
        var pa=view.project(a.point()); var pb=view.project(b.point()); var pc=view.project(c.point());
        if(pa==null || pb==null || pc==null) return;
        if(Math.max(pa.x(),Math.max(pb.x(),pc.x()))<0 || Math.min(pa.x(),Math.min(pb.x(),pc.x()))>width
            || Math.max(pa.y(),Math.max(pb.y(),pc.y()))<0 || Math.min(pa.y(),Math.min(pb.y(),pc.y()))>height) return;
        var ab=midpoint(a,b); var bc=midpoint(b,c); var ca=midpoint(c,a);

        double eab=error(pa,pb,view.project(ab.point())),ebc=error(pb,pc,view.project(bc.point())),eca=error(pc,pa,view.project(ca.point()));
        if(allowance>=2 && level<8 && Math.max(eab,Math.max(ebc,eca))> .75) {
            if(eab>=ebc && eab>=eca) {
                tessellate(out,view,texture,a,ab,c,color,level+1,width,height,allowance/2); tessellate(out,view,texture,ab,b,c,color,level+1,width,height,allowance-allowance/2);
            } else if(ebc>=eca) {
                tessellate(out,view,texture,a,b,bc,color,level+1,width,height,allowance/2); tessellate(out,view,texture,a,bc,c,color,level+1,width,height,allowance-allowance/2);
            } else {
                tessellate(out,view,texture,a,b,ca,color,level+1,width,height,allowance/2); tessellate(out,view,texture,ca,b,c,color,level+1,width,height,allowance-allowance/2);
            }
        } else {
            double depth=(pa.depth()+pb.depth()+pc.depth())/3;
            double fog=Math.max(0,Math.min(1,(depth-10)/16));
            int atmosphere=fog<=0?0:((int)(fog*255)<<24)|0x00e6d6c2;
            out.add(new Triangle(texture,pa,pb,pc,a,b,c,color,atmosphere));
        }
    }
}
