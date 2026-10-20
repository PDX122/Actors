package pdx.dev.fbactors.director;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.type.ImBoolean;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.take.CameraHeight;
import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.CameraMove;
import pdx.dev.fbactors.take.DirectorConfig;
import pdx.dev.fbactors.take.DirectorShot;
import pdx.dev.fbactors.ui.ActorHeads;
import pdx.dev.fbactors.ui.ActorSkinBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public final class DirectorShotsWindow {

    private static final float PANEL_WIDTH = 420.0f;
    private static final float PANEL_HEIGHT = 600.0f;

    private static boolean open;


    private static final ImBoolean OPEN = new ImBoolean();


    static final String AUTO_LABEL = "Auto";


    private static final List<DirectorShot> shots = new ArrayList<>();


    private static final DirectorMonitors monitors = new DirectorMonitors();


    static final int MAX_MONITORS = DirectorMonitors.MAX_MONITORS;


    @Nullable
    private static String status;

    private DirectorShotsWindow() {
    }


    static List<DirectorShot> shots() {
        return List.copyOf(shots);
    }


    static void setShots(List<DirectorShot> next) {
        shots.clear();
        if (next != null) {
            shots.addAll(next);
        }
    }


    public static void open() {
        open = true;
        OPEN.set(true);
        status = null;
    }


    public static void close() {
        open = false;
        OPEN.set(false);
    }

    public static boolean isOpen() {
        return open;
    }


    public static void render() {
        if (!open) {
            return;
        }
        ImGui.setNextWindowSize(PANEL_WIDTH, PANEL_HEIGHT, ImGuiCond.FirstUseEver);
        OPEN.set(open);
        if (!ImGui.begin("Shots", OPEN)) {
            ImGui.end();
            return;
        }
        open = OPEN.get();

        renderShots();
        ImGui.separator();
        renderMonitors();
        if (status != null) {
            ImGui.separator();
            ImGui.textDisabled(status);
        }
        ImGui.end();
        open = OPEN.get();
    }


    private static void renderShots() {
        ImGui.text("Shot list");
        if (shots.isEmpty()) {
            ImGui.textDisabled("Empty: moments are auto-detected. Add shots to direct explicitly.");
        }
        List<DirectorSubjects.Subject> subjects = DirectorSubjects.subjects();
        for (int i = 0; i < shots.size(); i++) {
            ImGui.pushID(1000 + i);
            renderShotRow(i, subjects);
            ImGui.popID();
        }
        if (ImGui.button("Add shot##DirectorShotAdd")) {
            int start = Math.max(0, FlashbackActors.replayTick());
            UUID subject = DirectorMenu.primaryOrFirst(subjects);
            if (subject == null) {
                status = "Select an actor or a player first.";
            } else {
                shots.add(DirectorShot.of(subject, start));
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Append a shot at the playhead. Empty list = auto.");
        }
    }

    private static void renderShotRow(int index, List<DirectorSubjects.Subject> subjects) {
        DirectorShot shot = shots.get(index);
        ImGui.text("Shot " + (index + 1));

        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.beginCombo("##DirectorShotSubject",
                DirectorSubjects.subjectName(shot.subjectId(), subjects))) {
            for (DirectorSubjects.Subject subject : subjects) {
                if (ImGui.menuItem(subject.name(), subject.id().equals(shot.subjectId()))) {
                    shots.set(index, new DirectorShot(subject.id(), shot.move(),
                        shot.shotType(), shot.height(), shot.holdTicks(), shot.startTick()));
                    shot = shots.get(index);
                }
            }
            ImGui.endCombo();
        }

        float third = (PANEL_WIDTH - 40.0f - 16.0f) / 3.0f;
        ImGui.setNextItemWidth(third);
        if (ImGui.beginCombo("##DirectorShotMove",
                shot.move() == null ? AUTO_LABEL : shot.move().label())) {
            if (ImGui.menuItem(AUTO_LABEL, shot.move() == null)) {
                shots.set(index, new DirectorShot(shot.subjectId(), null,
                    shot.shotType(), shot.height(), shot.holdTicks(), shot.startTick()));
                shot = shots.get(index);
            }
            for (CameraMove candidate : CameraMove.values()) {
                if (ImGui.menuItem(candidate.label(), candidate == shot.move())) {
                    shots.set(index, new DirectorShot(shot.subjectId(), candidate,
                        shot.shotType(), shot.height(), shot.holdTicks(), shot.startTick()));
                    shot = shots.get(index);
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(third);
        if (ImGui.beginCombo("##DirectorShotFrame",
                shot.shotType() == null ? AUTO_LABEL : DirectorMenu.shotName(shot.shotType()))) {
            if (ImGui.menuItem(AUTO_LABEL, shot.shotType() == null)) {
                shots.set(index, new DirectorShot(shot.subjectId(), shot.move(), null,
                    shot.height(), shot.holdTicks(), shot.startTick()));
                shot = shots.get(index);
            }
            for (CameraKeyframe.ShotType candidate : CameraKeyframe.ShotType.values()) {
                if (ImGui.menuItem(DirectorMenu.shotName(candidate), candidate == shot.shotType())) {
                    shots.set(index, new DirectorShot(shot.subjectId(), shot.move(), candidate,
                        shot.height(), shot.holdTicks(), shot.startTick()));
                    shot = shots.get(index);
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(third);
        if (ImGui.beginCombo("##DirectorShotHeight",
                shot.height() == null ? AUTO_LABEL : shot.height().label())) {
            if (ImGui.menuItem(AUTO_LABEL, shot.height() == null)) {
                shots.set(index, new DirectorShot(shot.subjectId(), shot.move(),
                    shot.shotType(), null, shot.holdTicks(), shot.startTick()));
                shot = shots.get(index);
            }
            for (CameraHeight candidate : CameraHeight.values()) {
                if (ImGui.menuItem(candidate.label(), candidate == shot.height())) {
                    shots.set(index, new DirectorShot(shot.subjectId(), shot.move(),
                        shot.shotType(), candidate, shot.holdTicks(), shot.startTick()));
                    shot = shots.get(index);
                }
            }
            ImGui.endCombo();
        }

        float[] start = {(float) shot.startTick()};
        ImGui.setNextItemWidth(-1.0f);
        ImGui.sliderFloat("At##DirectorShotStart", start, 0.0f, 36000.0f, "%.0f");
        float[] hold = {(float) shot.holdTicks()};
        ImGui.setNextItemWidth(-1.0f);
        ImGui.sliderFloat("For##DirectorShotHold", hold,
            (float) DirectorConfig.MIN_HOLD_TICKS, (float) DirectorConfig.MAX_HOLD_TICKS, "%.0f ticks");
        int newStart = Math.max(0, Math.round(start[0]));
        int newHold = Math.max(DirectorConfig.MIN_HOLD_TICKS, Math.round(hold[0]));
        if (newStart != shot.startTick() || newHold != shot.holdTicks()) {
            shots.set(index, new DirectorShot(shot.subjectId(), shot.move(),
                shot.shotType(), shot.height(), newHold, newStart));
            shot = shots.get(index);
        }

        if (index > 0 && ImGui.smallButton("Up##DirectorShotUp")) {
            shots.set(index, shots.get(index - 1));
            shots.set(index - 1, shot);
        }
        ImGui.sameLine();
        if (index < shots.size() - 1 && ImGui.smallButton("Down##DirectorShotDown")) {
            shots.set(index, shots.get(index + 1));
            shots.set(index + 1, shot);
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Delete##DirectorShotDel")) {
            shots.remove(index);
        }
        ImGui.separator();
    }


    private static void renderMonitors() {
        ImGui.text("Monitors (" + monitors.size() + "/" + MAX_MONITORS + ")");
        ImGui.textDisabled("One panel per actor. View jumps the main camera onto the feed.");
        for (Actor actor : FlashbackActors.ACTORS.actors()) {
            ImGui.pushID(actor.take.id.hashCode());
            boolean onWall = monitors.monitored(actor.take.id);
            String label = onWall ? "[x] " + actor.displayName() : "[ ] " + actor.displayName();
            if (ImGui.smallButton(label + "##DirectorMonitorToggle")) {
                if (onWall) {
                    monitors.unmonitor(actor.take.id);
                } else if (!monitors.tryMonitor(actor.take.id)) {
                    status = "The wall holds five feeds; drop one first.";
                }
            }
            ImGui.popID();
            ImGui.sameLine();
        }
        ImGui.newLine();

        for (UUID id : monitors.ids()) {
            Actor actor = FlashbackActors.ACTORS.byUuid(id);
            if (actor == null) {
                monitors.unmonitor(id);
                continue;
            }
            ImGui.pushID(id.hashCode());
            renderMonitorCard(actor);
            ImGui.popID();
        }
    }

    private static void renderMonitorCard(Actor actor) {
        float height = 64.0f;
        if (ImGui.beginChild("##DirectorMonitor", -1.0f, height, true)) {
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY();
            float head = 40.0f;
            ActorHeads.draw(ImGui.getWindowDrawList(), actor.take.id, x, y, head, 0xFFFFFFFF);
            ImGui.dummy(head, head);
            ImGui.sameLine();
            ImGui.beginChild("##DirectorMonitorText", -1.0f, height - 16.0f, false);
            ImGui.text(actor.displayName());
            int replayTick = FlashbackActors.replayTick();
            if (replayTick >= 0) {
                ImGui.textDisabled("take tick " + actor.takeTickFor(replayTick)
                    + (actor.solo ? " | SOLO" : ""));
            } else {
                ImGui.textDisabled(actor.enabled() ? "ready" : "muted");
            }
            if (ImGui.smallButton("View##DirectorMonitorView")) {
                ActorSkinBridge.command("spectate " + actor.take.id);
            }
            ImGui.sameLine();
            if (ImGui.smallButton((actor.solo ? "Unsolo" : "Solo") + "##DirectorMonitorSolo")) {
                boolean value = !actor.solo;
                for (Actor other : FlashbackActors.ACTORS.actors()) {
                    other.solo = false;
                }
                actor.solo = value;
            }
            ImGui.sameLine();
            if (ImGui.smallButton("Drop##DirectorMonitorDrop")) {
                monitors.unmonitor(actor.take.id);
            }
            ImGui.endChild();
        }
        ImGui.endChild();
    }
}

