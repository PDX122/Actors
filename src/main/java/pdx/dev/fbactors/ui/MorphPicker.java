package pdx.dev.fbactors.ui;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.morph.Morph;
import pdx.dev.fbactors.morph.MorphCompatibility;
import pdx.dev.fbactors.morph.MorphDefinition;
import pdx.dev.fbactors.morph.MorphPreviews;
import pdx.dev.fbactors.morph.MorphSearch;
import pdx.dev.fbactors.morph.MorphSelection;
import pdx.dev.fbactors.settings.FbSettings;
import java.util.UUID;

public final class MorphPicker {
    private static final ImBoolean OPEN = new ImBoolean(false);
    private static final int PAGE_SIZE = 12;
    private static Actor target;
    private static Actor pending;
    private static ImString search = ImGuiHelper.createResizableImString("");
    private static String category = "";
    private static String preview;
    private static String status;
    private static int page;
    private static final float[] scaleBuffer = {1};
    private static final float[] alphaBuffer = {1};
    private static boolean hideName;
    /** 3D mob preview is disabled until the flicker/unavailable issues are fixed. */
    private static final boolean PREVIEW_DISABLED = true;
    private MorphPicker() {}

    public static void open(@Nullable Actor actor) { if (actor != null) pending = actor; }
    public static void close() { pending = null; target = null; OPEN.set(false); MorphPreviews.close(); }
    public static boolean isOpen(@Nullable Actor actor) { return target != null && target == actor; }

    private static void begin(Actor actor) {
        target = actor;
        OPEN.set(true);
        search = ImGuiHelper.createResizableImString("");
        category = "";
        preview = null;
        page = 0;
        status = null;
        syncTuning(actor);
    }

