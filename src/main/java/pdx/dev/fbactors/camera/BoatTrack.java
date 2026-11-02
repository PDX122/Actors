package pdx.dev.fbactors.camera;

import imgui.moulberry90.ImGui;
import pdx.dev.fbactors.ui.TimelineGeometry;
import java.util.UUID;

public final class BoatTrack {
    private static UUID dragging, contextClip;
    private static BoatStudioState owner;
    private static int mode, start, end;
    private static float mouse;
    private BoatTrack() {}
    public static boolean isDragging() { return dragging != null; }
    public static void render(TimelineGeometry g, int cursorTick) {
        var state=BoatStudioState.get();
        if(owner!=state) { BoatEdits.finish(); owner=state; dragging=null; contextClip=null; BoatEdits.selected=null; }
        var track=state.timeline;
        float y=ImGui.getCursorScreenPosY(), h=ImGui.getTextLineHeight();
        float top=Math.max(y,g.clipTop()), bottom=Math.min(y+h,g.clipBottom());
        float left=g.laneLeft(), right=g.laneRight(), center=y+h*.5f, barHeight=Math.max(8,h-1);
        var draw=ImGui.getWindowDrawList();
        int color=track.enabled?0xffb7d567:0xff777777;
        ImGui.pushID("BoatCameraTrack");
        if(bottom>top && g.panelRight()>g.x()) {
            draw.pushClipRect(g.x(),top,g.panelRight(),bottom,true);
            for(int i=0;i<3;i++) {
                float row=center-4+i*4, knob=g.x()+10+(i==1?2:-2);
                draw.addLine(g.x()+5,row,g.x()+15,row,color,1);
                draw.addRectFilled(knob-1,row-1.5f,knob+1,row+1.5f,color,.5f);
            }
            draw.addText(g.x()+22,y,color,"Camera modes");
            draw.addText(g.panelRight()-16,y,color,"+");
            draw.popClipRect();
            if(ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(g.x(),top,g.panelRight(),bottom)) {
                ImGui.setTooltip("Camera modes: click to browse shots. Right click for track options.");
                if(ImGui.isMouseClicked(0)) CameraModes.open();
                if(ImGui.isMouseClicked(1)) { BoatEdits.finish(); contextClip=null; ImGui.openPopup("##BoatContext"); }
            }
        }
        boolean clipHovered=false;
        if(bottom>top && right>left && g.pixelsPerTick()>0) {
            draw.pushClipRect(left,top,right,bottom,true);
            for(var clip:track.clips) {
                float x0=g.tickX(clip.start), x1=g.tickX(clip.end);
                float a=Math.max(left,x0), b=Math.min(right,x1);
                if(b<=a) continue;
                boolean selected=clip.id.equals(BoatEdits.selected);
                boolean hover=ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(a,top,b,bottom);
                clipHovered|=hover;
                boolean muted=!track.enabled || !clip.enabled;
                int fill=muted?0xff39363a:selected?0xff6d8545:hover?0xff596d3c:0xff465732;
                int border=muted?0xff5a5558:selected?0xffc6dfa0:0xff8da363;
                float barTop=center-barHeight*.5f, barBottom=barTop+barHeight;
                draw.addRectFilled(a,barTop,b,barBottom,fill,2);
                draw.addRect(a,barTop,b,barBottom,border,2);
                if(x0>=left && b-a>4) draw.addRectFilled(a+1,barTop+1,a+3,barBottom-1,border,1);
                if(selected || hover) {
                    if(x0>=left && b-a>16) draw.addLine(a+4,barTop+3,a+4,barBottom-3,0xbbeeeeee,1);
                    if(x1<=right && b-a>16) draw.addLine(b-4,barTop+3,b-4,barBottom-3,0xbbeeeeee,1);
                }
                if(b-a>45) {
                    draw.pushClipRect(a+6,top,b-5,bottom,true);
                    draw.addText(a+7,y,muted?0xffaaa5a5:0xfff5f1ed,BoatCam.MODES[Math.max(0,Math.min(BoatCam.MODES.length-1,clip.settings.mode()))]);
                    draw.popClipRect();
                }
                if(hover && dragging==null) {
                    ImGui.setTooltip("Camera modes: drag to move, drag an edge to trim. Double click to edit. Right click for options.");
                    if(ImGui.isMouseClicked(0)) {
                        BoatEdits.finish(); BoatEdits.selected=clip.id;
                        if(ImGui.isMouseDoubleClicked(0)) BoatMenu.openClip(clip.id);
                        else if(!track.locked) {
                            BoatEdits.begin(); dragging=clip.id; mouse=ImGui.getMousePosX(); start=clip.start; end=clip.end;
                            mode=Math.abs(mouse-x0)<6?1:Math.abs(mouse-x1)<6?2:0;
                        }
                    }
                    if(ImGui.isMouseClicked(1)) {
                        BoatEdits.finish(); BoatEdits.selected=clip.id; contextClip=clip.id;
                        ImGui.openPopup("##BoatContext");
                    }
                }
            }
            draw.popClipRect();
            if(!clipHovered && ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(left,top,right,bottom) && ImGui.isMouseClicked(1)) {
                BoatEdits.finish(); contextClip=null; ImGui.openPopup("##BoatContext");
            }
        }
        if(dragging!=null) {
            var clip=track.find(dragging);
            if(clip==null || track.locked || !ImGui.isMouseDown(0)) { dragging=null; BoatEdits.finish(); }
            else {
                int delta=g.deltaTicks(ImGui.getMousePosX()-mouse), nextStart=start, nextEnd=end;
                if(mode==1) nextStart=Math.max(0,Math.min(end-1,start+delta));
                else if(mode==2) nextEnd=Math.max(start+1,Math.min(100_000_000,end+delta));
                else { nextStart=Math.max(0,Math.min(100_000_000-(end-start),start+delta)); nextEnd=nextStart+end-start; }
                int edge=mode==2?nextEnd:nextStart;
                if(Math.abs(g.tickX(edge)-g.tickX(cursorTick))<6) {
                    int snap=cursorTick-edge;
                    if(mode==0 && nextStart+snap>=0 && (long)nextEnd+snap<=100_000_000) { nextStart+=snap; nextEnd+=snap; }
                    else if(mode==1 && cursorTick>=0 && cursorTick<nextEnd) nextStart=cursorTick;
                    else if(mode==2 && cursorTick>nextStart && cursorTick<=100_000_000) nextEnd=cursorTick;
                }
                if(clip.start!=nextStart || clip.end!=nextEnd) {
                    clip.start=nextStart; clip.end=nextEnd; BoatStudioState.changed(); BoatCam.reset();
                }
            }
        }
        contextMenu(track);
        ImGui.setCursorPosX(0); ImGui.dummy(Math.max(1,g.width()),Math.max(1,h)); ImGui.separator();
        ImGui.popID();
    }
    private static void contextMenu(BoatTimeline track) {
        if(!ImGui.beginPopup("##BoatContext")) return;
        var clip=track.find(contextClip);
        if(clip!=null) {
            if(ImGui.menuItem("Edit camera mode")) BoatMenu.openClip(clip.id);
            ImGui.beginDisabled(track.locked);
            if(ImGui.menuItem(clip.enabled?"Mute clip":"Unmute clip")) BoatEdits.edit(() -> clip.enabled=!clip.enabled);
            if(ImGui.menuItem("Duplicate camera mode")) BoatEdits.edit(() -> {
                var copy=track.duplicate(clip.id); if(copy!=null) BoatEdits.selected=copy.id;
            });
            if(ImGui.menuItem("Delete camera mode")) BoatEdits.edit(() -> {
                track.clips.remove(clip); if(clip.id.equals(BoatEdits.selected)) BoatEdits.selected=null;
            });
            ImGui.endDisabled(); ImGui.separator();
        }
        if(ImGui.menuItem("Browse camera modes")) BoatMenu.open(0);
        ImGui.beginDisabled(track.locked);
        if(ImGui.menuItem(track.enabled?"Mute camera modes track":"Unmute camera modes track")) BoatEdits.edit(() -> track.enabled=!track.enabled);
        ImGui.endDisabled();
        if(ImGui.menuItem(track.locked?"Unlock camera modes track":"Lock camera modes track")) BoatEdits.edit(() -> track.locked=!track.locked);
        ImGui.beginDisabled(track.locked || track.clips.isEmpty());
        if(ImGui.menuItem("Delete camera mode track")) BoatEdits.edit(() -> {
            track.clips.clear(); BoatEdits.selected=null; dragging=null;
        });
        ImGui.setItemTooltip("Remove every camera mode clip. Undo restores them.");
        ImGui.endDisabled(); ImGui.endPopup();
    }
}
