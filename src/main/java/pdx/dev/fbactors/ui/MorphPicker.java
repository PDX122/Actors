package pdx.dev.fbactors.ui;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiCol;
import imgui.moulberry90.flag.ImGuiStyleVar;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImFloat;
import imgui.moulberry90.type.ImString;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.morph.*;
import pdx.dev.fbactors.settings.FbSettings;
import java.util.UUID;

public final class MorphPicker {
    private static final ImBoolean OPEN = new ImBoolean(false);
    private static Actor target, pending;
    private static UUID targetReplay, pendingClip;
    private static ImString search = ImGuiHelper.createResizableImString("");
    private static String category = "", preview = "cat", status, draftPose = "auto";
    private static boolean timelinePage, pendingTimeline;
    private static int clipStart, clipEnd;
    private static final float FONT_SCALE = 1.1f;

    private MorphPicker() {}

    public static void open(@Nullable Actor actor) {
        if (actor != null) { pending = actor; pendingTimeline = false; pendingClip = null; }
    }
    public static void openTimeline(Actor actor, UUID clip) {
        pending = actor; pendingTimeline = true; pendingClip = clip;
    }
    public static void close() {
        pending = null; target = null; OPEN.set(false);
    }
    public static boolean isOpen(@Nullable Actor actor) { return target != null && target == actor; }
    public static boolean previewsVisible() { return pending!=null || target!=null && OPEN.get(); }

    private static void begin(Actor actor) {
        target = actor;
        var replay = Flashback.getReplayServer();
        targetReplay = replay == null ? null : replay.getMetadata().replayIdentifier;
        OPEN.set(true); status = null; category = "";
        search = ImGuiHelper.createResizableImString("");
        var current = Morph.selectionFor(actor.take.id);
        selectBody(current != null && current.isMorphed() ? current.morphId() : "cat");
        timelinePage = pendingTimeline;
        clipStart = Math.max(0, Math.min(99_999_999, FlashbackActors.replayTick()));
        clipEnd = Math.min(100_000_000, clipStart + 100);
        if (pendingClip != null) selectClip(pendingClip);
    }

    public static void render() {
        try { renderInner(); }
        catch (Exception | LinkageError error) {
            FlashbackActors.LOGGER.warn("Morph picker failed after releasing its UI scopes", error);
            close();
        }
    }