    public static void render() {
        try {
            renderInner();
        } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Morph picker overlay failed; closing it", e);
            close();
        }
    }

    private static void renderInner() {
        if (pending != null) { Actor next = pending; pending = null; begin(next); }
        Actor actor = target;
        // Preview disabled: release any cached preview GL resources and never
        // open new ones (see preview()). Re-enable by passing through OPEN.
        MorphPreviews.beginFrame(false);
        if (actor == null) return;
        ImGui.setNextWindowSize(560, 440, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(150, 150, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Morphs - " + actor.displayName() + "###ActorMorphPicker", OPEN)) {
            if (!FbSettings.morphEnabled()) {
                ImGui.textWrapped("Morphs are disabled. Enable them in FBA Settings.");
            } else {
                var current = Morph.selectionFor(actor.take.id);
                var definition = current == null ? null : Morph.registry().byId(current.morphId());
                ImGui.textDisabled("Current: " + (definition == null ? "Player" : definition.displayName()));
                if (ImGui.beginTabBar("##MorphTabs")) {
                    if (ImGui.beginTabItem("Browse")) {
                        filterRow();
                        list(actor);
                        ImGui.endTabItem();
                    }
                    if (ImGui.beginTabItem("Tuning")) {
                        ImGui.beginDisabled(actor.take.locked);
                        tuning(actor);
                        ImGui.endDisabled();
                        ImGui.endTabItem();
                    }
                    ImGui.endTabBar();
                }
                if (status != null) ImGui.textWrapped(status);
                ImGui.separator();
                ImGui.beginDisabled(actor.take.locked || current == null || !current.isMorphed());
                if (ImGui.button("Restore player")) {
                    Morph.store().clear(actor.take.id);
                    Morph.save();
                    syncTuning(actor);
                    status = actor.displayName() + " is a player again.";
                }
                ImGui.endDisabled();
                ImGui.sameLine();
                if (ImGui.button("Close")) close();
            }
        }
        ImGui.end();
        if (!OPEN.get()) { pending = null; target = null; }
    }

    private static void syncTuning(Actor actor) {
        var selection = Morph.selectionFor(actor.take.id);
        scaleBuffer[0] = selection == null ? MorphSelection.DEFAULT_SCALE : selection.scale();
        alphaBuffer[0] = selection == null ? MorphSelection.DEFAULT_ALPHA : selection.alpha();
        hideName = selection != null && selection.hideName();
    }

    private static void tuning(Actor actor) {
        var current = Morph.selectionFor(actor.take.id);
        if (current == null || !current.isMorphed()) {
            ImGui.textWrapped("Apply a morph from Browse to adjust its size and opacity.");
            return;
        }
        ImGui.setNextItemWidth(-90);
        if (ImGui.sliderFloat("Size", scaleBuffer, MorphSelection.MIN_SCALE, MorphSelection.MAX_SCALE, "x%.2f")) {
            Morph.store().set(actor.take.id, current.withScale(scaleBuffer[0]));
            Morph.save();
        }
        ImGui.setNextItemWidth(-90);
        if (ImGui.sliderFloat("Opacity", alphaBuffer, 0, 1, "%.2f")) {
            Morph.store().set(actor.take.id, Morph.selectionFor(actor.take.id).withAlpha(alphaBuffer[0]));
            Morph.save();
        }
        if (ImGui.checkbox("Hide name", hideName)) {
            hideName = !hideName;
            Morph.store().set(actor.take.id, Morph.selectionFor(actor.take.id).withNameHidden(hideName));
            Morph.save();
        }
    }

    private static void filterRow() {
        float width = ImGui.getContentRegionAvailX();
        boolean inline = width >= 300;
        ImGui.setNextItemWidth(inline ? Math.max(1, width - 136) : -1);
        if (ImGui.inputTextWithHint("##MorphSearch", "Search mobs...", search)) page = 0;
        if (inline) ImGui.sameLine();
        ImGui.setNextItemWidth(inline ? 128 : -1);
        if (ImGui.beginCombo("##MorphCategory", labelFor(category))) {
            if (ImGui.selectable("All", category.isEmpty())) { category = ""; page = 0; }
            for (String known : MorphSearch.knownCategories()) {
                if (ImGui.selectable(labelFor(known), category.equals(known))) { category = known; page = 0; }
            }
            ImGui.endCombo();
        }
    }

    private static String labelFor(String id) {
        return id.isEmpty() ? "All" : id.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + id.substring(1);
    }

    private static void list(Actor actor) {
        var registry = Morph.registry();
        String query = ImGuiHelper.getString(search);
        int pages = MorphSearch.pageCount(registry, query, category, PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        var rows = MorphSearch.search(registry, query, category, page, PAGE_SIZE);
        float width = ImGui.getContentRegionAvailX();
        boolean beside = width >= 420;
        // Clamp: ImGui draw commands with a zero/negative extent produce a
        // zero-area clip rect, and Flashback's overlay renderer throws
        // (IllegalArgumentException: Scissor size must be >0) on those.
        float listW = beside ? Math.max(1, width - 188) : 0;
        if (ImGui.beginChild("##MorphList", listW, 240, true)) {
            if (rows.isEmpty()) ImGui.textDisabled("No matching mobs.");
            for (var morph : rows) {
                if (ImGui.selectable(morph.displayName() + "##" + morph.id(), morph.id().equals(preview))) preview = morph.id();
                ImGui.setItemTooltip(morph.id() + " / " + labelFor(morph.kind()) + "\nClick to preview. Apply changes the actor.");
            }
        }
        ImGui.endChild();
        if (beside) ImGui.sameLine();
        ImGui.beginGroup();
        preview(actor, beside ? 180 : Math.max(1, Math.min(180, width)));
        ImGui.endGroup();
        ImGui.beginDisabled(page == 0);
        if (ImGui.button("<##MorphPrev")) page--;
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.textDisabled("Page " + (page + 1) + " of " + Math.max(1, pages));
        ImGui.sameLine();
        ImGui.beginDisabled(page + 1 >= pages);
        if (ImGui.button(">##MorphNext")) page++;
        ImGui.endDisabled();
    }

    private static void preview(Actor actor, float width) {
        var definition = Morph.registry().byId(preview);
        if (definition == null) {
            ImGui.textWrapped("Click a mob to see its 3D model here.");
            return;
        }
        ImGui.textUnformatted(definition.displayName());
        ImGui.textDisabled("3D preview disabled for now - coming back in a later update.");
        ImGui.textWrapped("Select a mob below and Apply morph to use it.");

        ImGui.beginDisabled(actor.take.locked);
        if (ImGui.button("Apply morph", Math.max(1, width), 0)) { applyMorph(actor, definition); syncTuning(actor); }
        ImGui.endDisabled();
    }

    private static void applyMorph(Actor actor, MorphDefinition morph) {
        UUID takeId = actor.take.id;
        MorphSelection current = Morph.selectionFor(takeId);
        MorphSelection next = current == null || !current.isMorphed()
            ? MorphSelection.of(takeId, morph.id())
            : current.withMorph(morph.id());
        Morph.store().set(takeId, next);
        Morph.save();
        status = actor.displayName() + " is now \"" + morph.displayName() + "\".";
        if (!MorphCompatibility.canApply(morph, true)) {
            MorphCompatibility.report(morph);
            status += " Drawn at player size until renderer support lands.";
        }
    }
}

