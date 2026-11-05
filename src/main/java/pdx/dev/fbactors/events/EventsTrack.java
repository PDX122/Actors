package pdx.dev.fbactors.events;

import imgui.moulberry90.ImGui;
import java.util.UUID;
import pdx.dev.fbactors.ui.TimelineGeometry;

public final class EventsTrack {
    private static UUID dragging,context;
    private static EventTimeline owner;
    private static int mode,start,end;
    private static float mouse;
    private EventsTrack() {}
    public static boolean isDragging() { return dragging!=null; }
    public static void render(TimelineGeometry g,int cursorTick) {
        var track=Events.timeline();
        if(owner!=track) {dragging=null;context=null;owner=track;}
        row(g,null,cursorTick);
        for(var clip:java.util.List.copyOf(track.clips))if(clip.kind.available())row(g,clip,cursorTick);
        if(dragging!=null && !ImGui.isMouseDown(0)) {dragging=null;Events.finish();}
    }
    private static void row(TimelineGeometry g,EventClip clip,int cursorTick) {
        float y=ImGui.getCursorScreenPosY(),h=ImGui.getTextLineHeight();
        float top=Math.max(y,g.clipTop()),bottom=Math.min(y+h,g.clipBottom());
        float left=g.laneLeft(),right=g.laneRight();var draw=ImGui.getWindowDrawList();
        ImGui.pushID("EventRow"+(clip==null?"library":clip.id));
        if(bottom>top && g.panelRight()>g.x()) {
            draw.pushClipRect(g.x(),top,g.panelRight(),bottom,true);
            draw.addText(g.x()+10,y,0xff77b3ff,clip==null?"Events  +":(clip.locked?"  [L] ":"   ")+clip.name);
            draw.popClipRect();
            if(ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(g.x(),top,g.panelRight(),bottom)) {
                ImGui.setTooltip(clip==null?"Open the Events library. Drag an ICBM into the scene to place it.":"Click to edit. Right click for event options.");
                if(ImGui.isMouseClicked(0)) {if(clip!=null)Events.selected=clip.id;EventsMenu.open();}
                if(clip!=null && ImGui.isMouseClicked(1)) {context=clip.id;ImGui.openPopup("##EventContext");}
            }
        }
        if(clip!=null && bottom>top && right>left && g.pixelsPerTick()>0) {
            float x0=g.tickX(clip.start),x1=g.tickX(clip.end),a=Math.max(left,x0),b=Math.min(right,x1);
            if(b>a) {
                draw.pushClipRect(left,top,right,bottom,true);
                boolean hover=ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(a,top,b,bottom);
                boolean selected=clip.id.equals(Events.selected),muted=!Events.timeline().enabled || !clip.enabled;
                int fill=muted?0xff3a3838:selected?0xff93603b:hover?0xff714a30:0xff573a28;
                int border=muted?0xff666666:0xff85baff;
                draw.addRectFilled(a,y+1,b,y+h-1,fill,2);draw.addRect(a,y+1,b,y+h-1,border,2);
                if(x0>=left)draw.addLine(a+3,y+3,a+3,y+h-3,border,2);
                if(x1<=right)draw.addLine(b-3,y+3,b-3,y+h-3,border,2);
                // Phase ticks make the single stretched clip readable without extra control lanes.
                for(double phase:clip.kind==EventKind.ICBM?new double[]{MissileFlight.SEPARATION,MissileFlight.SECOND_SEPARATION,MissileFlight.SHROUD,MissileFlight.REENTRY,MissileFlight.IMPACT}:new double[]{clip.kind.impact,.60,.85}) {
                    float x=g.tickX(clip.start+(int)((clip.end-clip.start)*phase));
                    if(x>a && x<b)draw.addLine(x,y+2,x,y+h-2,phase==MissileFlight.IMPACT?0xff91baff:0x6685baff,1);
                }
                if(b-a>60) {
                    draw.pushClipRect(a+7,top,b-5,bottom,true);draw.addText(a+7,y,0xfff8f0e7,clip.name+(clip.kind.twoLocations?"  /  launch > impact":clip.kind.natural()?"  /  event > aftermath":"  /  drop > aftermath"));draw.popClipRect();
                }
                draw.popClipRect();
                if(hover && dragging==null) {
                    ImGui.setTooltip("Drag to move. Drag either edge to stretch the entire sequence. Double click to edit.\nTicks: stage separation / re-entry / impact.");
                    if(ImGui.isMouseClicked(0)) {
                        Events.finish();Events.selected=clip.id;
                        if(ImGui.isMouseDoubleClicked(0))EventsMenu.open();
                        else if(!clip.locked) {Events.begin();dragging=clip.id;mouse=ImGui.getMousePosX();start=clip.start;end=clip.end;mode=Math.abs(mouse-x0)<6?1:Math.abs(mouse-x1)<6?2:0;}
                    }
                    if(ImGui.isMouseClicked(1)) {Events.finish();context=clip.id;ImGui.openPopup("##EventContext");}
                }
            }
        }
        if(clip!=null && clip.id.equals(dragging)) {
            if(clip.locked || !ImGui.isMouseDown(0)) {dragging=null;Events.finish();}
            else {
                int delta=g.deltaTicks(ImGui.getMousePosX()-mouse),a=start,b=end;
                if(mode==1)a=Math.max(0,Math.min(end-20,start+delta));
                else if(mode==2)b=Math.max(start+20,Math.min(EventClip.MAX_TICK,end+delta));
                else {a=Math.max(0,Math.min(EventClip.MAX_TICK-(end-start),start+delta));b=a+end-start;}
                int edge=mode==2?b:a;
                if(Math.abs(g.tickX(edge)-g.tickX(cursorTick))<6) {
                    int snap=cursorTick-edge;
                    if(mode==0 && a+snap>=0 && (long)b+snap<=EventClip.MAX_TICK) {a+=snap;b+=snap;}
                    else if(mode==1 && cursorTick>=0 && cursorTick<=b-20)a=cursorTick;
                    else if(mode==2 && cursorTick>=a+20 && cursorTick<=EventClip.MAX_TICK)b=cursorTick;
                }
                if(a!=clip.start || b!=clip.end) {clip.start=a;clip.end=b;Events.changed();}
            }
        }
        if(ImGui.beginPopup("##EventContext")) {
            var c=Events.timeline().find(context);
            if(c!=null) {
                if(ImGui.menuItem("Edit event")) {Events.selected=c.id;EventsMenu.open();}
                if(ImGui.menuItem(c.locked?"Unlock event":"Lock event"))Events.edit(()->c.locked=!c.locked);
                ImGui.beginDisabled(c.locked);
                if(ImGui.menuItem(c.enabled?"Mute event":"Unmute event"))Events.edit(()->c.enabled=!c.enabled);
                if(ImGui.menuItem("Duplicate event"))Events.duplicate(c);
                if(ImGui.menuItem("Delete event"))Events.remove(c);
                ImGui.endDisabled();
            }
            ImGui.endPopup();
        }
        ImGui.setCursorPosX(0);ImGui.dummy(Math.max(1,g.width()),Math.max(1,h));ImGui.separator();ImGui.popID();
    }
}
