package pdx.dev.fbactors.filters;

import imgui.moulberry90.ImGui;
import pdx.dev.fbactors.ui.TimelineGeometry;
import java.util.UUID;

public final class EffectsTrack {
    private static UUID dragging;
    private static UUID contextClip;
    private static int mode;
    private static float originX;
    private static int originStart;
    private static int originEnd;
    private EffectsTrack() {}
    public static boolean isDragging() { return dragging != null; }

    public static void render(TimelineGeometry geometry, int cursorTick) { render(geometry, cursorTick, null); }

    public static void renderActor(TimelineGeometry geometry, int cursorTick, UUID actor) {
        if (Effects.timeline().clips.stream().anyMatch(clip -> actor.equals(clip.actor))) render(geometry, cursorTick, actor);
    }

    private static void render(TimelineGeometry geometry, int cursorTick, UUID actor) {
        var track = Effects.timeline();
        var actorTrack = actor == null ? null : pdx.dev.fbactors.FlashbackActors.ACTORS.actors().stream()
            .filter(item -> item.take.id.equals(actor)).findFirst().orElse(null);
        boolean enabled = actor == null ? track.enabled : actorTrack != null && actorTrack.enabled();
        boolean locked = actor == null ? track.locked : actorTrack == null || actorTrack.take.locked;
        float top = ImGui.getCursorScreenPosY();
        float height = ImGui.getTextLineHeight();
        float barHeight = Math.max(8, height - 1);
        var draw = ImGui.getWindowDrawList();
        float center = top + height * 0.5f;
        int color = enabled ? 0xffffbd84 : 0xff777777;
        ImGui.pushID("ActorEffectsTrack" + actor);
        sliders(geometry.x() + 10, center, color);
        draw.addText(geometry.x() + 22, top, color, actor == null ? "Misc" : "Looks");
        draw.addText(geometry.panelRight() - 16, top, color, "+");
        if (ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(geometry.x(), top, geometry.panelRight(), top + height)) {
            ImGui.setTooltip(actor == null ? "Misc: scene looks. Drag a look here from Looks." : "Actor looks. Double click a clip to edit.");
            if (ImGui.isMouseClicked(0)) LooksMenu.open();
            if (ImGui.isMouseClicked(1)) {
                Effects.finishEdit();
                contextClip = null;
                ImGui.openPopup("##EffectContext");
            }
        }
        float left = geometry.laneLeft(), right = geometry.laneRight();
        float clipTop = Math.max(top, geometry.clipTop());
        float clipBottom = Math.min(top + height, geometry.clipBottom());
        if (right > left && geometry.pixelsPerTick() > 0 && clipBottom > clipTop) {
            draw.pushClipRect(left, clipTop, right, clipBottom, true);
            for (var clip : track.clips) {
                if (!java.util.Objects.equals(actor, clip.actor)) continue;
                float x0 = geometry.tickX(clip.start), x1 = geometry.tickX(clip.end);
                float a = Math.max(left, x0), b = Math.min(right, x1);
                if (b <= a) continue;
                boolean selected = clip.id.equals(Effects.selected);
                boolean hover = ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(a, top, b, top + height);
                boolean muted = !enabled || !clip.enabled;
                int fill = muted ? 0xff39363a : selected ? 0xff976b45 : hover ? 0xff795a40 : 0xff604832;
                int border = muted ? 0xff5a5558 : selected ? 0xffedc399 : 0xff9a7552;
                float barTop = center - barHeight * 0.5f;
                float barBottom = barTop + barHeight;
                draw.addRectFilled(a, barTop, b, barBottom, fill, 2);
                draw.addRect(a, barTop, b, barBottom, border, 2);
                if (x0 >= left && b - a > 4) draw.addRectFilled(a + 1, barTop + 1, a + 3, barBottom - 1, border, 1);
                if (selected || hover) {
                    if (x0 >= left && b - a > 16) draw.addLine(a + 4, barTop + 3, a + 4, barBottom - 3, 0xbbeeeeee, 1);
                    if (x1 <= right && b - a > 16) draw.addLine(b - 4, barTop + 3, b - 4, barBottom - 3, 0xbbeeeeee, 1);
                }
                if (selected && clip.fadeIn > 0) draw.addLine(a, barBottom, Math.min(b, geometry.tickX(clip.start + clip.fadeIn)), barTop, 0x88ffffff, 1);
                if (selected && clip.fadeOut > 0) draw.addLine(Math.max(a, geometry.tickX(clip.end - clip.fadeOut)), barTop, b, barBottom, 0x88ffffff, 1);
                if (b - a > 45) {
                    if (height <= 0) {
                        pdx.dev.fbactors.debug.DragDiag.warnRare("effect-title-clip",
                            "skipped effect title: a=%.1f b=%.1f h=%.1f", a, b, height);
                    } else {
                        draw.pushClipRect(a + 6, top, b - 5, top + height, true);
                        draw.addText(a + 7, top, muted ? 0xffaaa5a5 : 0xfff5f1ed, clip.name);
                        draw.popClipRect();
                    }
                }
                if (hover && dragging == null) {
                    ImGui.setTooltip(clip.name + "\nDrag to move. Drag an edge to trim. Double click to edit.");
                    if (ImGui.isMouseClicked(0)) {
                        Effects.finishEdit();
                        Effects.selected = clip.id;
                        if (ImGui.isMouseDoubleClicked(0)) EffectsMenu.open();
                        else if (!locked) {
                            dragging = clip.id;
                            originX = ImGui.getMousePosX();
                            originStart = clip.start;
                            originEnd = clip.end;
                            mode = Math.abs(originX - x0) < 6 ? 1 : Math.abs(originX - x1) < 6 ? 2 : 0;
                        }
                    }
                    if (ImGui.isMouseClicked(1)) {
                        Effects.finishEdit();
                        Effects.selected = clip.id;
                        contextClip = clip.id;
                        ImGui.openPopup("##EffectContext");
                    }
                }
            }
            draw.popClipRect();
        } else {
            pdx.dev.fbactors.debug.DragDiag.warnRare("effects-clip",
                "skipped effect lane: left=%.1f right=%.1f clipTop=%.1f clipBottom=%.1f h=%.1f ppt=%.3f",
                left, right, clipTop, clipBottom, height, geometry.pixelsPerTick());
        }
        if (dragging != null && (track.find(dragging) == null || java.util.Objects.equals(actor, track.find(dragging).actor))) {
            var clip = track.find(dragging);
            if (clip == null || locked || !ImGui.isMouseDown(0)) {
                dragging = null;
                Effects.finishEdit();
            } else {
                int delta = geometry.deltaTicks(ImGui.getMousePosX() - originX);
                int start = originStart, end = originEnd;
                if (mode == 1) start = Math.max(0, Math.min(end - 1, originStart + delta));
                else if (mode == 2) end = Math.max(start + 1, originEnd + delta);
                else { start = Math.max(0, originStart + delta); end = start + originEnd - originStart; }
                int edge = mode == 2 ? end : start;
                if (Math.abs(geometry.tickX(edge) - geometry.tickX(cursorTick)) < 6) {
                    int snap = cursorTick - edge;
                    if (mode == 0) { if (start + snap >= 0) { start += snap; end += snap; } }
                    else if (mode == 1 && cursorTick >= 0 && cursorTick < end) start = cursorTick;
                    else if (mode == 2 && cursorTick > start) end = cursorTick;
                }
                if (clip.start != start || clip.end != end) { clip.start = start; clip.end = end; Effects.changed(); }
            }
        }
        contextMenu(actor, locked);
        ImGui.setCursorPosX(0);
        ImGui.dummy(Math.max(1, geometry.width()), Math.max(1, height));
        dropTarget(geometry, top, height, actor, cursorTick);
        ImGui.separator();
        ImGui.popID();
    }