    private static void renderInner() {
        if (pending != null) { var actor = pending; pending = null; begin(actor); }
        var actor = target;
        var replay = Flashback.getReplayServer();
        if (actor != null && (replay == null || !java.util.Objects.equals(targetReplay, replay.getMetadata().replayIdentifier)
            || !FlashbackActors.ACTORS.actors().contains(actor))) { close(); actor = null; }
        if (actor == null || !OPEN.get()) return;
        var viewport = ImGui.getMainViewport();
        var size = MorphPickerLayout.windowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY());
        float width = size.width(), height = size.height();
        ImGui.setNextWindowSize(width, height, ImGuiCond.Appearing);
        float maxWidth = Math.max(1, viewport.getWorkSizeX() - 24);
        float maxHeight = Math.max(1, viewport.getWorkSizeY() - 24);
        ImGui.setNextWindowSizeConstraints(Math.min(420, width), Math.min(320, height), maxWidth, maxHeight);
        ImGui.setNextWindowPos(viewport.getWorkPosX() + (viewport.getWorkSizeX() - width) / 2,
            viewport.getWorkPosY() + (viewport.getWorkSizeY() - height) / 2, ImGuiCond.Appearing);
        SkinPickerChrome.push();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10, 10);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6, 5);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 7, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowTitleAlign, .5f, .5f);
        ImGui.pushStyleVar(ImGuiStyleVar.SelectableTextAlign, .5f, .5f);
        boolean visible = ImGui.begin("Morphs | " + actor.displayName() + "###ActorMorphPicker", OPEN);
        try {
            ImGui.setWindowFontScale(FONT_SCALE);
            if (visible) contents(actor);
        } finally { ImGui.end(); ImGui.popStyleVar(5); SkinPickerChrome.pop(); }
        CustomMobMenu.render();
        String imported = CustomMobMenu.takeImported();
        if (imported != null) { selectBody(imported); category = "custom"; search.set(""); }
        if (!OPEN.get()) close();
    }

    private static void contents(Actor actor) {
        var current = Morph.selectionFor(actor.take.id);
        var definition = current == null ? null : Morph.registry().byId(current.morphId());
        centered(actor.displayName() + " | " + (definition == null ? "Player" : definition.displayName()), false);
        if (!FbSettings.morphEnabled()) {
            ImGui.textWrapped("Morphs are turned off. Enable them in Actors > Settings."); return;
        }
        float tab = Math.max(1, (ImGui.getContentRegionAvailX() - 8) / 2);
        if (SkinPickerChrome.button("Choose body", !timelinePage, tab)) timelinePage = false;
        ImGui.sameLine();
        if (SkinPickerChrome.button("Morph timeline", timelinePage, tab)) timelinePage = true;
        if (actor.take.locked) centered("This actor is locked. Unlock its track to make changes.", false);
        if (status != null) centered(status, false);
        if (MorphTracks.waitingToSave()) centered("Timeline changes are waiting to save. Retrying...", false);
        ImGui.separator();
        float footer = 42;
        boolean body = ImGui.beginChild("##MorphWorkspace", 0, Math.max(1, ImGui.getContentRegionAvailY() - footer), false);
        try {
            ImGui.setWindowFontScale(1);
            if (body) { if (timelinePage) timeline(actor); else library(actor); }
        } finally { ImGui.endChild(); }
        ImGui.separator();
        footer(actor);
    }

    private static void footer(Actor actor) {
        float width = ImGui.getContentRegionAvailX();
        float closeWidth = 65, resetWidth = 120;
        ImGui.beginDisabled(actor.take.locked);
        try {
            if (!timelinePage) {
                var definition = Morph.registry().byId(preview);
                ImGui.beginDisabled(definition == null);
                try {
                    if (primary("Apply " + bodyName(preview), Math.max(1, width - closeWidth - resetWidth - 16))) applyMorph(actor, definition);
                } finally { ImGui.endDisabled(); }
                ImGui.sameLine();
                if (SkinPickerChrome.button("Reset to player", false, resetWidth)) {
                    Morph.store().clear(actor.take.id); Morph.save(); Morph.refreshVisible();
                    status = actor.displayName() + " now uses its player body.";
                }
                ImGui.sameLine();
            }
        } finally { ImGui.endDisabled(); }
        if (SkinPickerChrome.button("Close", false, closeWidth)) close();
    }

    private static boolean primary(String label, float width) {
        ImGui.pushStyleColor(ImGuiCol.Button, 0xffb7d567);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xffcae985);
        ImGui.pushStyleColor(ImGuiCol.Text, 0xff1d2419);
        try { return ImGui.button(label, Math.max(1, width), 32); }
        finally { ImGui.popStyleColor(3); }
    }

    private static void filters() {
        float width = ImGui.getContentRegionAvailX();
        float categoryWidth = Math.min(150, width * .35f);
        ImGui.setNextItemWidth(Math.max(1, width - categoryWidth - 8));
        ImGui.inputTextWithHint("##MorphSearch", "Search mobs...", search);
        ImGui.sameLine(); ImGui.setNextItemWidth(categoryWidth);
        if (ImGui.beginCombo("##MorphCategory", category.isEmpty() ? "All bodies" : title(category))) {
            try {
                if (ImGui.selectable("All bodies", category.isEmpty())) category = "";
                for (String known : MorphSearch.knownCategories())
                    if (ImGui.selectable(title(known), category.equals(known))) category = known;
            } finally { ImGui.endCombo(); }
        }
    }

    private static void library(Actor actor) {
        filters();
        var layout = MorphPickerLayout.fit(ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(),
            ImGui.getFrameHeight(), ImGui.getStyle().getItemSpacingX(), ImGui.getStyle().getItemSpacingY());
        boolean list = ImGui.beginChild("##MorphLibrary", layout.listWidth(), layout.listHeight(), true);
        try {
            ImGui.setWindowFontScale(1);
            if (list) bodyList(actor);
        } finally { ImGui.endChild(); }
        if (layout.beside()) ImGui.sameLine();
        boolean inspector = ImGui.beginChild("##MorphInspector", layout.inspectorWidth(), layout.inspectorHeight(), true);
        try {
            ImGui.setWindowFontScale(1);
            if (inspector) inspector(actor);
        } finally { ImGui.endChild(); }
    }

    private static void bodyList(Actor actor) {
        if (SkinPickerChrome.button("Import custom model", false, ImGui.getContentRegionAvailX())) CustomMobMenu.open();
        var rows = MorphSearch.search(Morph.registry(), ImGuiHelper.getString(search), category, 0, Integer.MAX_VALUE);
        centered(rows.size() + " bodies", true); ImGui.separator();
        if (rows.isEmpty()) ImGui.textWrapped("No matching bodies. Try another name or category.");
        var current = Morph.selectionFor(actor.take.id);
        for (var morph : rows) {
            String label = morph.displayName() + (current != null && morph.id().equals(current.morphId()) ? "  (current)" : "");
            if (ImGui.selectable(label + "##" + morph.id(), morph.id().equals(preview), 0, 0, Math.max(30, ImGui.getFrameHeight()))) selectBody(morph.id());
        }
    }

    private static void selectBody(String id) {
        preview = id;
        var current = target == null ? null : Morph.selectionFor(target.take.id);
        draftPose = current != null && id.equals(current.morphId()) ? current.pose() : "auto";
    }

    private static void inspector(Actor actor) {
        var definition = Morph.registry().byId(preview);
        if (definition == null) { ImGui.textWrapped("Choose a body on the left."); return; }
        centered(definition.displayName(), false);
        posePicker("Animation / pose", definition.id());
        if(SkinPickerChrome.button("Browse morph library",false,ImGui.getContentRegionAvailX()))
            MorphLibrary.open(definition.id(),actor);
        ImGui.spacing();
        MorphThumbnail image = null;
        try { image = MorphPreviews.get(definition.id(),draftPose); }
        catch (Exception | LinkageError error) { FlashbackActors.LOGGER.warn("Morph preview unavailable for {}", definition.id(), error); }
        float width = ImGui.getContentRegionAvailX();
        SkinPickerChrome.stage(image == null ? 0 : image.texture(), width, Math.max(80, Math.min(110, width * .45f)),
            MorphPreviews.isUnavailable() ? "Preview unavailable" : "Loading preview...");
        ImGui.spacing();
        if (SkinPickerChrome.button("Add to timeline", false, width)) { timelinePage = true; MorphTracks.selected = null; }
        var current = Morph.selectionFor(actor.take.id);
        if (current != null && definition.id().equals(current.morphId()) && ImGui.collapsingHeader("Size and visibility")) tuning(actor);
        if (ImGui.collapsingHeader("Preview animation")) previewControls();
        customAnimation(actor, definition);
    }

    private static void posePicker(String label, String body) {
        draftPose = MorphPoses.normalize(body, draftPose);
        var poses=MorphPoses.choices(body);
        if(poses.size()==1) { ImGui.textDisabled("Pose follows recorded movement."); return; }
        centered(label, true);
        if(poses.size()>4) {
            ImGui.setNextItemWidth(-1);
            if(ImGui.beginCombo("##MorphActionChoice",MorphPoses.label(draftPose))) {
                try { for(String pose:poses) if(ImGui.selectable(MorphPoses.label(pose),pose.equals(draftPose))) draftPose=pose; }
                finally { ImGui.endCombo(); }
            }
            ImGui.textWrapped(MorphPoses.description(draftPose));
            return;
        }
        float width=ImGui.getContentRegionAvailX();
        int columns=width>=180?2:1;
        float buttonWidth=Math.max(1,(width-(columns-1)*ImGui.getStyle().getItemSpacingX())/columns);
        for(int i=0;i<poses.size();i++) {
            if(i%columns!=0) ImGui.sameLine();
            String pose=poses.get(i);
            if(SkinPickerChrome.button(MorphPoses.label(pose)+"##MorphPose"+pose,pose.equals(draftPose),buttonWidth)) draftPose=pose;
            ImGui.setItemTooltip(MorphPoses.description(pose));
        }
        ImGui.textWrapped(MorphPoses.description(draftPose));
    }

    private static void previewControls() {
        var playback = MorphPreviews.playback(); var stages = MorphPreviews.stages();
        if (stages.isEmpty()) return;
        if (ImGui.checkbox("Play animation", playback.playing)) playback.playing = !playback.playing;
        if (ImGui.checkbox("Cycle animations", playback.cycling)) playback.cycling = !playback.cycling;
        int stage = Math.min(MorphPreviews.stage(), stages.size() - 1);
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##PreviewAnimation", stages.get(stage))) {
            try { for (int i = 0; i < stages.size(); i++) if (ImGui.selectable(stages.get(i), stage == i)) playback.select(i); }
            finally { ImGui.endCombo(); }
        }
    }

    private static void tuning(Actor actor) {
        var current = Morph.selectionFor(actor.take.id);
        if (current == null) return;
        ImGui.beginDisabled(actor.take.locked);
        try {
            float[] scale = {current.scale()}, alpha = {current.alpha()};
            ImGui.setNextItemWidth(-110);
            if (ImGui.sliderFloat("Size", scale, MorphSelection.MIN_SCALE, MorphSelection.MAX_SCALE, "x%.2f")) Morph.store().set(actor.take.id, current.withScale(scale[0]));
            ImGui.setNextItemWidth(-110);
            if (ImGui.sliderFloat("Opacity", alpha, 0, 1, "%.2f")) Morph.store().set(actor.take.id, Morph.selectionFor(actor.take.id).withAlpha(alpha[0]));
            if (ImGui.checkbox("Hide name", current.hideName())) Morph.store().set(actor.take.id, Morph.selectionFor(actor.take.id).withNameHidden(!current.hideName()));
            Morph.save();
        } finally { ImGui.endDisabled(); }
    }

    private static void customAnimation(Actor actor, MorphDefinition definition) {
        var animations = CustomMobs.animations(definition.id());
        if (animations.isEmpty() || !ImGui.collapsingHeader("Custom model animation")) return;
        String current = CustomMobs.animation(actor.take.id);
        ImGui.beginDisabled(actor.take.locked);
        try {
            if (ImGui.beginCombo("##CustomMobPlayback", current.isBlank() ? "Auto: idle / walk" : current)) {
                try {
                    if (ImGui.selectable("Auto: idle / walk", current.isBlank())) setAnimation(actor, "");
                    for (String animation : animations) if (ImGui.selectable(animation, current.equals(animation))) setAnimation(actor, animation);
                } finally { ImGui.endCombo(); }
            }
        } finally { ImGui.endDisabled(); }
    }
    private static void setAnimation(Actor actor, String animation) {
        try { CustomMobs.animation(actor.take.id, animation); } catch (java.io.IOException error) { status = error.getMessage(); }
    }

    private static void selectClip(UUID id) {
        var clip = MorphTracks.timeline().find(id);
        if (clip == null || target == null || !target.take.id.equals(clip.actor)) return;
        MorphTracks.selected = id; preview = clip.morph; draftPose = clip.pose; clipStart = clip.start; clipEnd = clip.end;
    }

    private static void timeline(Actor actor) {
        var track = MorphTracks.timeline();
        var selected = track.find(MorphTracks.selected);
        if (selected != null && !actor.take.id.equals(selected.actor)) selected = null;
        centered("Choose a body, pose and time range. Gaps use the actor's default body.", true);
        ImGui.beginDisabled(actor.take.locked);
        try {
            ImGui.setNextItemWidth(-1);
            centered("Body", true);
            if (ImGui.beginCombo("##TimelineBody", bodyName(preview))) {
                try {
                    if (ImGui.selectable("Player", "player".equals(preview))) selectBody("player");
                    for (var definition : Morph.registry().all())
                        if (ImGui.selectable(definition.displayName(), definition.id().equals(preview))) selectBody(definition.id());
                } finally { ImGui.endCombo(); }
            }
            posePicker("Animation / pose", preview);
            var start = new ImFloat(clipStart / 20f); var end = new ImFloat(clipEnd / 20f);
            ImGui.setNextItemWidth(-170);
            if (ImGui.inputFloat("Start (seconds)", start)) clipStart = Math.max(0, Math.min(99_999_999, Math.round(start.get() * 20)));
            ImGui.setNextItemWidth(-170);
            if (ImGui.inputFloat("End (seconds)", end)) clipEnd = Math.max(0, Math.min(100_000_000, Math.round(end.get() * 20)));
            ImGui.beginDisabled(clipEnd <= clipStart || preview == null);
            try {
                if (selected != null) {
                    var c = selected;
                    if (primary("Save selected clip", ImGui.getContentRegionAvailX())) MorphTracks.edit(() -> {
                        c.morph = preview; c.pose = draftPose; c.start = clipStart; c.end = clipEnd;
                    });
                }
                if (SkinPickerChrome.button("Add new morph clip", false, ImGui.getContentRegionAvailX())) MorphTracks.add(actor.take.id, preview, draftPose, clipStart, clipEnd);
            } finally { ImGui.endDisabled(); }
        } finally { ImGui.endDisabled(); }
        ImGui.separator(); centered("Clips for " + actor.displayName(), true);
        var clips = track.clips.stream().filter(c -> actor.take.id.equals(c.actor)).sorted(java.util.Comparator.comparingInt(c -> c.start)).toList();
        if (clips.isEmpty()) centered("No clips yet. Add one above to create this actor's Morph lane.", true);
        for (var c : clips) {
            ImGui.pushID(c.id.toString());
            try {
                String text = String.format(java.util.Locale.ROOT, "%.2fs to %.2fs   %s / %s%s", c.start / 20f, c.end / 20f, bodyName(c.morph), MorphPoses.label(c.pose), c.enabled ? "" : " (muted)");
                if (ImGui.selectable(text, c.id.equals(MorphTracks.selected), 0, 0, 32)) selectClip(c.id);
                if (ImGui.beginPopupContextItem("##MorphClipMenu")) {
                    try {
                        ImGui.beginDisabled(actor.take.locked);
                        try {
                            if (ImGui.menuItem(c.enabled ? "Mute clip" : "Unmute clip")) MorphTracks.edit(() -> c.enabled = !c.enabled);
                            if (ImGui.menuItem("Duplicate")) MorphTracks.duplicate(c.id);
                            if (ImGui.menuItem("Delete")) MorphTracks.edit(() -> track.clips.remove(c));
                        } finally { ImGui.endDisabled(); }
                    } finally { ImGui.endPopup(); }
                }
            } finally { ImGui.popID(); }
        }
    }

    private static void centered(String text, boolean muted) {
        float left = ImGui.getCursorPosX(), width = ImGui.getContentRegionAvailX();
        String line = "";
        for (String word : text.split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && ImGui.calcTextSizeX(next) > width) {
                centeredLine(line, muted, left, width);
                line = word;
            } else line = next;
        }
        if (!line.isEmpty()) centeredLine(line, muted, left, width);
        ImGui.setCursorPosX(left);
    }

    private static void centeredLine(String text, boolean muted, float left, float width) {
        ImGui.setCursorPosX(left + Math.max(0, (width - ImGui.calcTextSizeX(text)) / 2));
        if (muted) ImGui.textDisabled(text); else ImGui.textUnformatted(text);
    }

    private static String title(String text) { return text.isBlank() ? "All" : Character.toUpperCase(text.charAt(0)) + text.substring(1); }
    private static String bodyName(String id) { var definition = Morph.registry().byId(id); return definition == null ? id : definition.displayName(); }
    private static void applyMorph(Actor actor, MorphDefinition definition) {
        if (definition == null || actor.take.locked) return;
        var current = Morph.selectionFor(actor.take.id);
        var next = (current == null ? MorphSelection.of(actor.take.id, definition.id()) : current.withMorph(definition.id())).withPose(draftPose);
        Morph.store().set(actor.take.id, next); Morph.save(); Morph.refreshVisible();
        status = actor.displayName() + " now uses " + definition.displayName() + ".";
        if (MorphTracks.timeline().active(actor.take.id, pdx.dev.fbactors.camera.BoatSubjects.clock()) != null)
            status += " A timeline clip overrides the default body at this time.";
    }
}
