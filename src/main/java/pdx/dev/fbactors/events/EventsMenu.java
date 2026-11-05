package pdx.dev.fbactors.events;

import com.moulberry.flashback.Flashback;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.type.ImBoolean;
import pdx.dev.fbactors.ui.SkinPickerChrome;

public final class EventsMenu {
    private static final ImBoolean OPEN=new ImBoolean(false);
    private static EventKind kind=EventKind.ICBM;
    private static java.util.UUID shownClip;
    private static final imgui.moulberry90.type.ImString SEARCH=new imgui.moulberry90.type.ImString(128);
    private static String category="All";
    private EventsMenu() {}
    public static void open() { OPEN.set(true); }
    public static void open(EventKind selected) {kind=selected;shownClip=null;Events.selected=null;EventPreview.select(kind);open();}
    public static void renderWindow() {
        if(!OPEN.get())return;
        var viewport=ImGui.getMainViewport();
        ImGui.setNextWindowSize(Math.min(780,viewport.getWorkSizeX()-32),Math.min(620,viewport.getWorkSizeY()-32),ImGuiCond.Always);
        ImGui.setNextWindowBgAlpha(1);
        SkinPickerChrome.push();
        try {if(ImGui.begin("Events library###ActorEvents",OPEN,imgui.moulberry90.flag.ImGuiWindowFlags.NoResize))renderPicker();}
        finally {ImGui.end();SkinPickerChrome.pop();}
    }
    public static void renderPicker() {
        var track=Events.timeline();
        var selection=Events.selected();
        if(selection!=null && selection.kind.available() && !selection.id.equals(shownClip)) {shownClip=selection.id;kind=selection.kind;EventPreview.select(kind);}
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##EventSearch","Search events",SEARCH);
        ImGui.setNextItemWidth(180);
        if(ImGui.beginCombo("##EventCategory",category)) {
            for(String name:new String[]{"All","Explosions","Meteors"})if(ImGui.selectable(name,category.equals(name)))category=name;
            ImGui.endCombo();
        }
        ImGui.sameLine();ImGui.textDisabled(EventKind.LIBRARY.length+" events / visual only");
        float width=Math.max(1,ImGui.getContentRegionAvailX());
        float height=Math.max(1,ImGui.getContentRegionAvailY());boolean beside=width>=570;
        float browserWidth=beside?width*.54f:width;
        boolean library=ImGui.beginChild("##EventBrowser",browserWidth,beside?height:height*.48f,false);
        try {if(library) {
            float available=ImGui.getContentRegionAvailX();int columns=Math.max(1,(int)((available+8)/150));
            float tile=Math.max(1,(available-(columns-1)*8)/columns);int index=0;
            String query=SEARCH.get().strip().toLowerCase(java.util.Locale.ROOT);
            for(var entry:EventKind.LIBRARY) {
                if(!category.equals("All") && !category.equals(entry.category()) || !(entry.label+" "+entry.description()).toLowerCase(java.util.Locale.ROOT).contains(query))continue;
                if(index++%columns!=0)ImGui.sameLine();
                ImGui.beginGroup();
                if(thumbnail(entry,tile))select(entry);dragSource(entry);
                if(SkinPickerChrome.button(entry.label+"##Event"+entry.name(),entry==kind,tile))select(entry);
                dragSource(entry);ImGui.setItemTooltip(entry.description());ImGui.endGroup();
            }
            if(index==0)ImGui.textWrapped("No matching events. Try another search or category.");
            ImGui.spacing();ImGui.separator();ImGui.textDisabled("IN THIS REPLAY");
            if(ImGui.checkbox("Show events",track.enabled))Events.edit(()->track.enabled=!track.enabled);
            for(var c:java.util.List.copyOf(track.clips))if(c.kind.available() && ImGui.selectable(c.name+"##"+c.id,c.id.equals(Events.selected)))Events.selected=c.id;
            if(track.clips.stream().noneMatch(c->c.kind.available()))ImGui.textWrapped("Drag an event onto a world block. Stretch its timeline row to change the duration.");
        }}finally{ImGui.endChild();}
        if(beside)ImGui.sameLine();
        boolean inspector=ImGui.beginChild("##EventInspector",beside?0:width,beside?height:0,false);
        try {if(inspector)inspector();}finally{ImGui.endChild();}
        if(!ImGui.isAnyItemActive() && !EventsTrack.isDragging())Events.finish();
    }
    private static void select(EventKind entry) {kind=entry;shownClip=null;Events.selected=null;EventPreview.select(entry);}
    private static boolean thumbnail(EventKind entry,float width) {
        float height=width*EventPreview.HEIGHT/EventPreview.WIDTH;
        boolean clicked=ImGui.invisibleButton("##EventThumb"+entry.name(),width,height);
        if(!ImGui.isItemVisible())return clicked;
        float x=ImGui.getItemRectMinX(),y=ImGui.getItemRectMinY();var draw=ImGui.getWindowDrawList();
        long texture=EventPreview.thumbnail(entry);
        if(texture!=0)draw.addImage(texture,x,y,x+width,y+height);
        else draw.addRectFilled(x,y,x+width,y+height,0xff34302a,6);
        if(entry==kind || ImGui.isItemHovered())draw.addRect(x,y,x+width,y+height,0xffb7a28b,6);
        return clicked;
    }
    private static void dragSource(EventKind entry) {
        if(ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("ACTORS_EVENT",entry.name());EventPlacement.drag(entry);
            ImGui.text(entry.label);ImGui.textDisabled(entry.twoLocations?"Release at launch, then click impact":"Release on a world block");ImGui.endDragDropSource();
        }
    }
    private static void inspector() {
        float width=Math.max(1,ImGui.getContentRegionAvailX());
        ImGui.text(kind.label);ImGui.textDisabled(kind.category().toUpperCase(java.util.Locale.ROOT));ImGui.spacing();
        var selected=Events.selected();
        if(selected!=null && !selected.kind.available()) {editor(selected);return;}
        EventPreview.configure(selected);
        EventPreview.card(width);
        boolean hover=ImGui.isItemHovered();
        dragSource(kind);
        if(hover)ImGui.setTooltip(kind.twoLocations?"Drag onto launch, then click impact.":"Drag onto the event's world block.");
        EventPreview.controls();
        ImGui.spacing();ImGui.textWrapped(kind.description());ImGui.spacing();
        ImGui.beginDisabled(com.moulberry.flashback.Flashback.getReplayServer()==null || Events.timeline().clips.size()>=EventTimeline.MAX_CLIPS);
        if(SkinPickerChrome.button("Place "+kind.label,true,width))EventPlacement.begin(kind,false);
        ImGui.endDisabled();
        ImGui.textDisabled("Purely visual. No damage or block changes.");
        if(EventPlacement.active()) {
            ImGui.separator();ImGui.textWrapped(EventPlacement.instruction());
            if(EventPlacement.choosingImpact() && ImGui.button("Back to launch"))EventPlacement.back();
            if(EventPlacement.choosingImpact())ImGui.sameLine();
            if(ImGui.button("Cancel placement (Esc)"))EventPlacement.cancel();
        }
        if(!Events.status.isEmpty())ImGui.textWrapped(Events.status);
        var c=Events.selected();if(c!=null)editor(c);
    }
    private static void editor(EventClip c) {
        ImGui.separator();ImGui.text("Selected: "+c.name);
        if(ImGui.checkbox("Lock event",c.locked))Events.edit(()->c.locked=!c.locked);
        ImGui.beginDisabled(c.locked);
        if(!c.kind.available()) {ImGui.textWrapped("This event has been retired. It is preserved here but will not play.");if(ImGui.button("Delete retired event"))Events.remove(c);ImGui.endDisabled();return;}
        if(ImGui.checkbox("Enabled",c.enabled))Events.edit(()->c.enabled=!c.enabled);
        if(ImGui.checkbox("Event cam",c.eventCam))Events.edit(()->c.eventCam=!c.eventCam);
        if(!c.eventCam)ImGui.textWrapped("Uses your camera. If the event outlasts the footage, the last recorded frame holds until it finishes.");
        if(c.eventCam && ImGui.beginCombo("Camera style",EventCameraModel.STYLES[c.cameraStyle])) {
            for(int i=0;i<EventCameraModel.STYLES.length;i++) {int style=i;if(ImGui.selectable(EventCameraModel.STYLES[i],c.cameraStyle==i))Events.edit(()->c.cameraStyle=style);}
            ImGui.endCombo();
        }
        if(c.eventCam && ImGui.checkbox("Cinematic letterbox",c.cinematicBars))Events.edit(()->c.cinematicBars=!c.cinematicBars);
        if(c.eventCam) {
            var replay=Flashback.getReplayServer();
            if(!Flashback.supportsDistantHorizons) {
                ImGui.textWrapped("Install Distant Horizons to show recorded terrain beyond the normal render distance.");
            } else if(replay!=null) {
                var terrainPaths=replay.getMetadata().distantHorizonPaths;
                if(terrainPaths==null || terrainPaths.values().stream().noneMatch(path->path!=null && path.isDirectory())) {
                    ImGui.textWrapped("This replay has no available Distant Horizons terrain cache. Record with Distant Horizons enabled to keep far terrain visible.");
                }
            }
        }
        if(ImGui.beginCombo("Ending",EventEnding.label(c))) {
            for(int i=0;i<EventEnding.NAMES.length;i++) {int ending=EventEnding.IDS[i];if(ImGui.selectable(EventEnding.NAMES[i],c.ending==ending))Events.edit(()->{c.ending=ending;if(ending>0)c.eventCam=true;});}
            ImGui.endCombo();
        }
        if(c.eventCam)ImGui.textWrapped(c.kind.natural()?"Frames the event from a safe cinematic distance. Overhead gives a high aerial view.":"Follows the flight, then cuts to the blast. Director cuts can frame nearby existing actors.");
        var start=new imgui.moulberry90.type.ImInt(c.start);var duration=new imgui.moulberry90.type.ImInt(c.end-c.start);
        ImGui.setNextItemWidth(-1);
        if(ImGui.inputInt("Start tick",start)) {Events.begin();int length=c.end-c.start;c.start=Math.max(0,Math.min(EventClip.MAX_TICK-length,start.get()));c.end=c.start+length;Events.changed();}
        ImGui.setNextItemWidth(-1);
        if(ImGui.inputInt("Duration (ticks)",duration)) {Events.begin();c.end=(int)Math.min(EventClip.MAX_TICK,(long)c.start+Math.max(20,duration.get()));Events.changed();}
        ImGui.textDisabled(String.format(java.util.Locale.ROOT,"%.1f seconds at 20 ticks/s",(c.end-c.start)/20.0));
        if(ImGui.button(c.kind==EventKind.ICBM?"Use cinematic timing (60 s)":"Use recommended timing"))Events.edit(()->c.end=Math.min(EventClip.MAX_TICK,c.start+c.kind.duration));
        if(!c.kind.natural())slider(c.kind==EventKind.ICBM?"Missile size":"Bomb size",c.scale,.25f,4,v->c.scale=v);
        slider(c.kind.natural()?"Event size":"Explosion size",c.blast,.25f,4,v->c.blast=v);
        if(c.kind.twoLocations) {
        ImGui.textWrapped(String.format(java.util.Locale.ROOT,"Launch: %.1f, %.1f, %.1f",c.launch.x(),c.launch.y(),c.launch.z()));
        if(ImGui.button("Move launch"))EventPlacement.relocate(c,true);
        }
        ImGui.textWrapped(String.format(java.util.Locale.ROOT,"Impact: %.1f, %.1f, %.1f",c.impact.x(),c.impact.y(),c.impact.z()));
        if(ImGui.button("Move impact"))EventPlacement.relocate(c,false);
        if(c.kind.twoLocations && ImGui.button("Swap locations"))Events.edit(()->{var from=c.launch;c.launch=c.impact;c.impact=from;});
        if(c.kind==EventKind.NUCLEAR)slider("Drop height",c.dropHeight,40,512,v->c.dropHeight=v);
        if(c.kind==EventKind.ICBM) {
            if(ImGui.checkbox("Event sounds",c.sound))Events.edit(()->c.sound=!c.sound);
            slider("Sound volume",c.volume,0,2,v->c.volume=v);
        } else ImGui.textDisabled("Silent event. Audio is currently provided for ICBM only.");
        if(c.kind==EventKind.NUCLEAR)ImGui.textWrapped("Bomb model: Atomic Bomb - Easter Freebie by Valentin Winkelmann (CC BY 4.0). Credits in About.");
        if(ImGui.button("Duplicate"))Events.duplicate(c);ImGui.sameLine();if(ImGui.button("Delete"))Events.remove(c);
        ImGui.endDisabled();
    }
    private static void slider(String label,float current,float min,float max,java.util.function.Consumer<Float> set) {
        float[] value={current};if(ImGui.sliderFloat(label,value,min,max)) {Events.begin();set.accept(value[0]);Events.changed();}
    }
}
