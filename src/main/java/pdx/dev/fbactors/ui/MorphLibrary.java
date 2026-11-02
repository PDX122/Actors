package pdx.dev.fbactors.ui;

import com.moulberry.flashback.Flashback;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import java.util.UUID;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.morph.*;
import pdx.dev.fbactors.settings.FbSettings;

public final class MorphLibrary {
    private static final ImBoolean OPEN=new ImBoolean(false);
    private static String body="cat",pose="auto";
    private static UUID preferredActor;
    private static final ImString SEARCH=new ImString(128);
    private static String category="";
    private static int page;
    private static boolean playPreview=true;
    private static final int PAGE_SIZE=4;
    private MorphLibrary() {}
    public static boolean isOpen() { return OPEN.get(); }
    public static void open() {
        var actor=ActorTrack.selected();
        var selection=actor==null?null:Morph.selectionFor(actor.take.id);
        open(selection!=null && selection.isMorphed()?selection.morphId():body,actor);
    }
    public static void open(String requested,Actor actor) {
        String normalized=requested==null?"cat":requested.replaceFirst("^minecraft:","");
        body=Morph.registry().has(requested)?requested:Morph.registry().has(normalized)?normalized:"cat";
        SEARCH.set(""); category=""; page=0;
        var entries=Morph.registry().all();
        for(int i=0;i<entries.size();i++) if(entries.get(i).id().equals(body)) { page=i/PAGE_SIZE; break; }
        preferredActor=actor==null?null:actor.take.id;
        var current=actor==null?null:Morph.selectionFor(actor.take.id);
        pose=current!=null && body.equals(current.morphId())?current.pose():"auto";
        MorphTracks.notice="";
        OPEN.set(true);
    }
    public static void render() {
        if(!OPEN.get()) return;
        var viewport=ImGui.getMainViewport();
        float width=Math.max(1,viewport.getWorkSizeX()-24),height=Math.max(1,viewport.getWorkSizeY()-24);
        ImGui.setNextWindowSize(Math.min(760,width),Math.min(600,height),ImGuiCond.Appearing);
        ImGui.setNextWindowSizeConstraints(Math.min(340,width),Math.min(360,height),width,height);
        SkinPickerChrome.push();
        boolean visible=ImGui.begin("Morph library###FbActorsPoses",OPEN);
        try {
            if(!visible) return;
            if(Flashback.getReplayServer()==null || !FbSettings.morphEnabled()) {
                ImGui.textWrapped("Open a replay and enable morphs in Settings to browse morphs."); return;
            }
            boolean content=ImGui.beginChild("##MorphLibraryContents",0,Math.max(1,ImGui.getContentRegionAvailY()-44),false);
            try { if(content) contents(); } finally { ImGui.endChild(); }
            ImGui.separator();
            if(SkinPickerChrome.button("Done",true,80)) OPEN.set(false);
        } finally { ImGui.end(); SkinPickerChrome.pop(); }
    }
    private static void contents() {
        ImGui.textWrapped("Browse bodies, choose a pose, then drag a morph onto an actor or its Morph lane.");
        float width=ImGui.getContentRegionAvailX(),filterWidth=Math.min(160,width*.35f);
        ImGui.setNextItemWidth(Math.max(1,width-filterWidth-8));
        if(ImGui.inputTextWithHint("##MorphLibrarySearch","Search morphs...",SEARCH)) page=0;
        ImGui.sameLine(); ImGui.setNextItemWidth(filterWidth);
        if(ImGui.beginCombo("##MorphLibraryCategory",category.isEmpty()?"All morphs":title(category))) {
            try {
                if(ImGui.selectable("All morphs",category.isEmpty())) { category=""; page=0; }
                for(String entry:MorphSearch.knownCategories())
                    if(ImGui.selectable(title(entry),category.equals(entry))) { category=entry; page=0; }
            } finally { ImGui.endCombo(); }
        }
        int count=MorphSearch.resultCount(Morph.registry(),SEARCH.get(),category);
        int pages=Math.max(1,(count+PAGE_SIZE-1)/PAGE_SIZE);
        page=Math.max(0,Math.min(page,pages-1));
        ImGui.beginDisabled(page==0);
        try { if(ImGui.button("Previous")) page--; } finally { ImGui.endDisabled(); }
        ImGui.sameLine(); ImGui.textUnformatted("Page "+(page+1)+" / "+pages+"  |  "+count+" morphs");
        ImGui.sameLine(); ImGui.beginDisabled(page>=pages-1);
        try { if(ImGui.button("Next")) page++; } finally { ImGui.endDisabled(); }
        var rows=MorphSearch.search(Morph.registry(),SEARCH.get(),category,page,PAGE_SIZE);
        boolean beside=width>=620;
        float leftWidth=beside?width*.60f:width;
        float height=beside?Math.max(320,ImGui.getContentRegionAvailY()):320;
        boolean cards=ImGui.beginChild("##MorphBodyCards",leftWidth,height,true);
        try {
            if(cards) {
                if(rows.isEmpty()) ImGui.textWrapped("No morphs match. Try another name or category.");
                float available=ImGui.getContentRegionAvailX(),gap=ImGui.getStyle().getItemSpacingX();
                int columns=available>=300?2:1,index=0;
                float cardWidth=Math.max(1,(available-gap*(columns-1))/columns);
                for(var definition:rows) {
                    if(index++%columns!=0) ImGui.sameLine();
                    if(card(definition,cardWidth)) { body=definition.id(); pose="auto"; }
                }
            }
        } finally { ImGui.endChild(); }
        if(beside) ImGui.sameLine();
        boolean details=ImGui.beginChild("##MorphLibraryInspector",beside?0:width,beside?height:390,true);
        try { if(details) inspector(); } finally { ImGui.endChild(); }
    }
    private static void inspector() {
        var definition=Morph.registry().byId(body);
        if(definition==null) { ImGui.textWrapped("Select a morph card."); return; }
        ImGui.textWrapped(definition.displayName());
        ImGui.textDisabled(title(MorphSearch.categoryOf(definition)));
        pose=MorphPoses.normalize(body,pose);
        var preview=definition.isPlayer()?null:MorphPreviews.poseCard(body,pose,playPreview);
        if(definition.isPlayer()) {
            ImGui.textWrapped("Restore the actor's recorded player body and skin.");
        } else {
            SkinPickerChrome.stage(preview==null?0:preview.texture(),ImGui.getContentRegionAvailX(),120,
                MorphPreviews.poseCardFailed(body,pose)?"Preview unavailable":"Loading preview...");
            dragSource(new MorphLibraryPresets.Preset(body,pose),preview);
        }
        var choices=MorphPoses.choices(body);
        if(choices.size()>1) {
            ImGui.textUnformatted("Animation / pose"); ImGui.setNextItemWidth(-1);
            if(ImGui.beginCombo("##MorphLibraryPose",MorphPoses.label(pose))) {
                try { for(String choice:choices) if(ImGui.selectable(MorphPoses.label(choice),pose.equals(choice))) pose=choice; }
                finally { ImGui.endCombo(); }
            }
            ImGui.textWrapped(MorphPoses.description(pose));
            if(ImGui.checkbox("Play preview",playPreview)) playPreview=!playPreview;
        } else ImGui.textWrapped("Uses recorded movement and native animation.");
        ImGui.spacing(); ImGui.separator();
        ImGui.textWrapped("Drag to create a clip of up to 5 seconds. Move or trim it in the Morph lane.");
        if(!MorphTracks.notice.isBlank()) ImGui.textWrapped(MorphTracks.notice);
        var actor=preferredActor==null?ActorTrack.selected():FlashbackActors.ACTORS.byUuid(preferredActor);
        String actorName=actor==null?"selected actor":actor.displayName();
        ImGui.beginDisabled(actor==null || actor.take.locked);
        try {
            if(SkinPickerChrome.button("Apply to "+actorName,false,ImGui.getContentRegionAvailX())) {
                if(definition.isPlayer()) Morph.store().clear(actor.take.id);
                else {
                    var current=Morph.selectionFor(actor.take.id);
                    var selected=(current==null?MorphSelection.of(actor.take.id,body):current.withMorph(body)).withPose(pose);
                    Morph.store().set(actor.take.id,selected);
                }
                Morph.save(); Morph.refreshVisible();
                MorphTracks.notice=name(body)+" applied to "+actor.displayName()+".";
                if(MorphTracks.timeline().active(actor.take.id,pdx.dev.fbactors.camera.BoatSubjects.clock())!=null)
                    MorphTracks.notice+=" A Morph clip overrides this default at the playhead.";
            }
            if(SkinPickerChrome.button("Add clip at playhead",false,ImGui.getContentRegionAvailX()))
                MorphTracks.drop(new MorphLibraryPresets.Preset(body,pose).payload(),actor,(int)Math.floor(pdx.dev.fbactors.camera.BoatSubjects.clock()));
        } finally { ImGui.endDisabled(); }
        if(actor==null) ImGui.textWrapped("Select an actor to use Apply or Add clip. Dragging chooses the destination actor.");
        else if(actor.take.locked) ImGui.textWrapped("Unlock this actor's track to apply or add a morph.");
    }
    private static boolean card(MorphDefinition definition,float width) {
        String id=definition.id();
        var preset=new MorphLibraryPresets.Preset(id,body.equals(id)?pose:"auto");
        boolean clicked=ImGui.invisibleButton("##MorphCard"+id,width,144);
        if(!ImGui.isItemVisible()) return clicked;
        boolean selected=body.equals(id),hovered=ImGui.isItemHovered();
        float x=ImGui.getItemRectMinX(),y=ImGui.getItemRectMinY();
        var draw=ImGui.getWindowDrawList();
        draw.addRectFilled(x,y,x+width,y+144,selected?0xff514436:hovered?0xff3d3730:0xff292521,10);
        draw.addRect(x,y,x+width,y+144,selected?0xffb7d567:0xff51483d,10);
        var preview=definition.isPlayer()?null:MorphPreviews.poseCard(id,preset.pose());
        float size=Math.max(1,Math.min(width-16,108));
        if(definition.isPlayer()) PhosphorIcons.draw("user-focus",x+(width-64)/2,y+24,64,0xfff0ebe7);
        else if(preview!=null) draw.addImage(preview.texture(),x+(width-size)/2,y+6,x+(width+size)/2,y+6+size,0,1,1,0);
        else draw.addText(x+8,y+42,0xffa49c94,MorphPreviews.poseCardFailed(id,preset.pose())?"Preview unavailable":"Preview loading...");
        String label=definition.displayName();
        while(label.length()>1 && ImGui.calcTextSizeX(label)>width-12) label=label.substring(0,label.length()-1);
        draw.addText(x+Math.max(6,(width-ImGui.calcTextSizeX(label))/2),y+119,0xfff0ebe7,label);
        dragSource(preset,preview);
        ImGui.setItemTooltip(definition.displayName()+" / "+MorphPoses.label(preset.pose())+". Drag onto an actor or Morph lane.");
        return clicked;
    }
    private static void dragSource(MorphLibraryPresets.Preset preset,MorphThumbnail preview) {
        if(ImGui.beginDragDropSource()) {
            try {
                body=preset.body(); pose=preset.pose();
                ImGui.setDragDropPayload(MorphLibraryPresets.PAYLOAD,preset.payload());
                ImGui.textUnformatted(name(body)+" / "+MorphPoses.label(pose));
                if(preview!=null) ImGui.image(preview.texture(),96,96,0,1,1,0);
                ImGui.textUnformatted("Drop onto an actor or Morph lane");
            } finally { ImGui.endDragDropSource(); }
        }
    }
    private static String name(String id) {
        var definition=Morph.registry().byId(id);
        return definition==null?id:definition.displayName();
    }
    private static String title(String text) { return text.isEmpty()?text:Character.toUpperCase(text.charAt(0))+text.substring(1); }

    public static void acceptDrop(TimelineGeometry geometry,UUID actor,int cursorTick) {
        if(actor==null) return;
        String payload=ImGui.acceptDragDropPayload(MorphLibraryPresets.PAYLOAD,String.class);
        if(payload==null) return;
        int tick=ImGui.getMousePosX()<geometry.laneLeft()?cursorTick:geometry.tickAt(ImGui.getMousePosX());
        MorphTracks.drop(payload,FlashbackActors.ACTORS.byUuid(actor),tick);
    }
    public static void dropTarget(TimelineGeometry geometry,float y,float height,UUID actor,int cursorTick) {
        float top=Math.max(y,geometry.clipTop()),bottom=Math.min(y+height,geometry.clipBottom());
        if(bottom<=top) return;
        float x=ImGui.getCursorScreenPosX(),oldY=ImGui.getCursorScreenPosY();
        ImGui.setCursorScreenPos(geometry.x(),top);
        try {
            ImGui.dummy(Math.max(1,geometry.width()),bottom-top);
            if(ImGui.beginDragDropTarget()) {
                try { acceptDrop(geometry,actor,cursorTick); } finally { ImGui.endDragDropTarget(); }
            }
        } finally { ImGui.setCursorScreenPos(x,oldY); }
    }
}
