package pdx.dev.fbactors.camera;

import imgui.moulberry90.ImGui;
import java.util.*;
import pdx.dev.fbactors.camera.CameraModePreviewScene.Point;
import pdx.dev.fbactors.camera.CameraPreviewStudio.Triangle;

public final class CameraModesPreview {
    private record Key(BoatCam.Settings settings,float width,float height,long revision) {}
    private record Cached(Key key,int budget,double seconds,List<Triangle> triangles) {}
    static final int CARDS_PER_PAGE=12,VERTICES_PER_TRIANGLE=3,DRAW_LIST_CEILING=64_000,CHROME_RESERVE=1100;
    private static final Map<Integer,Cached> cache=new HashMap<>();
    private static double started=Double.NaN,held,hoverStarted;
    private static int hoveredMode=-1;
    private static boolean paused;
    private CameraModesPreview() {}
    public static void restart() { started=Double.NaN;held=0;paused=false; }
    public static void reopen() { CameraPreviewNative.close();restart(); }
    static int triangleBudget(int currentVertices,boolean animated) {
        return Math.max(0,Math.min(animated?3200:900,(DRAW_LIST_CEILING-currentVertices-CHROME_RESERVE)/VERTICES_PER_TRIANGLE));
    }
    private static double seconds() {
        if(!Double.isFinite(started)) started=ImGui.getTime();
        if(!paused && ImGui.getTime()-started>=12) { held=12;paused=true; }
        return paused?held:ImGui.getTime()-started;
    }
    public static boolean draw(String id,BoatCam.Settings tuning,float width,float height,boolean animated) {
        width=Math.max(1,width);height=Math.max(1,height);
        boolean clicked=ImGui.invisibleButton(id,width,height);
        if(!ImGui.isItemVisible()) return clicked;
        boolean hover=!animated && ImGui.isItemHovered();
        if(hover && hoveredMode!=tuning.mode()) { hoveredMode=tuning.mode();hoverStarted=ImGui.getTime(); }
        if(!hover && hoveredMode==tuning.mode() && !animated) hoveredMode=-1;
        boolean moving=animated || hover;
        double time=animated?seconds():hover?Math.min(12,ImGui.getTime()-hoverStarted):3.5;
        float x=ImGui.getItemRectMinX(),y=ImGui.getItemRectMinY();
        var draw=ImGui.getWindowDrawList();
        int budget=triangleBudget(draw.getVtxCurrentIdx(),moving);
        draw.pushClipRect(x,y,x+width,y+height,true);
        try {
            draw.addRectFilledMultiColor(x,y,x+width,y+height,0xffe4e9ec,0xffe4e9ec,0xfff1efeb,0xfff1efeb);
            if(budget>0 && !pdx.dev.fbactors.debug.ErrorRecovery.FAILURES.stopped("Camera preview")) {
                var nativeFrame=CameraPreviewNative.get(tuning.mode(),time,moving,animated);
                time=nativeFrame.seconds();
                var key=new Key(tuning,width,height,nativeFrame.revision());
                int slot=tuning.mode()*2+(moving?1:0);
                var previous=cache.get(slot);
                if(previous==null || !previous.key().equals(key) || previous.budget()!=budget || moving && previous.seconds()!=time) {
                    previous=new Cached(key,budget,time,CameraPreviewStudio.render(tuning,time,width,height,budget,nativeFrame.subjects()));
                    cache.put(slot,previous);
                }
                float u=ImGui.getFontTexUvWhitePixelX(),v=ImGui.getFontTexUvWhitePixelY();
                long texture=0;
                for(var t:previous.triangles()) {
                    if(t.texture()!=texture) {
                        if(texture!=0) draw.popTextureID();
                        texture=t.texture();
                        if(texture!=0) draw.pushTextureID(texture);
                    }
                    draw.primReserve(3,3);
                    draw.primVtx(x+t.a().x(),y+t.a().y(),texture==0?u:t.au(),texture==0?v:t.av(),t.ca());
                    draw.primVtx(x+t.b().x(),y+t.b().y(),texture==0?u:t.bu(),texture==0?v:t.bv(),t.cb());
                    draw.primVtx(x+t.c().x(),y+t.c().y(),texture==0?u:t.cu(),texture==0?v:t.cv(),t.cc());
                }
                if(texture!=0) draw.popTextureID();
                if(nativeFrame.subjects().isEmpty() && !CameraModesCatalog.pov(tuning.mode()) && !CameraModesCatalog.fpv(tuning.mode()))
                    draw.addText(x+12,y+height*.5f,0xff685e50,"Preparing native animation...");
                if(animated) {
                    draw.addRectFilled(x+10,y+10,x+118,y+31,0xd9f3f2ee,3);
                    draw.addText(x+18,y+14,0xff5d5a54,"CAMERA VIEW");
                    var camera=CameraModePreviewScene.camera(tuning,time,width/height);
                    String lens=String.format(Locale.ROOT,"%.0f deg / %.1f m high",camera.fov(),camera.y()-CameraModePreviewScene.subject(time,tuning.mode()).y());
                    draw.addRectFilled(x+10,y+height-31,x+ImGui.calcTextSizeX(lens)+26,y+height-10,0xd9f3f2ee,3);
                    draw.addText(x+18,y+height-27,0xff5d5a54,lens);
                    if(width>=350 && height>=190) plan(tuning,time,x+width-140,y+height-112,128,100,width/height);
                }
            }
        } catch(RuntimeException | LinkageError | AssertionError error) {
            cache.clear();pdx.dev.fbactors.debug.ErrorRecovery.report("Camera preview",error);
            draw.addText(x+12,y+12,0xff554f48,"Preview unavailable. See the error report.");
        } finally { draw.popClipRect(); }
        draw.addRect(x,y,x+width,y+height,0x40666058,4);
        return clicked;
    }
    public static void playback() {
        double now=ImGui.getTime();
        if(ImGui.smallButton(paused?"Play##CameraPreview":"Pause##CameraPreview")) {
            if(paused) { if(held>=12)held=0;started=now-held;paused=false; }
            else { held=seconds();paused=true; }
        }
        ImGui.sameLine();
        if(ImGui.smallButton("Replay##CameraPreview")) restart();
        ImGui.sameLine();ImGui.setNextItemWidth(-1);
        float[] at={(float)seconds()};
        if(ImGui.sliderFloat("##CameraPreviewTime",at,0,12,"%.1f s")) {
            held=at[0];started=now-held;paused=true;
        }
    }
    private static void plan(BoatCam.Settings tuning,double time,float x,float y,float w,float h,double aspect) {
        var draw=ImGui.getWindowDrawList();
        draw.addRectFilled(x,y,x+w,y+h,0xe82f2c28,5);
        draw.addText(x+10,y+8,0xffc8c1b6,"CAMERA PATH");
        var actor=CameraModePreviewScene.subject(time,tuning.mode());
        var camera=CameraModePreviewScene.camera(tuning,time,aspect);
        double extent=Math.max(8,Math.hypot(camera.x()-actor.x(),camera.z()-actor.z())*1.2);
        float scale=(float)((Math.min(w,h)-32)/(extent*2)),cx=x+w*.5f,cy=y+h*.61f;
        float px=0,py=0;
        for(int i=0;i<=30;i++) {
            double t=Math.max(0,time-3+i*.2);
            var p=CameraModePreviewScene.camera(tuning,t,aspect);
            float qx=cx+(float)(p.x()-actor.x())*scale,qy=cy-(float)(p.z()-actor.z())*scale;
            if(i>0) draw.addLine(px,py,qx,qy,0xffceaa67,1.4f);
            px=qx;py=qy;
        }
        float vx=cx+(float)(camera.x()-actor.x())*scale,vy=cy-(float)(camera.z()-actor.z())*scale;
        draw.addLine(vx,vy,cx,cy,0x607b9daf,1);
        draw.addCircleFilled(cx,cy,3,0xff7398c4,12);
        draw.addCircleFilled(vx,vy,3,0xffe2bb72,12);
        double heading=Math.toRadians(camera.yaw());
        draw.addLine(vx,vy,vx-(float)Math.sin(heading)*9,vy-(float)Math.cos(heading)*9,0xffe2bb72,1.5f);
    }
}
