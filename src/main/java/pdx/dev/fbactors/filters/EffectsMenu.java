package pdx.dev.fbactors.filters;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import pdx.dev.fbactors.FlashbackActors;

public final class EffectsMenu {
    private static final ImBoolean OPEN = new ImBoolean(false);
    private static java.util.UUID named;
    private static final ImString NAME = new ImString(64);
    private EffectsMenu() {}
    public static void open() { OPEN.set(true); }

    public static void renderWindow() {
        if (!OPEN.get()) return;
        ImGui.setNextWindowSize(440, 420, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Edit Looks###ActorEffects", OPEN)) renderContents();
        ImGui.end();
        if (!ImGui.isAnyItemActive() && !EffectsTrack.isDragging()) Effects.finishEdit();
    }

    private static String targetName(java.util.UUID actor) {
        return FlashbackActors.ACTORS.actors().stream().filter(item -> item.take.id.equals(actor))
            .map(item -> item.displayName()).findFirst().orElse("Missing actor");
    }

    public static void renderContents() {
        if (com.moulberry.flashback.Flashback.getReplayServer() == null) {
            ImGui.textWrapped("Open a replay to add effects to the timeline.");
            return;
        }
        var track = Effects.timeline();
        var clip = Effects.selected();
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##EffectClip", clip == null ? "Choose an effect clip..." : clip.name)) {
            for (var item : track.clips) {
                if (ImGui.selectable(item.name + " / " + String.format(java.util.Locale.ROOT, "%.1fs", item.start / 20f) + "##" + item.id, item.id.equals(Effects.selected))) {
                    Effects.finishEdit();
                    Effects.selected = item.id;
                }
            }
            ImGui.endCombo();
        }
        clip = Effects.selected();
        if (clip == null) {

            ImGui.spacing();
            ImGui.textWrapped("Drag a look from the Looks library onto an actor track or Misc. Select a clip to edit it.");
        } else {
            ImGui.textDisabled(clip.actor == null ? "Target: Misc / whole scene" : "Target: " + targetName(clip.actor));
            if (clip.actor == null) {
                if (ImGui.checkbox("Enable Misc", track.enabled)) { track.enabled = !track.enabled; Effects.changed(); }
                ImGui.sameLine();
                if (ImGui.checkbox("Lock Misc", track.locked)) { track.locked = !track.locked; Effects.changed(); }
            }
            ImGui.separator();
            ImGui.beginDisabled(Effects.locked(clip));
            if (ImGui.beginTabBar("##LookEditorTabs")) {
                if (ImGui.beginTabItem("Look")) {
                    LooksMenu.renderClip(clip, "Look");
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("Timing")) {
                    renderTiming(clip);
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("Grade")) {
                    LooksMenu.renderClip(clip, "Grade");
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("Presets")) {
                    LooksMenu.renderClip(clip, "Presets");
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
            ImGui.endDisabled();
        }
        ImGui.separator();
        if (ImGui.button("Browse looks")) LooksMenu.open();
        if (!Effects.status.isEmpty()) ImGui.textWrapped(Effects.status);
        if (!Looks.status.isEmpty()) ImGui.textWrapped(Looks.status);
        if (!ImGui.isAnyItemActive() && !EffectsTrack.isDragging()) Effects.finishEdit();
    }

    private static void renderTiming(EffectClip clip) {
        if (!clip.id.equals(named)) { NAME.set(clip.name); named = clip.id; }
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputText("##EffectName", NAME)) { clip.name = NAME.get(); Effects.changed(); }
        if (ImGui.checkbox("Enable clip", clip.enabled)) { clip.enabled = !clip.enabled; Effects.changed(); }
        float[] timing = {clip.start / 20f, clip.end / 20f};
        ImGui.setNextItemWidth(-110);
        if (ImGui.sliderFloat("Start (seconds)", timing, 0, Math.max(300, clip.end / 20f), "%.2f")) {
            clip.start = Math.min(clip.end - 1, Math.round(timing[0] * 20)); Effects.changed();
        }
        float[] end = {clip.end / 20f};
        ImGui.setNextItemWidth(-110);
        if (ImGui.sliderFloat("End (seconds)", end, (clip.start + 1) / 20f, Math.max(300, clip.end / 20f + 30), "%.2f")) {
            clip.end = Math.round(end[0] * 20); Effects.changed();
        }
        float[] fadeIn = {clip.fadeIn / 20f}, fadeOut = {clip.fadeOut / 20f};
        ImGui.setNextItemWidth(-110);
        if (ImGui.sliderFloat("Fade in", fadeIn, 0, (clip.end - clip.start) / 20f, "%.2fs")) { clip.fadeIn = Math.round(fadeIn[0] * 20); Effects.changed(); }
        ImGui.setNextItemWidth(-110);
        if (ImGui.sliderFloat("Fade out", fadeOut, 0, (clip.end - clip.start) / 20f, "%.2fs")) { clip.fadeOut = Math.round(fadeOut[0] * 20); Effects.changed(); }
        ImGui.spacing();
        if (ImGui.button("Duplicate")) Effects.duplicate();
        ImGui.sameLine();
        if (ImGui.button("Delete clip")) Effects.remove();
    }
}