    private static void contextMenu(UUID actor, boolean locked) {
        if (!ImGui.beginPopup("##EffectContext")) return;
        var clip = Effects.timeline().find(contextClip);
        if (clip != null && java.util.Objects.equals(actor, clip.actor)) {
            if (ImGui.menuItem("Edit effect")) { Effects.selected = clip.id; EffectsMenu.open(); }
            ImGui.beginDisabled(locked);
            if (ImGui.menuItem("Duplicate effect")) { Effects.selected = clip.id; Effects.duplicate(); }
            if (ImGui.menuItem("Delete effect")) { Effects.selected = clip.id; Effects.remove(); }
            ImGui.endDisabled();
            ImGui.separator();
        }
        if (ImGui.menuItem("Browse looks")) LooksMenu.open();
        ImGui.beginDisabled(locked || Effects.timeline().clips.stream().noneMatch(item -> java.util.Objects.equals(actor, item.actor)));
        if (ImGui.menuItem("Delete effect track")) {
            dragging = null;
            Effects.removeTrack(actor);
        }
        ImGui.setItemTooltip(actor == null ? "Remove every scene effect. Undo restores them." : "Remove this actor's Looks lane and all its effects. Undo restores them.");
        ImGui.endDisabled();
        ImGui.endPopup();
    }

    public static void dropTarget(TimelineGeometry geometry, float top, float height, UUID actor, int cursorTick) {
        float oldX = ImGui.getCursorScreenPosX(), oldY = ImGui.getCursorScreenPosY();
        ImGui.setCursorScreenPos(geometry.x(), top);
        ImGui.dummy(Math.max(1, geometry.width()), Math.max(1, height));
        if (ImGui.beginDragDropTarget()) {
            String filter = ImGui.acceptDragDropPayload("ACTORS_LOOK", String.class);
            if (filter != null && (actor != null || !Effects.timeline().locked)) {
                pdx.dev.fbactors.debug.DragDiag.log(
                    "drop accepted: filter=%s actor=%s tick=%d mouse=(%.1f,%.1f) lane=[%.1f,%.1f] top=%.1f h=%.1f clip=[%.1f,%.1f]",
                    filter, String.valueOf(actor), cursorTick, ImGui.getMousePosX(), ImGui.getMousePosY(),
                    geometry.laneLeft(), geometry.laneRight(), top, height,
                    geometry.clipTop(), geometry.clipBottom());
                if (actor == null) {
                    int tick = ImGui.getMousePosX() < geometry.laneLeft() ? cursorTick : geometry.tickAt(ImGui.getMousePosX());
                    Effects.add(filter, Math.max(0, tick));
                } else {
                    var target = pdx.dev.fbactors.FlashbackActors.ACTORS.actors().stream()
                        .filter(item -> item.take.id.equals(actor)).findFirst().orElse(null);
                    if (target != null && !target.take.locked) Effects.attach(filter, target);
                }
            }
            ImGui.endDragDropTarget();
        }
        ImGui.setCursorScreenPos(oldX, oldY);
    }

    private static void sliders(float x, float y, int color) {
        var draw = ImGui.getWindowDrawList();
        for (int i = 0; i < 3; i++) {
            float row = y - 4 + i * 4;
            float knob = x + (i == 1 ? 2 : -2);
            draw.addLine(x - 5, row, x + 5, row, color, 1);
            draw.addRectFilled(knob - 1, row - 1.5f, knob + 1, row + 1.5f, color, 0.5f);
        }
    }
}
