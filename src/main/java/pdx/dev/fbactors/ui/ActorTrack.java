package pdx.dev.fbactors.ui;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.playback.ReplayServer;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImString;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.take.ActorDeath;
import pdx.dev.fbactors.take.ActorHit;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.ClipEditor;
import pdx.dev.fbactors.take.TimelineOps;
import pdx.dev.fbactors.timeline.TimelineSelection;
import pdx.dev.fbactors.undo.UndoStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ActorTrack {

    private static final String ICON_VISIBLE = "\ue8f4";
    private static final String ICON_HIDDEN = "\ue8f5";
    private static final String ICON_DELETE = "\ue872";
    private static final String ICON_RECORD = "\ue04b";
    private static final String ICON_MENU = "\ue5d2";

    private static final int ID_BASE = 0x7FBAC00;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RECORD_RED = ActorColours.pack(255, 72, 72, 255);

    private static final float MAX_HEAD = 18.0f;
    private static final float EDGE_GRAB = 5.0f;
    private static final float SNAP_PIXELS = 6.0f;
    private static final int SCROLL_ROWS = 3;

    private static final int MODE_MOVE = 1;
    private static final int MODE_TRIM_IN = 2;
    private static final int MODE_TRIM_OUT = 3;

    @Nullable
    private static UUID dragActor;
    private static int dragMode;
    private static float dragOriginX;
    private static int dragOriginValue;

    @Nullable
    private static UUID renaming;
    @Nullable
    private static ImString renameBuffer;
    private static boolean renameFocus;

    private static final float[] COLOUR_BUFFER = new float[3];

    /**
     * The take whose colour popup is open, and the colour it had when the
     * popup opened. The picker writes every frame while dragged, so the
     * undo entry is a single push on close, not one per frame.
     */
    @Nullable
    private static UUID colourUndoId;
    private static int colourUndoBefore;
    private static boolean colourWasOpen;

    @Nullable
    private static Actor pendingRemove;
    @Nullable
    private static Actor pendingDuplicate;
    @Nullable
    private static ActorTake pendingSave;
    private static int pendingSeek = -1;

    @Nullable
    private static Actor selected;

    /** Phase 1 multi-select: the selected take ids in selection order. */
    private static final TimelineSelection MULTI = new TimelineSelection();

    /** Camera keyframes on the key clipboard for copy/paste, or empty. */
    private static List<CameraKeyframe> keyClipboard = new ArrayList<>();

    private ActorTrack() {
    }

    public static boolean isDragging() {
        return dragActor != null;
    }

    public static void render(TimelineGeometry geometry, int cursorTicks) {
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        ImDrawList drawList = ImGui.getWindowDrawList();

        float textHeight = ImGui.getTextLineHeight();
        float spacingX = ImGui.getStyle().getItemSpacingX();
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();

        handleScroll(geometry, lineHeight);
        boolean soloing = FlashbackActors.ACTORS.anySolo();

        for (int i = 0; i < actors.size(); i++) {
            Actor actor = actors.get(i);
            ImGui.pushID(ID_BASE + i);
            row(actor, geometry, drawList, cursorTicks, textHeight, spacingX, lineHeight, soloing);
            ImGui.popID();
            ImGui.separator();
        }

        Actor selection = selected;
        if (selection != null && actors.contains(selection)) {
            ImGui.pushID(ID_BASE - 2);
            sideMenu(selection, cursorTicks);
            ImGui.popID();
            ImGui.separator();
        }

        ImGui.pushID(ID_BASE - 1);
        recordRow(geometry, drawList, cursorTicks, textHeight, lineHeight);
        ImGui.popID();
        ImGui.separator();

        ImGui.setCursorPosX(8.0f);
        flush();
    }

    private static void handleScroll(TimelineGeometry geometry, float lineHeight) {
        if (ImGui.getScrollMaxY() <= 0.0f || ReplayUiInput.wantsText()) {
            return;
        }
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        if (mouseX < geometry.x() || mouseX > geometry.panelRight()
            || mouseY < geometry.clipTop() || mouseY > geometry.clipBottom()) {
            return;
        }
        float wheel = ReplayUiInput.wheel();
        if (wheel == 0.0f) {
            return;
        }
        float target = ImGui.getScrollY() - wheel * lineHeight * SCROLL_ROWS;
        ImGui.setScrollY(Math.max(0.0f, Math.min(ImGui.getScrollMaxY(), target)));
    }

    private static void row(Actor actor, TimelineGeometry geometry, ImDrawList drawList,
                            int cursorTicks, float textHeight, float spacingX, float lineHeight,
                            boolean soloing) {
        float rowTop = ImGui.getCursorScreenPosY();
        float midY = rowTop + textHeight * 0.5f;
        int colour = colourOf(actor);
        boolean muted = !actor.enabled() || (soloing && !actor.solo);
        int tint = muted ? 0x70FFFFFF : WHITE;

        lane(actor, geometry, drawList, cursorTicks, midY, lineHeight, colour, tint, muted);

        if (selected == actor) {
            drawList.addRectFilled(geometry.x(), rowTop - 1.0f, geometry.panelRight(), rowTop + lineHeight - 1.0f,
                ActorColours.withAlpha(colour, 0x26));
        }

        ImGui.setCursorPosX(4.0f);
        ActorHeads.draw(drawList, actor.take.id, ImGui.getCursorScreenPosX(), rowTop, textHeight, tint);
        ImGui.dummy(textHeight, textHeight);
        if (ImGui.isItemClicked(0)) {
            if (ReplayUiInput.ctrlDown()) {
                toggleSelect(actor);
            } else {
                select(actor);
            }
            pendingSeek = actor.take.firstTick() + actor.offset();
        }
        ImGui.setItemTooltip("Jump to this actor's entrance");
        ImGui.sameLine();

        if (actor.take.id.equals(renaming)) {
            renameField(actor);
        } else {
            String name = actor.solo ? actor.displayName() + " *" : actor.displayName();
            if (muted) {
                ImGui.textDisabled(name);
            } else {
                ImGui.textColored(colour, name);
            }
            if (ImGui.isItemClicked(0) && ImGui.isMouseDoubleClicked(0)) {
                beginRename(actor);
            } else if (ImGui.isItemClicked(0)) {
                if (ReplayUiInput.ctrlDown()) {
                    toggleSelect(actor);
                } else {
                    select(actor);
                }
            }
            if (ImGui.isItemClicked(1)) {
                ImGui.openPopup("##ActorPopup");
            }
            ImGui.setItemTooltip(summary(actor));
        }

        ImGui.sameLine();
        float buttonX = geometry.panelRight() - (textHeight + spacingX) * 3.0f;
        float buttonY = ImGui.getCursorScreenPosY();

        ImGui.setCursorPosX(buttonX - geometry.x());
        if (ImGui.invisibleButton("##ActorOptions", textHeight, textHeight) || ImGui.isItemClicked(1)) {
            select(actor);
            ImGui.openPopup("##ActorPopup");
        }
        drawList.addText(buttonX - 2.0f, buttonY, WHITE, ICON_MENU);
        ImGui.setItemTooltip("Actor options");

        ImGui.sameLine();
        buttonX += textHeight + spacingX;
        ImGui.setCursorPosX(buttonX - geometry.x());
        if (ImGui.invisibleButton("##ToggleActor", textHeight, textHeight)) {
            boolean before = actor.enabled();
            actor.setEnabled(!actor.enabled());
            UndoStack.pushEnabled(actor.take, before, actor.enabled());
            pendingSave = actor.take;
        }
        drawList.addText(buttonX - 2.0f, buttonY, WHITE, actor.enabled() ? ICON_VISIBLE : ICON_HIDDEN);
        ImGui.setItemTooltip(actor.enabled() ? "Hide this actor" : "Show this actor");

        ImGui.sameLine();
        buttonX += textHeight + spacingX;
        ImGui.setCursorPosX(buttonX - geometry.x());
        if (ImGui.invisibleButton("##DeleteActor", textHeight, textHeight)) {
            pendingRemove = actor;
        }
        drawList.addText(buttonX - 2.0f, buttonY, WHITE, ICON_DELETE);
        ImGui.setItemTooltip("Delete this actor and its take");

        popup(actor, cursorTicks);
    }

    private static void renameField(Actor actor) {
        if (renameBuffer == null) {
            renaming = null;
            return;
        }
        ImGui.setNextItemWidth(140.0f);
        if (renameFocus) {
            renameFocus = false;
            ImGui.setKeyboardFocusHere();
        }
        boolean done = ImGui.inputText("##ActorName", renameBuffer, 4160);
        if (done || ImGui.isItemDeactivated()) {
            String value = ImGuiHelper.getString(renameBuffer).trim();
            if (!value.isEmpty()) {
                String before = actor.take.name;
                actor.take.name = value;
                UndoStack.pushRename(actor.take, before, value);
                ActorSkinBridge.applyName(actor, value);
                pendingSave = actor.take;
            }
            renaming = null;
            renameBuffer = null;
        }
    }

    private static void beginRename(Actor actor) {
        renaming = actor.take.id;
        renameBuffer = ImGuiHelper.createResizableImString(actor.displayName());
        renameFocus = true;
    }

    private static void popup(Actor actor, int cursorTicks) {
        boolean openColour = false;

        if (ImGuiHelper.beginPopup("##ActorPopup")) {
            if (ImGui.menuItem("\ue5d2 Flashback menu")) {
                select(actor);
            }
            ImGui.separator();
            if (ImGui.menuItem("\ue3c9 Rename")) {
                beginRename(actor);
            }
            if (ImGui.menuItem("\ue40a Set colour")) {
                openColour = true;
            }
            if (ImGui.menuItem("Change skin...")) {
                SkinPicker.open(actor);
            }
            if (ImGui.menuItem("Default skin")) {
                ActorSkinBridge.clear(actor);
            }
            if (pdx.dev.fbactors.settings.FbSettings.morphEnabled()) {
                if (ImGui.menuItem("Change morph...")) {
                    MorphPicker.open(actor);
                }
                if (ImGui.menuItem("Default morph")) {
                    pdx.dev.fbactors.morph.Morph.store().clear(actor.take.id);
                    pdx.dev.fbactors.morph.Morph.save();
                }
            }
            ImGui.separator();
            if (ImGui.menuItem("Duplicate")) {
                pendingDuplicate = actor;
            }
            if (ImGui.menuItem("Move take to playhead")) {
                int before = actor.offset();
                actor.setOffset(cursorTicks - actor.take.startTick);
                UndoStack.pushOffset(actor.take, before, actor.offset());
                pendingSave = actor.take;
            }
            if (ImGui.menuItem("Trim start here")) {
                int beforeIn = actor.take.trimIn;
                int beforeOut = actor.take.trimOut;
                actor.take.trimIn = Math.max(0,
                    Math.min(actor.take.length() - 1, cursorTicks - actor.offset() - actor.take.startTick));
                UndoStack.pushTrim(actor.take, beforeIn, beforeOut,
                    actor.take.trimIn, actor.take.trimOut);
                pendingSave = actor.take;
            }
            if (ImGui.menuItem("Trim end here")) {
                int beforeIn = actor.take.trimIn;
                int beforeOut = actor.take.trimOut;
                actor.take.trimOut = Math.max(0,
                    Math.min(actor.take.length() - 1, actor.take.endTick() - (cursorTicks - actor.offset())));
                UndoStack.pushTrim(actor.take, beforeIn, beforeOut,
                    actor.take.trimIn, actor.take.trimOut);
                pendingSave = actor.take;
            }
            if (ImGui.menuItem("Reset trim")) {
                int beforeIn = actor.take.trimIn;
                int beforeOut = actor.take.trimOut;
                actor.take.resetTrim();
                UndoStack.pushTrim(actor.take, beforeIn, beforeOut,
                    actor.take.trimIn, actor.take.trimOut);
                pendingSave = actor.take;
            }
            if (ImGui.menuItem(actor.take.locked ? "Unlock track" : "Lock track")) {
                boolean before = actor.take.locked;
                boolean after = !before;
                actor.take.locked = after;
                UndoStack.pushEdit(() -> actor.take.locked = before,
                    () -> actor.take.locked = after);
                pendingSave = actor.take;
            }
            if (ImGui.menuItem("Razor at playhead")) {
                razorSelectedAt(cursorTicks);
            }
            if (ImGui.menuItem("Copy camera keys")) {
                select(actor);
                copyKeysSelected();
            }
            if (ImGui.menuItem("Paste camera keys at playhead")) {
                select(actor);
                pasteKeysSelected(cursorTicks);
            }

            ImGui.separator();
            if (ImGui.menuItem(actor.solo ? "Unsolo" : "Solo")) {
                boolean value = !actor.solo;
                UndoStack.pushSolo(UndoStack.cellsOf(
                    new ArrayList<>(FlashbackActors.ACTORS.actors())),
                    actor.take.id, value);
                for (Actor other : FlashbackActors.ACTORS.actors()) {
                    other.solo = false;
                }
                actor.solo = value;
                pendingSave = actor.take;
            }
            if (ImGui.menuItem("Spectate")) {
                ActorSkinBridge.command("spectate " + actor.take.id);
            }
            if (ImGui.menuItem("Teleport to")) {
                ActorSkinBridge.command("teleport " + actor.take.id);
            }

            ImGui.separator();
            if (ImGui.menuItem("\ue92b Delete")) {
                pendingRemove = actor;
            }
            ImGui.endPopup();
        }

        if (openColour) {
            int current = colourOf(actor);
            COLOUR_BUFFER[0] = (current & 0xFF) / 255.0f;
            COLOUR_BUFFER[1] = (current >>> 8 & 0xFF) / 255.0f;
            COLOUR_BUFFER[2] = (current >>> 16 & 0xFF) / 255.0f;
            colourUndoId = actor.take.id;
            colourUndoBefore = actor.take.colour;
            ImGui.openPopup("##ActorColour");
        }
        // The picker mutates every frame while it is dragged, so pushing per
        // frame would flood the stack with one entry per shade. Instead the
        // base colour is captured when the popup opens and a single edit is
        // pushed when it closes, covering both the picker and Reset.
        boolean colourOpen = ImGuiHelper.beginPopup("##ActorColour");
        if (colourOpen) {
            if (ImGui.button("Reset##ActorColourReset")) {
                actor.take.colour = 0;
                pendingSave = actor.take;
                ImGui.closeCurrentPopup();
            } else if (ImGui.colorPicker3("Actor colour", COLOUR_BUFFER)) {
                actor.take.colour = ActorColours.pack(
                    Math.round(COLOUR_BUFFER[0] * 255.0f),
                    Math.round(COLOUR_BUFFER[1] * 255.0f),
                    Math.round(COLOUR_BUFFER[2] * 255.0f), 255);
                pendingSave = actor.take;
            }
            ImGui.endPopup();
        }
        if (colourWasOpen && !colourOpen && colourUndoId != null
            && colourUndoId.equals(actor.take.id)
            && actor.take.colour != colourUndoBefore) {
            UndoStack.pushColour(actor.take, colourUndoBefore, actor.take.colour);
            pendingSave = actor.take;
        }
        if (!colourOpen) {
            colourUndoId = null;
        }
        colourWasOpen = colourOpen;
    }

    private static void lane(Actor actor, TimelineGeometry geometry, ImDrawList drawList,
                             int cursorTicks, float midY, float lineHeight, int colour, int tint,
                             boolean muted) {
        ActorTake take = actor.take;
        float laneLeft = geometry.laneLeft();
        float laneRight = geometry.laneRight();
        if (laneRight <= laneLeft || take.length() <= 0) {
            return;
        }

        int offset = actor.offset();
        int start = take.firstTick() + offset;
        int end = take.lastTick() + offset;
        float rawStart = geometry.tickX(start);
        float rawEnd = geometry.tickX(end);

        float barHeight = Math.max(5.0f, Math.min(lineHeight * 0.38f, 10.0f));
        float barTop = midY - barHeight * 0.5f;
        float barBottom = barTop + barHeight;

        interact(actor, geometry, cursorTicks, rawStart, rawEnd, barTop, barBottom);

        offset = actor.offset();
        start = take.firstTick() + offset;
        end = take.lastTick() + offset;
        rawStart = geometry.tickX(start);
        rawEnd = geometry.tickX(end);
        if (rawEnd < laneLeft || rawStart > laneRight) {
            return;
        }

        drawList.pushClipRect(laneLeft, geometry.clipTop(), laneRight, geometry.clipBottom(), true);

        boolean live = cursorTicks >= start && cursorTicks <= end;
        boolean grabbed = take.id.equals(dragActor);
        float barStart = Math.max(laneLeft + 1.0f, rawStart);
        float barEnd = Math.min(laneRight - 1.0f, Math.max(rawEnd, rawStart + 2.0f));

        if (barEnd > barStart) {
            int alpha = muted ? 0x2E : (grabbed ? 0xF0 : (live ? 0xC4 : 0x8C));
            drawList.addRectFilled(barStart, barTop, barEnd, barBottom,
                ActorColours.withAlpha(colour, alpha), barHeight * 0.5f);

            if (take.trimIn > 0) {
                ghost(drawList, geometry, take.startTick + offset, start, barTop, barBottom,
                    laneLeft, laneRight, colour);
            }
            if (take.trimOut > 0) {
                ghost(drawList, geometry, end, take.endTick() + offset, barTop, barBottom,
                    laneLeft, laneRight, colour);
            }

            int death = take.deathTick();
            if (death >= 0 && death + offset >= start && death + offset <= end) {
                float deathX = geometry.tickX(death + offset);
                if (deathX < barEnd) {
                    drawList.addRectFilled(Math.max(barStart, deathX), barTop, barEnd, barBottom,
                        ActorColours.pack(0, 0, 0, muted ? 0x28 : 0x55), barHeight * 0.5f);
                }
                mark(drawList, deathX, midY, barHeight * 0.5f + 2.0f, laneLeft, laneRight,
                    muted ? 0x50 : 0xDD);
            }

            for (ActorHit hit : take.hits) {
                int tick = hit.tick() + offset;
                if (tick < start || tick > end) {
                    continue;
                }
                mark(drawList, geometry.tickX(tick), midY, barHeight * 0.5f + 1.0f,
                    laneLeft, laneRight, muted ? 0x44 : 0xAA);
            }

            // Voice lines sit along the bottom of the bar as a waveform, with
            // the actor's head at each one so it is obvious who is speaking.
            if (!take.voices.isEmpty()) {
                float waveTop = barBottom - barHeight * 0.34f;
                for (pdx.dev.fbactors.take.ActorVoiceLine line : take.voices) {
                    // Voice ticks are take-relative, so shift by start and
                    // offset to land on the timeline like every other marker.
                    int tick = line.tick() + take.startTick + offset;
                    if (tick < start || tick > end) {
                        continue;
                    }
                    float x = geometry.tickX(tick);
                    float width = Math.max(2.0f,
                        line.durationTicks() * geometry.pixelsPerTick());
                    float amplitude = peak(line.samples());
                    float height = Math.max(1.5f, barHeight * 0.30f * amplitude);
                    int voiceAlpha = muted ? 0x40 : 0xD0;
                    drawList.addRectFilled(
                        Math.max(barStart, x), midY - height * 0.5f,
                        Math.min(barEnd, x + width), midY + height * 0.5f,
                        ActorColours.pack(140, 220, 255, voiceAlpha), 1.0f);
                    if (!muted) {
                        ActorHeads.draw(drawList, take.id, x, waveTop, barHeight * 0.34f, tint);
                    }
                }
            }

            // Manual subtitles sit on the same lane as hits: one tick each,
            // take-relative like voices, so they move with the clip.
            for (pdx.dev.fbactors.take.ActorSubtitle subtitle : take.subtitles) {
                int tick = subtitle.tick() + take.startTick + offset;
                if (tick < start || tick > end) {
                    continue;
                }
                mark(drawList, geometry.tickX(tick), midY, barHeight * 0.5f + 1.0f,
                    laneLeft, laneRight, muted ? 0x44 : 0xCC);
            }

            for (ActorDeath authored : take.deaths) {
                int tick = authored.tick() + offset;
                if (tick < start || tick > end) {
                    continue;
                }
                diamond(drawList, geometry.tickX(tick), midY, barHeight * 0.5f + 2.0f,
                    laneLeft, laneRight, muted
                        ? ActorColours.withAlpha(kindColour(authored.kind()), 0x40)
                        : kindColour(authored.kind()));
            }
        }

        float headSize = Math.max(10.0f, Math.min(lineHeight - 6.0f, MAX_HEAD));
        float headTop = midY - headSize * 0.5f;
        float floor = laneLeft + 2.0f;
        float ceiling = Math.max(floor, Math.min(laneRight - headSize - 2.0f, rawEnd - headSize));
        float headX = Math.max(floor, Math.min(rawStart - headSize * 0.5f, ceiling));

        label(drawList, actor, headX + headSize + 5.0f, midY, barEnd, colour, muted);

        drawList.addRectFilled(headX - 1.5f, headTop - 1.5f, headX + headSize + 1.5f,
            headTop + headSize + 1.5f, ActorColours.withAlpha(colour, muted ? 0x66 : 0xFF), 3.0f);
        ActorHeads.draw(drawList, take.id, headX, headTop, headSize, tint);

        drawList.popClipRect();
    }

    private static void ghost(ImDrawList drawList, TimelineGeometry geometry, int fromTick,
                              int toTick, float barTop, float barBottom, float laneLeft,
                              float laneRight, int colour) {
        float from = Math.max(laneLeft + 1.0f, geometry.tickX(fromTick));
        float to = Math.min(laneRight - 1.0f, geometry.tickX(toTick));
        if (to <= from) {
            return;
        }
        float inset = (barBottom - barTop) * 0.32f;
        drawList.addRectFilled(from, barTop + inset, to, barBottom - inset,
            ActorColours.withAlpha(colour, 0x2A));
    }

    private static void label(ImDrawList drawList, Actor actor, float textX, float midY,
                              float barEnd, int colour, boolean muted) {
        String name = actor.displayName();
        float textWidth = ImGuiHelper.calcTextWidth(name);
        if (textX + textWidth + 4.0f > barEnd) {
            return;
        }
        drawList.addText(textX, midY - ImGui.getTextLineHeight() * 0.5f,
            ActorColours.withAlpha(WHITE, muted ? 0x50 : 0xB4), name);
    }

    private static void interact(Actor actor, TimelineGeometry geometry, int cursorTicks,
                                 float rawStart, float rawEnd, float barTop, float barBottom) {
        ActorTake take = actor.take;
        boolean mine = take.id.equals(dragActor);

        if (mine) {
            if (!ImGui.isMouseDown(0)) {
                dragActor = null;
                int mode = dragMode;
                dragMode = 0;
                if (!take.locked) {
                    if (mode == MODE_MOVE) {
                        int before = dragOriginValue;
                        int after = actor.offset();
                        if (before != after) {
                            UndoStack.pushEdit(() -> actor.setOffset(before),
                                () -> actor.setOffset(after));
                        }
                    } else if (mode == MODE_TRIM_IN) {
                        int before = dragOriginValue;
                        int after = take.trimIn;
                        if (before != after) {
                            UndoStack.pushEdit(() -> take.trimIn = before,
                                () -> take.trimIn = after);
                        }
                    } else if (mode == MODE_TRIM_OUT) {
                        int before = dragOriginValue;
                        int after = take.trimOut;
                        if (before != after) {
                            UndoStack.pushEdit(() -> take.trimOut = before,
                                () -> take.trimOut = after);
                        }
                    }
                }
                pendingSave = take;
                return;
            }
            if (take.locked) {
                dragActor = null;
                return;
            }
            int delta = geometry.deltaTicks(ImGui.getMousePosX() - dragOriginX);
            switch (dragMode) {
                case MODE_MOVE -> actor.setOffset(snap(geometry, take, dragOriginValue + delta, cursorTicks));
                case MODE_TRIM_IN -> take.trimIn = Math.max(0,
                    Math.min(take.length() - 1 - Math.max(0, take.trimOut), dragOriginValue + delta));
                case MODE_TRIM_OUT -> take.trimOut = Math.max(0,
                    Math.min(take.length() - 1 - Math.max(0, take.trimIn), dragOriginValue - delta));
                default -> {
                }
            }
            dragTooltip(actor);
            return;
        }

        if (dragActor != null || !ImGui.isWindowHovered() || ReplayUiInput.wantsText()) {
            return;
        }
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        if (mouseY < barTop - 2.0f || mouseY > barBottom + 2.0f) {
            return;
        }
        float left = Math.min(rawStart, rawEnd);
        float right = Math.max(rawEnd, rawStart + 2.0f);
        if (mouseX < left - EDGE_GRAB || mouseX > right + EDGE_GRAB) {
            return;
        }

        boolean wide = right - left > EDGE_GRAB * 3.0f;
        int mode = MODE_MOVE;
        if (wide && mouseX <= left + EDGE_GRAB) {
            mode = MODE_TRIM_IN;
        } else if (wide && mouseX >= right - EDGE_GRAB) {
            mode = MODE_TRIM_OUT;
        }
        ImGui.setMouseCursor(mode == MODE_MOVE ? 2 : 4);
        ImGui.setTooltip(summary(actor));

        if (ImGui.isMouseDoubleClicked(0)) {
            pendingSeek = take.firstTick() + actor.offset();
            return;
        }
        if (ImGui.isMouseClicked(1)) {
            ImGui.openPopup("##ActorPopup");
            return;
        }
        if (ImGui.isMouseClicked(0)) {
            if (actor.take.locked) {
                return;
            }
            dragActor = take.id;
            dragMode = mode;
            dragOriginX = mouseX;
            dragOriginValue = switch (mode) {
                case MODE_TRIM_IN -> take.trimIn;
                case MODE_TRIM_OUT -> take.trimOut;
                default -> actor.offset();
            };
        }
    }

    private static int snap(TimelineGeometry geometry, ActorTake take, int offset, int cursorTicks) {
        float scale = geometry.pixelsPerTick();
        if (scale <= 0.0f) {
            return offset;
        }
        int first = take.firstTick();
        int desiredStart = first + offset;
        List<Integer> candidates = new ArrayList<>();
        candidates.add(cursorTicks);
        for (Actor other : FlashbackActors.ACTORS.actors()) {
            if (other.take == take) {
                continue;
            }
            candidates.add(other.take.firstTick() + other.offset());
            candidates.add(other.take.lastTick() + other.offset());
        }
        int[] array = candidates.stream().mapToInt(Integer::intValue).toArray();
        int snappedStart = TimelineOps.snapStart(desiredStart, array, scale, SNAP_PIXELS);
        if (snappedStart == desiredStart) {
            int startDelta = cursorTicks - (take.firstTick() + offset);
            int endDelta = cursorTicks - (take.lastTick() + offset);
            int best = Math.abs(startDelta) <= Math.abs(endDelta) ? startDelta : endDelta;
            if (Math.abs(best) * scale <= SNAP_PIXELS) {
                return offset + best;
            }
            return offset;
        }
        return snappedStart - first;
    }

    private static void dragTooltip(Actor actor) {
        int offset = actor.offset();
        String seconds = String.format("%.2fs", offset / 20.0f);
        ImGui.setTooltip(actor.displayName() + "\noffset " + offset + " ticks (" + seconds + ")"
            + "\nlength " + (actor.take.lastTick() - actor.take.firstTick() + 1) + " ticks");
    }

    private static void mark(ImDrawList drawList, float x, float midY, float halfHeight,
                             float laneLeft, float laneRight, int alpha) {
        if (x < laneLeft || x > laneRight) {
            return;
        }
        drawList.addRectFilled(x - 1.0f, midY - halfHeight, x + 1.0f, midY + halfHeight,
            ActorColours.withAlpha(WHITE, alpha));
    }

    private static void diamond(ImDrawList drawList, float x, float midY, float half,
                                float laneLeft, float laneRight, int colour) {
        if (x < laneLeft || x > laneRight) {
            return;
        }
        drawList.addQuad(x, midY - half, x + half * 0.7f, midY, x, midY + half,
            x - half * 0.7f, midY, colour);
    }

    /** Peak amplitude of a voice line, 0..1, so the waveform is scaled sanely. */
    private static float peak(short[] samples) {
        int max = 0;
        for (short s : samples) {
            int magnitude = Math.abs((int) s);
            if (magnitude > max) {
                max = magnitude;
            }
        }
        return Math.min(1.0f, max / 16_384.0f);
    }

    /** Voice controls for the selected actor, shown under the death markers. */
    private static void voiceMenu(Actor actor, ActorTake take, float frameHeight) {
        int lines = take.voices.size();
        if (!take.voiceEnabled) {
            ImGui.textDisabled("\uD83D\uDD0E Voice off for this actor");
        } else if (lines == 0) {
            ImGui.textDisabled("\uD83D\uDD0E Voice on, nothing recorded yet");
        } else {
            ImGui.textColored(ActorColours.pack(140, 220, 255, 0xFF),
                "\uD83D\uDD0E Voice: " + lines + (lines == 1 ? " line" : " lines"));
        }

        if (ImGui.button(take.voiceEnabled ? "Disable voice" : "Enable voice",
                Math.max(1.0f, ImGui.getContentRegionAvailX() * 0.5f - 2.0f), frameHeight)) {
            take.voiceEnabled = !take.voiceEnabled;
            pendingSave = take;
        }
        if (ImGui.beginPopup("voice_device_popup")) {
            for (pdx.dev.fbactors.voice.MicDevices.Device device
                    : pdx.dev.fbactors.voice.MicDevices.list()) {
                String name = device.isDefault() ? "Default microphone" : device.name();
                boolean current = take.voiceDevice == null
                    ? device.isDefault() : name.equals(take.voiceDevice);
                if (ImGui.selectable(name, current)) {
                    take.voiceDevice = device.name();
                    pendingSave = take;
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.endPopup();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Which microphone this actor records from."
                + " Only microphones OpenAL reports are listed.");
        }
        if (ImGui.button("Choose microphone",
                Math.max(1.0f, ImGui.getContentRegionAvailX() * 0.5f - 2.0f), frameHeight)) {
            ImGui.openPopup("voice_device_popup");
        }

        if (ImGui.button(take.voiceActivation ? "Voice activation: ON" : "Voice activation: OFF",
                Math.max(1.0f, ImGui.getContentRegionAvailX() * 0.5f - 2.0f), frameHeight)) {
            take.voiceActivation = !take.voiceActivation;
            pendingSave = take;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("With activation on, only speech is recorded."
                + " Turn it off to record continuously while the take runs.");
        }
        if (ImGui.button("Sensitivity: " + take.voiceThreshold + " dB",
                Math.max(1.0f, ImGui.getContentRegionAvailX() * 0.5f - 2.0f), frameHeight)) {
            int index = THRESHOLDS.indexOf(take.voiceThreshold);
            take.voiceThreshold = THRESHOLDS.get(
                (Math.max(0, index) + 1) % THRESHOLDS.size());
            pendingSave = take;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("How loud speech has to be before it counts."
                + " Lower picks up quieter speech and more background noise.");
        }
    }

    private static final java.util.List<Integer> THRESHOLDS = java.util.List.of(-30, -40, -50, -60);

    private static void select(Actor actor) {
        if (actor == null) {
            selected = null;
            MULTI.clear();
            return;
        }
        if (selected == actor && MULTI.size() <= 1) {
            selected = null;
            MULTI.clear();
            return;
        }
        selected = actor;
        MULTI.select(actor.take.id);
    }

    public static void selectViewport(Actor actor) {
        if (actor == null) {
            return;
        }
        selected = actor;
        MULTI.select(actor.take.id);
    }

    private static void toggleSelect(Actor actor) {
        if (actor == null) {
            return;
        }
        MULTI.toggle(actor.take.id);
        selected = MULTI.contains(actor.take.id) ? actor : selectedInManager();
    }

    @Nullable
    private static Actor selectedInManager() {
        for (Actor actor : FlashbackActors.ACTORS.actors()) {
            if (MULTI.contains(actor.take.id)) {
                return actor;
            }
        }
        return null;
    }

    private static void sideMenu(Actor actor, int cursorTicks) {
        ActorTake take = actor.take;
        int colour = colourOf(actor);
        float frameHeight = ImGui.getFrameHeight();

        ImGui.textColored(colour, ICON_MENU + " Flashback menu");
        ImGui.setItemTooltip("Death markers are reversible: \u25c0 \u25b6 changes the way, \u2715 removes it.");

        ImGui.separator();

        voiceMenu(actor, take, frameHeight);

        if (take.deaths.isEmpty()) {
            ImGui.textDisabled("No authored deaths - this actor's death is hit-derived.");
            return;
        }

        ImGui.textUnformatted("Authored deaths");
        List<ActorDeath> sorted = new ArrayList<>(take.deaths);
        sorted.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
        for (int i = 0; i < sorted.size(); i++) {
            ActorDeath death = sorted.get(i);
            ImGui.pushID(ID_BASE + 0x2000 + i);
            ImGui.textColored(kindColour(death.kind()), "\u25c8");
            ImGui.sameLine();
            ImGui.textUnformatted((death.tick() + actor.offset()) + " ticks \u00b7 " + death.kind().label());

            ImGui.sameLine();
            if (ImGui.button("\u25c0", 18.0f, frameHeight)) {
                cycleDeath(actor, death, -1);
            }
            ImGui.setItemTooltip("Cycle the way of death");
            ImGui.sameLine();
            if (ImGui.button("\u25b6", 18.0f, frameHeight)) {
                cycleDeath(actor, death, 1);
            }
            ImGui.setItemTooltip("Cycle the way of death");
            ImGui.sameLine();
            if (ImGui.button("\u2715", 18.0f, frameHeight)) {
                int at = take.deaths.indexOf(death);
                take.deaths.remove(death);
                UndoStack.pushDeathRemove(take, death, at);
                pendingSave = take;
            }
            ImGui.setItemTooltip("Remove this marker - the actor dies naturally again");
            ImGui.popID();
        }
    }

    private static void cycleDeath(Actor actor, ActorDeath death, int direction) {
        ActorTake take = actor.take;
        ActorDeath.Kind[] kinds = ActorDeath.Kind.values();
        int index = death.kind().ordinal() + direction;
        if (index < 0) {
            index = kinds.length - 1;
        }
        if (index >= kinds.length) {
            index = 0;
        }
        int i = take.deaths.indexOf(death);
        if (i >= 0) {
            ActorDeath after = new ActorDeath(death.tick(), kinds[index]);
            take.deaths.set(i, after);
            UndoStack.pushDeathSwap(take, i, death, after);
            pendingSave = take;
        }
    }

    private static int kindColour(ActorDeath.Kind kind) {
        return switch (kind) {
            case LIGHTNING -> ActorColours.pack(255, 224, 160, 255);
            case ANVIL -> ActorColours.pack(176, 176, 180, 255);
            case TNT -> ActorColours.pack(255, 92, 64, 255);
            case LAVA -> ActorColours.pack(255, 128, 32, 255);
            case WITHER -> ActorColours.pack(140, 72, 190, 255);
            case ARROW -> ActorColours.pack(130, 190, 224, 255);
            case DROWN -> ActorColours.pack(64, 128, 224, 255);
            case POISON -> ActorColours.pack(96, 192, 48, 255);
            case VOID -> ActorColours.pack(88, 64, 176, 255);
            case METEOR -> ActorColours.pack(255, 176, 84, 255);
        };
    }

    private static void recordRow(TimelineGeometry geometry, ImDrawList drawList, int cursorTicks,
                                  float textHeight, float lineHeight) {
        ActorMode.Phase phase = ActorMode.phase();
        boolean active = phase != ActorMode.Phase.IDLE;
        float rowTop = ImGui.getCursorScreenPosY();
        float midY = rowTop + textHeight * 0.5f;

        ImGui.setCursorPosX(4.0f);
        String label = ICON_RECORD + " " + switch (phase) {
            case IDLE -> "New actor take";
            case SETUP -> "Setting up actor...";
            case PREP -> "Gearing up - /start to roll";
            case COUNTDOWN -> "Rolling in a moment";
            case RECORDING -> "Recording";
        };

        if (active) {
            ImGui.textColored(RECORD_RED, label);
        } else {
            ImGui.textDisabled(label);
        }
        if (ImGui.isItemClicked(0)) {
            toggleActorMode();
        }
        ImGui.setItemTooltip(active ? "Stop the take" : "Start an actor take");

        if (phase != ActorMode.Phase.RECORDING) {
            return;
        }

        ActorTake take = FlashbackActors.RECORDER.current();
        if (take == null) {
            return;
        }

        float laneLeft = geometry.laneLeft();
        float laneRight = geometry.laneRight();
        if (laneRight <= laneLeft) {
            return;
        }

        float barHeight = Math.max(5.0f, Math.min(lineHeight * 0.38f, 10.0f));
        float barTop = midY - barHeight * 0.5f;
        float barStart = Math.max(laneLeft, geometry.tickX(take.startTick));
        float barEnd = Math.min(laneRight, Math.max(geometry.tickX(cursorTicks), barStart + 2.0f));

        drawList.pushClipRect(laneLeft, geometry.clipTop(), laneRight, geometry.clipBottom(), true);
        float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 220.0);
        drawList.addRectFilled(barStart, barTop, barEnd, barTop + barHeight,
            ActorColours.withAlpha(RECORD_RED, 0x50 + Math.round(pulse * 0x80)), barHeight * 0.5f);
        drawList.popClipRect();
    }

    private static int colourOf(Actor actor) {
        return actor.take.colour != 0 ? actor.take.colour : ActorColours.of(actor.take.id);
    }

    private static String summary(Actor actor) {
        ActorTake take = actor.take;
        StringBuilder text = new StringBuilder(actor.displayName());
        text.append('\n').append(take.lastTick() - take.firstTick() + 1).append(" ticks from ")
            .append(take.firstTick() + actor.offset());
        if (actor.offset() != 0) {
            text.append("\noffset ").append(actor.offset()).append(" ticks");
        }
        if (take.trimIn > 0 || take.trimOut > 0) {
            text.append("\ntrimmed ").append(take.trimIn).append(" / ").append(take.trimOut);
        }
        if (take.locked) {
            text.append("\nlocked");
        }
        if (!take.hits.isEmpty()) {
            text.append('\n').append(take.hits.size()).append(take.hits.size() == 1 ? " hit" : " hits");
        }
        if (take.deathTick() >= 0) {
            text.append("\ndies at ").append(take.deathTick() + actor.offset());
        }
        for (ActorDeath death : take.deaths) {
            text.append('\n').append(death.kind().label()).append(" at ").append(death.tick() + actor.offset());
        }
        text.append("\ndrag to retime, edges to trim, right-click for options");
        return text.toString();
    }

    private static void flush() {
        Actor remove = pendingRemove;
        Actor duplicate = pendingDuplicate;
        ActorTake save = pendingSave;
        int seek = pendingSeek;
        pendingRemove = null;
        pendingDuplicate = null;
        pendingSave = null;
        pendingSeek = -1;

        if (remove == null && duplicate == null && save == null && seek < 0) {
            return;
        }

        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        replay.execute(() -> {
            if (remove != null) {
                UndoStack.pushRemoval(remove.take);
                FlashbackActors.removeActor(remove);
            }
            if (duplicate != null) {
                Actor created = FlashbackActors.duplicateActor(duplicate);
                ActorTake added = created.take;
                UndoStack.pushEdit(
                    () -> replay.execute(() -> {
                        Actor found = FlashbackActors.ACTORS.byTakeId(added.id);
                        if (found != null) {
                            FlashbackActors.removeActor(found);
                        }
                    }),
                    () -> replay.execute(() -> {
                        if (FlashbackActors.ACTORS.byTakeId(added.id) == null) {
                            FlashbackActors.ACTORS.add(added);
                            FlashbackActors.save(added);
                        }
                    }));
            }
            if (save != null) {
                FlashbackActors.save(save);
            }
            if (seek >= 0) {
                replay.goToReplayTick(Math.max(0, seek));
            }
        });
    }

    private static void toggleActorMode() {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        replay.execute(() -> {
            ServerPlayer viewer = FlashbackActors.viewerOf(replay);
            if (viewer == null) {
                return;
            }
            if (ActorMode.isActive()) {
                FlashbackActors.finishTake(replay, viewer);
            } else {
                ActorMode.enterSetup(replay, viewer);
            }
        });
    }

    // ---- phase 1 timeline ops -------------------------------------------------

    /**
     * The selected take ids in selection order. Backs multi-select delete and
     * razor: actions read this, single-take callers keep using
     * {@link #selected()}.
     */
    public static java.util.Set<UUID> selectedIds() {
        return MULTI.ids();
    }

    /** Selects every actor. {@code Ctrl+A}. */
    public static void selectAll() {
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        if (actors.isEmpty()) {
            return;
        }
        MULTI.clear();
        for (Actor actor : actors) {
            MULTI.add(actor.take.id);
        }
        selected = actors.get(actors.size() - 1);
    }

    private static List<Actor> multiTargets() {
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        if (MULTI.isEmpty()) {
            return selected != null && actors.contains(selected)
                ? List.of(selected) : List.of();
        }
        List<Actor> out = new ArrayList<>();
        for (Actor actor : actors) {
            if (MULTI.contains(actor.take.id)) {
                out.add(actor);
            }
        }
        return out;
    }

    /**
     * Razors every selected take at the playhead.
     *
     * <p>Thin wrapper: {@link TimelineOps#razorRelative} converts the replay
     * tick, {@link ClipEditor#splitAt} does the split, and the manager swap
     * plus an {@link UndoStack} edit run on the server thread. Locked takes
     * and edge playheads are skipped, never partial.
     *
     * @param cursorTicks the playhead in replay ticks
     */
    public static void razorSelectedAt(int cursorTicks) {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        List<Actor> targets = new ArrayList<>(multiTargets());
        if (targets.isEmpty()) {
            return;
        }
        replay.execute(() -> {
            for (Actor actor : targets) {
                ActorTake take = actor.take;
                if (!TimelineOps.canEdit(take.locked)) {
                    continue;
                }
                int relative = TimelineOps.razorRelative(
                    take.startTick, actor.offset(), cursorTicks, take.length());
                if (relative < 0) {
                    continue;
                }
                ActorTake[] halves = ClipEditor.splitAt(take, relative);
                FlashbackActors.ACTORS.remove(actor);
                UUID replayId = FlashbackActors.loadedReplayIdOrNull();
                if (replayId != null) {
                    pdx.dev.fbactors.take.TakeStore.delete(replayId, take.id);
                }
                Actor first = FlashbackActors.ACTORS.add(halves[0]);
                Actor second = FlashbackActors.ACTORS.add(halves[1]);
                FlashbackActors.save(halves[0]);
                FlashbackActors.save(halves[1]);
                MULTI.remove(take.id);
                MULTI.add(halves[0].id);
                MULTI.add(halves[1].id);
                UndoStack.pushEdit(
                    () -> replay.execute(() -> {
                        Actor a = FlashbackActors.ACTORS.byTakeId(halves[0].id);
                        Actor b = FlashbackActors.ACTORS.byTakeId(halves[1].id);
                        if (a != null) {
                            FlashbackActors.ACTORS.remove(a);
                        }
                        if (b != null) {
                            FlashbackActors.ACTORS.remove(b);
                        }
                        FlashbackActors.ACTORS.add(take);
                        FlashbackActors.save(take);
                    }),
                    () -> replay.execute(() -> {
                        Actor original = FlashbackActors.ACTORS.byTakeId(take.id);
                        if (original != null) {
                            FlashbackActors.ACTORS.remove(original);
                        }
                        if (FlashbackActors.ACTORS.byTakeId(halves[0].id) == null) {
                            FlashbackActors.ACTORS.add(halves[0]);
                        }
                        if (FlashbackActors.ACTORS.byTakeId(halves[1].id) == null) {
                            FlashbackActors.ACTORS.add(halves[1]);
                        }
                        FlashbackActors.save(halves[0]);
                        FlashbackActors.save(halves[1]);
                    }));
                selected = second;
                if (first == null) {
                    selected = second;
                }
            }
        });
    }

    /**
     * Deletes every selected take and ripples later clips to close the gap.
     *
     * <p>The gap closed is the deleted span's own
     * {@code [firstTick + offset, lastTick + offset]}; surviving clips at or
     * after it shift by {@link TimelineOps#rippleShift}, with an undo edit
     * per surviving clip plus a removal entry per deleted take.
     */
    public static void rippleDeleteSelected() {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        List<Actor> targets = new ArrayList<>(multiTargets());
        if (targets.isEmpty()) {
            return;
        }
        replay.execute(() -> {
            targets.sort((a, b) -> Integer.compare(
                a.take.firstTick() + a.offset(), b.take.firstTick() + b.offset()));
            for (Actor victim : targets) {
                if (!TimelineOps.canEdit(victim.take.locked)) {
                    continue;
                }
                int deletedStart = victim.take.firstTick() + victim.offset();
                int deletedEnd = victim.take.lastTick() + victim.offset();
                ActorTake removed = victim.take;
                pdx.dev.fbactors.undo.UndoStack.pushRemoval(removed);
                FlashbackActors.removeActor(victim);
                MULTI.remove(removed.id);
                for (Actor survivor : FlashbackActors.ACTORS.actors()) {
                    int shift = TimelineOps.rippleShift(
                        survivor.take.firstTick() + survivor.offset(),
                        deletedStart, deletedEnd);
                    if (shift == 0 || !TimelineOps.canEdit(survivor.take.locked)) {
                        continue;
                    }
                    int before = survivor.offset();
                    int after = before + shift;
                    survivor.setOffset(after);
                    FlashbackActors.save(survivor.take);
                    UndoStack.pushEdit(() -> survivor.setOffset(before),
                        () -> survivor.setOffset(after));
                }
            }
            selected = selectedInManager();
        });
    }

    /** Toggles the track lock on every selected take. */
    public static void toggleLockSelected() {
        List<Actor> targets = multiTargets();
        if (targets.isEmpty()) {
            return;
        }
        ReplayServer replay = Flashback.getReplayServer();
        for (Actor actor : targets) {
            boolean before = actor.take.locked;
            boolean after = !before;
            actor.take.locked = after;
            UndoStack.pushEdit(() -> actor.take.locked = before,
                () -> actor.take.locked = after);
            if (replay != null) {
                replay.execute(() -> FlashbackActors.save(actor.take));
            }
        }
    }

    /** Copies the selected take's whole camera track onto the key clipboard. */
    public static void copyKeysSelected() {
        Actor actor = selected;
        if (actor == null) {
            return;
        }
        keyClipboard = new ArrayList<>(actor.take.cameraTrack.keyframes);
    }

    /**
     * Pastes the key clipboard onto the selected take at the playhead.
     *
     * <p>Keys are retimed by {@link TimelineOps#pasteKeys} so the earliest
     * lands on the playhead; locked takes refuse.
     */
    public static void pasteKeysSelected(int cursorTicks) {
        Actor actor = selected;
        if (actor == null || keyClipboard.isEmpty()) {
            return;
        }
        if (!TimelineOps.canEdit(actor.take.locked)) {
            return;
        }
        List<CameraKeyframe> pasted = TimelineOps.pasteKeys(keyClipboard, cursorTicks);
        if (pasted.isEmpty()) {
            return;
        }
        int added = pasted.size();
        for (CameraKeyframe key : pasted) {
            actor.take.cameraTrack.add(key);
        }
        UndoStack.pushEdit(
            () -> actor.take.cameraTrack.keyframes.removeAll(pasted),
            () -> {
                if (!actor.take.cameraTrack.keyframes.containsAll(pasted)) {
                    for (CameraKeyframe key : pasted) {
                        actor.take.cameraTrack.add(key);
                    }
                }
            });
        ReplayServer replay = Flashback.getReplayServer();
        if (replay != null) {
            replay.execute(() -> FlashbackActors.save(actor.take));
        }
    }

    // ---- hotkey entry points ----------------------------------------------------

    /**
     * Toggles a take from the keyboard. {@code Ctrl+R}.
     *
     * <p>The hotkey subsystem may not call the private toggle directly, so
     * the one implementation is reachable from both the timeline button and
     * the keyboard - a second copy of this body would be a second thing to
     * forget when the toggle's behaviour changes.
     */
    public static void toggleRecording() {
        toggleActorMode();
    }

    /**
     * Enters take setup from the keyboard. {@code Ctrl+N}.
     *
     * <p>Same replay-and-viewer shape as {@link #toggleActorMode}, for the
     * same reason: the hotkey runs on the client thread and the mode change
     * belongs on the server thread.
     */
    public static void newActor() {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        replay.execute(() -> {
            ServerPlayer viewer = FlashbackActors.viewerOf(replay);
            if (viewer != null) {
                ActorMode.enterSetup(replay, viewer);
            }
        });
    }

    /**
     * Opens the inline rename field for the selected actor. {@code F2}.
     *
     * @return true when a rename was started
     */
    public static boolean renameSelected() {
        Actor actor = selected;
        if (actor == null) {
            return false;
        }
        beginRename(actor);
        return true;
    }

    /**
     * Moves the selection by one row. {@code Ctrl+]} / {@code Ctrl+[}.
     *
     * <p>Clamps at both ends rather than wrapping: the actor list is short
     * enough that wrapping would feel like the selection teleported, and a
     * clamp says "there is no further actor" the way the cursor does at the
     * end of a line.
     *
     * @param delta +1 for next, -1 for previous
     */
    public static void selectNext(int delta) {
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        if (actors.isEmpty()) {
            return;
        }
        int index = selected == null
            ? (delta > 0 ? -1 : 0)
            : actors.indexOf(selected);
        int next = Mth.clamp(index + delta, 0, actors.size() - 1);
        selected = actors.get(next);
        MULTI.select(selected.take.id);
    }

    /**
     * The currently selected actor, or null.
     *
     * <p>Single-take actions keep using this; multi-take actions (razor,
     * ripple-delete) read {@link #selectedIds()}.
     */
    @Nullable
    public static Actor selected() {
        return selected;
    }
}
