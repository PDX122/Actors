package pdx.dev.fbactors.camera;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.*;
import imgui.moulberry90.type.ImBoolean;
import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.settings.FbSettings;
import pdx.dev.fbactors.ui.SkinPickerChrome;
import java.util.UUID;

public final class BoatMenu {
    private static final ImBoolean OPEN = new ImBoolean(false);
    private static int page, from, to;
    private static String shotGroup="All shots";
    private static final imgui.moulberry90.type.ImString SEARCH=new imgui.moulberry90.type.ImString(128);
    private static final String[] PAGES = {"Modes", "Timeline", "Boat handling"};
    private BoatMenu() {}
    public static void open(int section) { page=Math.max(0,Math.min(2,section)); CameraModesPreview.reopen(); OPEN.set(true); useRange(); }
    public static void openClip(UUID id) {
        open(1); BoatEdits.selected=id;
        var clip=BoatStudioState.get().timeline.find(id);
        if(clip!=null) { from=clip.start; to=clip.end; }
    }
    public static void render() {
        if (!OPEN.get()) return;
        var viewport=ImGui.getMainViewport();
        float maxWidth=Math.max(1,viewport.getWorkSizeX()-24),maxHeight=Math.max(1,viewport.getWorkSizeY()-24);
        ImGui.setNextWindowSize(Math.min(960,maxWidth),Math.min(720,maxHeight),ImGuiCond.Appearing);
        ImGui.setNextWindowSizeConstraints(Math.min(360,maxWidth),Math.min(340,maxHeight),maxWidth,maxHeight);
        ImGui.setNextWindowBgAlpha(1); SkinPickerChrome.push();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,12,10);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing,6,5);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding,8,4);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding,10);
        boolean visible=ImGui.begin("Camera modes###FbBoatCam",OPEN);
        try {
            if (visible) {
                if(!CameraModes.notice.isBlank()) ImGui.textWrapped(CameraModes.notice);
                if(BoatStudioState.waitingToSave()) ImGui.textWrapped("Boat changes are waiting to save. Retrying...");
                float tabWidth=Math.max(1,(ImGui.getContentRegionAvailX()-12)/3);
                for(int i=0;i<PAGES.length;i++) {
                    if(i>0) ImGui.sameLine();
                    if(SkinPickerChrome.button(PAGES[i],page==i,tabWidth)) page=i;
                }
                ImGui.separator();
                float height=Math.max(1,ImGui.getContentRegionAvailY()-42);
                boolean body=ImGui.beginChild("##BoatContents",0,height,false);
                try {
                    if(body) {
                        if(page==1) targetPicker("Subject",false);
                        switch(page) {
                            case 0 -> camera();
                            case 1 -> timelineControls();
                            case 2 -> handling();
                        }
                    }
                } finally { ImGui.endChild(); }
                ImGui.separator();
                if(SkinPickerChrome.button("Done",true,80)) OPEN.set(false);
            }
        } finally { ImGui.end(); ImGui.popStyleVar(4); SkinPickerChrome.pop(); }
        if(!OPEN.get()) { FbSettings.settings().save(); BoatStudioState.flush(true); }
    }
    private static String label(UUID id) {
        if(id==null) return "Choose a subject...";
        var actor=FlashbackActors.ACTORS.byUuid(id);
        if(actor!=null) return actor.displayName();
        var mc=Minecraft.getInstance();
        var player=mc.level==null?null:mc.level.getPlayerByUUID(id);
        return player==null?"Unavailable at this tick":player.getName().getString();
    }
    private static void targetPicker(String caption, boolean rival) {
        var state=BoatStudioState.get(); UUID chosen=rival?state.rival:state.target;
        ImGui.textUnformatted(caption);
        ImGui.setNextItemWidth(-1);
        if(ImGui.beginCombo("##"+caption,label(chosen))) {
            var mc=Minecraft.getInstance();
            var seen=new java.util.HashSet<UUID>();
            for(var actor:FlashbackActors.ACTORS.actors()) {
                UUID id=actor.take.id; seen.add(id);
                if(rival && id.equals(state.target)) continue;
                if(ImGui.selectable("Actor: "+actor.displayName()+"##"+id,id.equals(chosen))) choose(id,rival);
            }
            if(mc.level!=null) for(var p:mc.level.players()) {
                UUID id=p.getUUID();
                if(p==mc.player || seen.contains(id) || rival && id.equals(state.target)) continue;
                if(ImGui.selectable("Player: "+p.getName().getString()+"##"+id,id.equals(chosen))) choose(id,rival);
            }
            ImGui.endCombo();
        }
        var selected=pdx.dev.fbactors.ui.ActorTrack.selected();
        if(!rival && selected!=null && ImGui.smallButton("Use selected actor")) choose(selected.take.id,false);
    }
    private static void choose(UUID id, boolean rival) {
        var state=BoatStudioState.get();
        if(rival) state.rival=id;
        else { state.target=id; if(id.equals(state.rival)) state.rival=null; }
        BoatStudioState.changed(); BoatCam.reset();
    }
    private static void camera() {
        var s=FbSettings.settings();
        float width=ImGui.getContentRegionAvailX();
        boolean beside=width>=430;
        float libraryWidth=beside?Math.max(160,width*.43f):width;
        float height=Math.max(1,ImGui.getContentRegionAvailY());
        boolean library=ImGui.beginChild("##CameraModeLibrary",libraryWidth,beside?height:Math.max(110,height*.45f),false);
        try {
            if(library) {
                ImGui.textUnformatted("Shot library");
                ImGui.setNextItemWidth(-1);
                if(ImGui.beginCombo("##CameraShotGroup",shotGroup)) {
                    try { for(String group:CameraModesCatalog.GROUPS) if(ImGui.selectable(group,shotGroup.equals(group))) shotGroup=group; }
                    finally { ImGui.endCombo(); }
                }
                ImGui.setNextItemWidth(-1);
                ImGui.inputTextWithHint("##CameraShotSearch","Search shots...",SEARCH);
                ImGui.textDisabled("Drag a shot onto an actor");
                int matches=0;
                int columns=libraryWidth>=190?2:1,index=0;
                float tile=Math.max(1,(ImGui.getContentRegionAvailX()-(columns-1)*6)/columns);
                for(var entry:CameraModesCatalog.ENTRIES) {
                    if(!shotGroup.equals("All shots") && !shotGroup.equals(CameraModesCatalog.group(entry.mode()))) continue;
                    String query=SEARCH.get().trim().toLowerCase(java.util.Locale.ROOT);
                    if(!query.isEmpty() && !(entry.name()+" "+entry.description()+" "+CameraModesCatalog.group(entry.mode())).toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                    matches++;
                    if(index++%columns!=0) ImGui.sameLine();
                    ImGui.pushID(entry.id()); ImGui.beginGroup();
                    try {
                        var tuning=CameraModes.tuning(entry.mode());
                        if(CameraModesPreview.draw("##ModeTile",tuning,tile,tile*9/16,false)) { CameraModes.select(entry.mode()); }
                        if(s.boatCamMode==entry.mode()) ImGui.getWindowDrawList().addRect(ImGui.getItemRectMinX(),ImGui.getItemRectMinY(),ImGui.getItemRectMaxX(),ImGui.getItemRectMaxY(),0xffb7d567,6);
                        dragSource(entry);
                        if(SkinPickerChrome.button(entry.name(),s.boatCamMode==entry.mode(),tile)) { CameraModes.select(entry.mode()); }
                        dragSource(entry); ImGui.setItemTooltip(entry.description());
                    } finally { ImGui.endGroup(); ImGui.popID(); }
                }
                if(matches==0) ImGui.textWrapped("No shots match. Try another group or search.");
            }
        } finally { ImGui.endChild(); }
        if(beside) ImGui.sameLine();
        boolean inspector=ImGui.beginChild("##CameraModeInspector",beside?0:width,beside?height:0,false);
        try {
            if(inspector) {
                var entry=CameraModesCatalog.ENTRIES.get(Math.max(0,Math.min(CameraModesCatalog.ENTRIES.size()-1,s.boatCamMode)));
                ImGui.textDisabled(CameraModesCatalog.group(entry.mode()).toUpperCase(java.util.Locale.ROOT));
                ImGui.textUnformatted(entry.name());
                float previewWidth=ImGui.getContentRegionAvailX();
                CameraModesPreview.draw("##CameraModePreview",BoatCam.snapshot(),previewWidth,Math.max(140,previewWidth*9/16),true);
                dragSource(entry);
                ImGui.textDisabled(CameraModePreviewScene.ice(entry.mode())?"Ice circuit / boat and rider":CameraModePreviewScene.horse(entry.mode())?"Trail / horse and rider":"Terrain / walking subject");
                ImGui.textWrapped(entry.description());
                section("Subjects");
                targetPicker("Subject",false);
                if(s.boatCamMode==7) targetPicker("Second subject",true);
                var selected=pdx.dev.fbactors.ui.ActorTrack.selected();
                primary("Add to selected actor",selected==null,()->CameraModes.attach(entry.id(),selected));
                ImGui.textWrapped("Drag the preview or a mode card onto an actor. The camera follows it for the take.");
                section("Camera settings");
                cameraControls();
                if(SkinPickerChrome.button("Reset recommended framing",false,ImGui.getContentRegionAvailX())) CameraModes.select(s.boatCamMode);
                if(ImGui.collapsingHeader("Live follow")) {
                    if(BoatStudioState.get().timeline.active(BoatSubjects.clock())!=null) ImGui.textWrapped("A timeline clip controls the camera here.");
                    else primary(BoatCam.enabled()?"Stop following":"Start following",BoatStudioState.get().target==null || s.boatCamMode==7 && BoatStudioState.get().rival==null,BoatCam::toggle);
                    ImGui.textWrapped(BoatCam.status());
                }
            }
        } finally { ImGui.endChild(); }
    }
    private static void dragSource(CameraModesCatalog.Entry entry) {
        if(ImGui.beginDragDropSource()) {
            try {
                ImGui.setDragDropPayload(CameraModes.PAYLOAD,entry.id());
                ImGui.textUnformatted(entry.name());
                ImGui.textUnformatted("Drop onto an actor track");
            } finally { ImGui.endDragDropSource(); }
        }
    }
    public static void cameraControls() {
        var s=FbSettings.settings();
        ImGui.textDisabled("FRAMING");
        if(s.boatCamMode!=3) {
            s.boatCamDistance=slider("Distance",s.boatCamDistance,.5f,24);
            s.boatCamHeight=slider("Height",s.boatCamHeight,.3f,12);
        }
        s.boatCamFov=slider("Field of view",s.boatCamFov,30,110);
        if(s.boatCamMode==1 || s.boatCamMode==8 || s.boatCamMode==16 || s.boatCamMode>=17 && s.boatCamMode<=19) s.boatCamSide=slider("Side offset",s.boatCamSide,-12,12);
        if((s.boatCamMode>=10 && s.boatCamMode<=13) || s.boatCamMode==21 || s.boatCamMode==22) ImGui.textWrapped("Motion eases over the first 8 seconds of the clip, then holds. Distance and height set the framing range.");
        if(s.boatCamMode==23) ImGui.textWrapped("Rises over eight seconds, then keeps orbiting. Orbit speed controls direction and pace.");
        section("Motion");
        s.boatCamSmoothing=slider("Smoothing",s.boatCamSmoothing,0,1);
        if(s.boatCamMode==2 || s.boatCamMode==23) s.boatCamOrbitSpeed=slider("Orbit speed",s.boatCamOrbitSpeed,-90,90);
        if((s.boatCamMode<7 && s.boatCamMode!=3 && s.boatCamMode!=5 || s.boatCamMode>=14 && s.boatCamMode<=20) && ImGui.collapsingHeader("Advanced motion")) {
            if(s.boatCamMode==0 || s.boatCamMode==1 || s.boatCamMode==6 || s.boatCamMode==14 || s.boatCamMode==17 || s.boatCamMode==19 || s.boatCamMode==20)
                s.boatCamLookAhead=slider("Look ahead",s.boatCamLookAhead,0,12);
            if(s.boatCamMode!=19) s.boatCamSpeedFov=slider("Speed zoom",s.boatCamSpeedFov,0,20);
            if(s.boatCamMode==0 || s.boatCamMode==1 || s.boatCamMode==4 || s.boatCamMode==6 || s.boatCamMode==14 || s.boatCamMode==17) s.boatCamBank=slider("Turn banking",s.boatCamBank,0,30);
        }
        section("Obstacles");
        if(ImGui.checkbox("Avoid walls and ceilings",s.boatCamCollision)) { s.boatCamCollision=!s.boatCamCollision; BoatCam.reset(); }
        if(s.boatCamMode==7) ImGui.textDisabled("A wall can force the camera closer and crop a subject.");
    }
    private static float slider(String label,float value,float min,float max) {
        ImGui.textUnformatted(label);
        float[] v={value}; ImGui.setNextItemWidth(-1);
        if(ImGui.sliderFloat("##"+label,v,min,max,"%.2f")) { BoatCam.reset(); return v[0]; }
        return value;
    }
    private static void section(String title) {
        ImGui.spacing(); ImGui.separator(); ImGui.spacing(); ImGui.textUnformatted(title);
    }
    private static void useRange() {
        var replay=Flashback.getReplayServer(); var state=EditorStateManager.getCurrent();
        if(replay==null || state==null) { from=to=0; return; }
        long stamp=state.acquireRead();
        try { var scene=state.getCurrentScene(stamp); from=Math.max(0,scene.exportStartTicks); to=scene.exportEndTicks<0?replay.getTotalReplayTicks():scene.exportEndTicks; }
        finally { state.release(stamp); }
    }
    private static void timelineControls() {
        var state = BoatStudioState.get();
        var timeline = state.timeline;
        ImGui.textWrapped("Camera modes clips activate at the playhead, including during export. Each clip stores the current Camera shot, tuning and subjects.");
        ImGui.beginDisabled(timeline.locked);
        if (ImGui.checkbox("Enable camera modes track", timeline.enabled)) { BoatEdits.edit(() -> timeline.enabled = !timeline.enabled); }
        ImGui.endDisabled();
        if(ImGui.checkbox("Lock camera modes track",timeline.locked)) BoatEdits.edit(() -> timeline.locked=!timeline.locked);
        ImGui.beginDisabled(timeline.locked);
        if(SkinPickerChrome.button("Use export In / Out range",false,ImGui.getContentRegionAvailX())) useRange();
        var start=new imgui.moulberry90.type.ImFloat(from/20f); var end=new imgui.moulberry90.type.ImFloat(to/20f);
        ImGui.setNextItemWidth(100); if(ImGui.inputFloat("Start (seconds)",start)) from=Math.max(0,Math.min(99_999_999,Math.round(start.get()*20)));
        ImGui.setNextItemWidth(100); if(ImGui.inputFloat("End (seconds)",end)) to=Math.max(0,Math.min(100_000_000,Math.round(end.get()*20)));
        primary("Add camera mode clip",timeline.clips.size()>=256 || to<=from || state.target==null || FbSettings.settings().boatCamMode==7 && state.rival==null,()-> {
            var clip = new BoatTimeline.Clip(); clip.start=from; clip.end=to;
            clip.target=state.target; clip.rival=state.rival; clip.settings=BoatCam.snapshot();
            BoatEdits.edit(() -> { timeline.clips.add(clip); BoatEdits.selected=clip.id; }); BoatCam.stop();
        });
        ImGui.textDisabled("Drag clips or edges in the Camera modes timeline row.");
        BoatTimeline.Clip remove = null;
        for (var clip : timeline.clips.stream().sorted(java.util.Comparator.comparing(c -> !c.id.equals(BoatEdits.selected))).toList()) {
            ImGui.pushID(clip.id.toString());
            ImGui.separator();
            if(clip.id.equals(BoatEdits.selected)) ImGui.textDisabled("SELECTED CLIP");
            ImGui.textUnformatted(String.format(java.util.Locale.ROOT,"%.2fs - %.2fs / %s", clip.start/20f, clip.end/20f, BoatCam.MODES[Math.max(0,Math.min(BoatCam.MODES.length-1,clip.settings.mode()))]));
            if(ImGui.checkbox("Enabled",clip.enabled)) { BoatEdits.edit(() -> clip.enabled=!clip.enabled); }
            if(ImGui.smallButton("Use entered range" ) && to>from) { BoatEdits.edit(() -> { clip.start=from; clip.end=to; }); }
            if(ImGui.smallButton("Use current mode and subjects") && state.target!=null && (FbSettings.settings().boatCamMode!=7 || state.rival!=null)) {
                BoatEdits.edit(() -> { clip.settings=BoatCam.snapshot(); clip.target=state.target; clip.rival=state.rival; });
            }
            if(ImGui.smallButton("Delete clip")) remove=clip;
            ImGui.popID();
        }
        if(remove!=null) { var removed=remove; BoatEdits.edit(() -> timeline.clips.remove(removed)); }
        ImGui.endDisabled();
    }
    public static void handling() {
        var s=FbSettings.settings();
        ImGui.textWrapped("Change how simulated actor boats respond. Seek back to restart the take after changing handling.");
        ImGui.textDisabled("MOVEMENT");
        s.boatSpeed=slider("Propulsion",s.boatSpeed,.1f,3);
        s.boatSteering=slider("Steering",s.boatSteering,.1f,3);
        s.boatGrip=slider("Lateral grip",s.boatGrip,0,2);
        s.boatBounce=slider("Wall bounce",s.boatBounce,0,1);
        if(SkinPickerChrome.button("Reset vanilla handling",false,ImGui.getContentRegionAvailX())) { s.boatSpeed=1;s.boatSteering=1;s.boatGrip=1;s.boatBounce=0; }
        section("Animation and reactions");
        if(ImGui.checkbox("Animate paddles",s.boatPaddles)) s.boatPaddles=!s.boatPaddles;
        if(ImGui.checkbox("Smooth paddle strokes",s.smoothBoatPaddles)) s.smoothBoatPaddles=!s.smoothBoatPaddles;
        if(ImGui.checkbox("Animate older takes",s.legacyBoatPaddles)) s.legacyBoatPaddles=!s.legacyBoatPaddles;
        if(ImGui.checkbox("React to boat collisions",s.boatCollisionResponse)) s.boatCollisionResponse=!s.boatCollisionResponse;
    }
    private static void primary(String label,boolean disabled,Runnable action) {
        ImGui.pushStyleColor(ImGuiCol.Button,0xFFB7D567);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered,0xFFCAE985);
        ImGui.pushStyleColor(ImGuiCol.Text,0xFF1D2419);
        ImGui.beginDisabled(disabled);
        try { if(ImGui.button(label,Math.max(1,ImGui.getContentRegionAvailX()),28)) action.run(); }
        finally { ImGui.endDisabled(); ImGui.popStyleColor(3); }
    }
}
