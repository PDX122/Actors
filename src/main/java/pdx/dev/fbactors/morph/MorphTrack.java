package pdx.dev.fbactors.morph;

import imgui.moulberry90.ImGui;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.ui.MorphLibrary;
import pdx.dev.fbactors.ui.TimelineGeometry;
import java.util.UUID;

public final class MorphTrack {
    private static UUID dragging,context,dragActor;
    private static int mode,start,end;
    private static float mouse;
    private MorphTrack() {}
    public static boolean isDragging() { return dragging!=null; }
    public static void render(TimelineGeometry g,int cursorTick,Actor actor) {
        var track=MorphTracks.timeline();
        if(dragging!=null && track.find(dragging)==null) { dragging=null; dragActor=null; MorphTracks.finish(); }
        if(track.clips.stream().noneMatch(c -> actor.take.id.equals(c.actor))) return;
        float y=ImGui.getCursorScreenPosY(),h=ImGui.getTextLineHeight(),top=Math.max(y,g.clipTop()),bottom=Math.min(y+h,g.clipBottom());
        var draw=ImGui.getWindowDrawList();
        ImGui.pushID("MorphTrack"+actor.take.id);
        if(bottom>top) {
            draw.pushClipRect(g.x(),top,g.panelRight(),bottom,true);
            draw.addText(g.x()+22,y,0xffd9a5dc,"Morph"); draw.addText(g.panelRight()-16,y,0xffd9a5dc,"+"); draw.popClipRect();
            if(ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(g.x(),top,g.panelRight(),bottom)) {
                if(ImGui.isMouseClicked(0)) MorphLibrary.open(null,actor);
                if(ImGui.isMouseClicked(1)) { MorphTracks.finish(); context=null; ImGui.openPopup("##MorphContext"); }
            }
            if(g.laneRight()>g.laneLeft() && g.pixelsPerTick()>0) {
                draw.pushClipRect(g.laneLeft(),top,g.laneRight(),bottom,true);
                for(var c:track.clips) {
                    if(!actor.take.id.equals(c.actor)) continue;
                    float x0=g.tickX(c.start),x1=g.tickX(c.end),a=Math.max(g.laneLeft(),x0),b=Math.min(g.laneRight(),x1);
                    if(b<=a) continue;
                    boolean hover=ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(a,top,b,bottom),selected=c.id.equals(MorphTracks.selected);
                    boolean muted=!actor.enabled() || !c.enabled;
                    int border=muted?0xff5a5558:selected?0xffedb8ef:0xffa478a5;
                    draw.addRectFilled(a,y+1,b,y+h-1,muted?0xff39363a:selected?0xff855a87:hover?0xff704c72:0xff573959,2);
                    draw.addRect(a,y+1,b,y+h-1,border,2);
                    if(selected || hover) {
                        if(x0>=g.laneLeft() && b-a>16) draw.addLine(a+4,y+3,a+4,y+h-3,0xbbeeeeee);
                        if(x1<=g.laneRight() && b-a>16) draw.addLine(b-4,y+3,b-4,y+h-3,0xbbeeeeee);
                    }
                    var def=Morph.registry().byId(c.morph);
                    String label=(def==null?c.morph:def.displayName())+" / "+MorphPoses.label(c.pose);
                    if(b-a>45) {
                        draw.pushClipRect(a+6,top,b-5,bottom,true); draw.addText(a+7,y,0xfff5f1ed,label); draw.popClipRect();
                    }
                    if(hover && dragging==null) {
                        ImGui.setTooltip(label+"\nDrag to move. Drag an edge to trim. Double click to browse the morph library.");
                        if(ImGui.isMouseClicked(0)) {
                            MorphTracks.finish(); MorphTracks.selected=c.id;
                            if(ImGui.isMouseDoubleClicked(0)) MorphLibrary.open(c.morph,actor);
                            else if(!actor.take.locked) { MorphTracks.begin(); dragging=c.id; dragActor=actor.take.id; mouse=ImGui.getMousePosX(); start=c.start; end=c.end; mode=Math.abs(mouse-x0)<6?1:Math.abs(mouse-x1)<6?2:0; }
                        }
                        if(ImGui.isMouseClicked(1)) { MorphTracks.finish(); context=c.id; MorphTracks.selected=c.id; ImGui.openPopup("##MorphContext"); }
                    }
                }
                draw.popClipRect();
            }
        }
        pdx.dev.fbactors.ui.MorphLibrary.dropTarget(g,y,h,actor.take.id,cursorTick);
        if(dragging!=null) {
            var c=track.find(dragging);
            if(c==null || actor.take.id.equals(dragActor)) {
                if(c==null || actor.take.locked || !ImGui.isMouseDown(0)) { dragging=null; MorphTracks.finish(); }
                else {
                    int delta=g.deltaTicks(ImGui.getMousePosX()-mouse),s=start,e=end;
                    if(mode==1) s=Math.max(0,Math.min(end-1,start+delta));
                    else if(mode==2) e=Math.max(start+1,Math.min(100_000_000,end+delta));
                    else { s=Math.max(0,Math.min(100_000_000-(end-start),start+delta)); e=s+end-start; }
                    int edge=mode==2?e:s;
                    if(Math.abs(g.tickX(edge)-g.tickX(cursorTick))<6) {
                        int snap=cursorTick-edge;
                        if(mode==0 && s+snap>=0 && (long)e+snap<=100_000_000) { s+=snap; e+=snap; }
                        else if(mode==1 && cursorTick>=0 && cursorTick<e) s=cursorTick;
                        else if(mode==2 && cursorTick>s && cursorTick<=100_000_000) e=cursorTick;
                    }
                    c.start=s; c.end=e;
                }
            }
        }
        if(ImGui.beginPopup("##MorphContext")) {
            var c=track.find(context);
            if(c!=null && actor.take.id.equals(c.actor)) {
                ImGui.beginDisabled(actor.take.locked);
                if(ImGui.menuItem(c.enabled?"Mute clip":"Unmute clip")) MorphTracks.edit(() -> c.enabled=!c.enabled);
                if(ImGui.menuItem("Duplicate morph clip")) MorphTracks.duplicate(c.id);
                if(ImGui.menuItem("Delete morph clip")) MorphTracks.edit(() -> track.clips.remove(c));
                ImGui.endDisabled(); ImGui.separator();
            }
            if(ImGui.menuItem("Morph library...")) MorphLibrary.open(c==null?null:c.morph,actor);
            ImGui.beginDisabled(actor.take.locked);
            if(ImGui.menuItem("Delete morph track")) MorphTracks.edit(() -> track.clips.removeIf(c2 -> actor.take.id.equals(c2.actor)));
            ImGui.endDisabled(); ImGui.endPopup();
        }
        ImGui.setCursorPosX(0); ImGui.dummy(Math.max(1,g.width()),Math.max(1,h)); ImGui.separator(); ImGui.popID();
    }
}
