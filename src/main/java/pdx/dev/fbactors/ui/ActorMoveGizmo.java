package pdx.dev.fbactors.ui;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.flag.ImGuiStyleVar;
import net.minecraft.world.phys.Vec3;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorPathTranslation;
import pdx.dev.fbactors.undo.UndoStack;
import java.util.List;
import java.util.UUID;

public final class ActorMoveGizmo {
    private static final int FLAGS = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove
        | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoNav
        | ImGuiWindowFlags.NoFocusOnAppearing;
    private static final int[] COLORS = {0xff5555ee, 0xff66dd66, 0xffee9955};
    private static Actor dragging;
    private static UUID selected;
    private static final float[][] handleCenters = new float[3][];
    private static List<ActorFrame> before, after;
    private static int axis;
    private static float mouseX, mouseY, directionX, directionY, pixelsPerBlock;
    private ActorMoveGizmo() {}

    public static boolean canEdit() {
        var replay=Flashback.getReplayServer();
        return replay!=null && replay.replayPaused && !ActorMode.isActive();
    }
    public static void select(Actor actor) { finish(); selected=actor.take.id; clearHandles(); }
    public static void clearSelection() { finish(); selected=null; clearHandles(); }
    private static void clearHandles() { java.util.Arrays.fill(handleCenters,null); }
    public static boolean consumesInput() {
        if(dragging!=null)return true;
        if(ImGui.isPopupOpen("",128|256))return false;
        float x=ImGui.getMousePosX(), y=ImGui.getMousePosY();
        for(float[] center:handleCenters)
            if(center!=null && Math.abs(x-center[0])<=16 && Math.abs(y-center[1])<=16)return true;
        return false;
    }

    public static void render() {
        var replay = Flashback.getReplayServer();
        if (!canEdit()) { clearSelection(); return; }
        Actor actor = dragging != null ? dragging : FlashbackActors.ACTORS.byTakeId(selected);
        if (actor == null || actor.take.locked || actor.clone() == null) { clearSelection(); return; }
        clearHandles();

        if(ImGui.isPopupOpen("",128|256))return;
        Vec3 anchor = actor.clone().position().add(0, 0.1, 0);
        float[] origin = Compat.projectActorPoint(anchor);
        if (origin == null) return;
        for (int i = 0; i < 3; i++) {
            float[] tip = Compat.projectActorPoint(anchor.add(i==0?1:0, i==1?1:0, i==2?1:0));
            if (tip == null) continue;
            float dx=tip[0]-origin[0], dy=tip[1]-origin[1];
            float length=(float)Math.sqrt(dx*dx+dy*dy);
            if (length < 8) continue;
            float displayLength = Math.max(48, Math.min(90, length));
            float x=origin[0]+dx/length*displayLength, y=origin[1]+dy/length*displayLength;
            handleCenters[i]=new float[]{x,y};
            var draw = ImGui.getForegroundDrawList();
            draw.addLine(origin[0], origin[1], x, y, COLORS[i], 3);
            draw.addCircleFilled(x, y, 9, COLORS[i]);
            draw.addText(x-4,y-7,0xffffffff,new String[]{"X","Y","Z"}[i]);
            ImGui.setNextWindowPos(x-16,y-16);
            ImGui.setNextWindowSize(32,32);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,0,0);
            ImGui.begin("##actor-move-axis-"+i, FLAGS);
            try {
                ImGui.invisibleButton("##drag",32,32);
                if (ImGui.isItemHovered()) ImGui.setTooltip("Drag "+new String[]{"X","Y","Z"}[i]+" to move actor path. World edits stay at their authored positions.");
                if (ImGui.isItemActivated()) {
                    dragging=actor; axis=i; before=List.copyOf(actor.take.frames); after=before;
                    mouseX=ImGui.getMousePosX(); mouseY=ImGui.getMousePosY();
                    directionX=dx/length; directionY=dy/length; pixelsPerBlock=length;
                }
            } finally { ImGui.end(); ImGui.popStyleVar(); }
        }
        if (dragging != null) {
            if (!ImGui.isMouseDown(0)) { finish(); return; }
            double distance=((ImGui.getMousePosX()-mouseX)*directionX+(ImGui.getMousePosY()-mouseY)*directionY)/pixelsPerBlock;
            after=ActorPathTranslation.translate(before,axis==0?distance:0,axis==1?distance:0,axis==2?distance:0);
            apply(dragging, after, false);
        }
    }

    private static void apply(Actor actor, List<ActorFrame> frames, boolean save) {
        var replay=Flashback.getReplayServer();
        if (replay == null) return;
        replay.execute(() -> {
            if(frames.isEmpty() || actor.take.frames.isEmpty())return;
            ActorFrame old=actor.take.frames.getFirst(), moved=frames.getFirst();
            double x=moved.x()-old.x(),y=moved.y()-old.y(),z=moved.z()-old.z();
            actor.take.frames.clear(); actor.take.frames.addAll(frames);
            actor.take.vehicles.replaceAll(v -> v.translated(x,y,z));
            var clone=actor.clone();
            if(clone!=null) {
                Compat.snapTo(clone,clone.getX()+x,clone.getY()+y,clone.getZ()+z,clone.getYRot(),clone.getXRot());
                if(clone.getVehicle()!=null) {
                    var vehicle=clone.getVehicle();
                    Compat.snapTo(vehicle,vehicle.getX()+x,vehicle.getY()+y,vehicle.getZ()+z,vehicle.getYRot(),vehicle.getXRot());
                }
            }
            actor.combat().translate(x,y,z);
            if (save) FlashbackActors.save(actor.take);
        });
    }
    private static void finish() {
        if (dragging == null) return;
        Actor actor=dragging; List<ActorFrame> original=before, moved=after;
        dragging=null;
        if (!original.equals(moved)) {
            apply(actor,moved,true);
            UndoStack.pushEdit(() -> apply(actor,original,true), () -> apply(actor,moved,true));
        }
    }
}
