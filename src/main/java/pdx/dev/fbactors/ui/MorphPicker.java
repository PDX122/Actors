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
import pdx.dev.fbactors.morph.MorphRegistry;
import pdx.dev.fbactors.morph.MorphSearch;
import pdx.dev.fbactors.morph.MorphSelection;
import pdx.dev.fbactors.settings.FbSettings;

import java.util.List;
import java.util.UUID;


public final class MorphPicker {


    private static final String ID = "###ActorMorphPicker";


    private static final ImBoolean OPEN = new ImBoolean(true);


    @Nullable
    private static Actor target;


    @Nullable
    private static Actor pending;

    private static ImString search = ImGuiHelper.createResizableImString("");
    private static String category = "";
    private static int page;

    private static final float[] scaleBuffer = {1.0f};
    private static final float[] alphaBuffer = {1.0f};
    private static final boolean[] hideName = {false};


    @Nullable
    private static String status;

    private MorphPicker() {
    }


    public static void open(@Nullable Actor actor) {
        if (actor != null) {
            pending = actor;
        }
    }


    public static void close() {
        pending = null;
        target = null;
        OPEN.set(false);
    }


    public static boolean isOpen(@Nullable Actor actor) {
        return target != null && target == actor;
    }


    private static void begin(Actor actor) {
        target = actor;
        OPEN.set(true);
        search = ImGuiHelper.createResizableImString("");
        category = "";
        page = 0;
        status = null;
        syncTuning(actor);
    }


    public static void render() {
        Actor requested = pending;
        if (requested != null) {
            pending = null;
            begin(requested);
        }

        Actor actor = target;
        if (actor == null) {
            return;
        }

        ImGui.setNextWindowSize(340.0f, 420.0f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(150.0f, 150.0f, ImGuiCond.FirstUseEver);

        if (ImGui.begin("Change morph - " + actor.displayName() + ID, OPEN)) {
            if (!FbSettings.morphEnabled()) {
                ImGui.textDisabled("Morphs are disabled. Turn them back on in FBA Settings.");
                ImGui.separator();
                if (ImGui.button("Close##ActorMorphClose")) {
                    close();
                }
            } else {
                currentMorph(actor);
                ImGui.separator();
                tuning(actor);
                ImGui.separator();
                filterRow();
                ImGui.separator();
                list(actor);
                ImGui.separator();
                if (status != null) {
                    ImGui.textDisabled(status);
                }
                if (ImGui.button("None##ActorMorphNone")) {
                    Morph.store().clear(actor.take.id);
                    Morph.save();
                    status = actor.displayName() + " is a player again.";
                }
                ImGui.sameLine();
                if (ImGui.button("Close##ActorMorphClose")) {
                    close();
                }
            }
        }
        ImGui.end();

        if (!OPEN.get()) {
            pending = null;
            target = null;
        }
    }


    private static void currentMorph(Actor actor) {
        MorphSelection selection = Morph.selectionFor(actor.take.id);
        if (selection == null || !selection.isMorphed()) {
            ImGui.textUnformatted("Currently: player (no morph)");
            return;
        }
        MorphDefinition def = Morph.registry().byId(selection.morphId());
        String name = def == null ? selection.morphId() : def.displayName();
        ImGui.textUnformatted("Currently: " + name + "  x" + selection.scale());
        if (def == null) {
            ImGui.textDisabled("That morph is no longer in the catalogue; picking anything replaces it.");
        }
    }


    private static void syncTuning(Actor actor) {
        MorphSelection selection = Morph.selectionFor(actor.take.id);
        scaleBuffer[0] = selection == null ? MorphSelection.DEFAULT_SCALE : selection.scale();
        alphaBuffer[0] = selection == null ? MorphSelection.DEFAULT_ALPHA : selection.alpha();
        hideName[0] = selection != null && selection.hideName();
    }


    private static void tuning(Actor actor) {
        MorphSelection current = Morph.selectionFor(actor.take.id);
        if (current == null || !current.isMorphed()) {
            ImGui.textDisabled("Pick a morph below, then tune it here.");
            return;
        }
        ImGui.setNextItemWidth(ImGui.getWindowWidth() - 16.0f);
        if (ImGui.sliderFloat("Size##ActorMorphScale", scaleBuffer,
                MorphSelection.MIN_SCALE, MorphSelection.MAX_SCALE, "x%.2f")) {
            Morph.store().set(actor.take.id, current.withScale(scaleBuffer[0]));
            Morph.save();
        }
        ImGui.setNextItemWidth(ImGui.getWindowWidth() - 16.0f);
        if (ImGui.sliderFloat("Opacity##ActorMorphAlpha", alphaBuffer, 0.0f, 1.0f, "%.2f")) {
            Morph.store().set(actor.take.id, Morph.selectionFor(actor.take.id).withAlpha(alphaBuffer[0]));
            Morph.save();
        }
        if (ImGui.checkbox("Hide name##ActorMorphHideName", hideName[0])) {
            hideName[0] = !hideName[0];
            Morph.store().set(actor.take.id,
                Morph.selectionFor(actor.take.id).withNameHidden(hideName[0]));
            Morph.save();
        }
    }


    private static void filterRow() {
        ImGui.setNextItemWidth(ImGui.getWindowWidth() - 16.0f);
        if (ImGui.inputTextWithHint("##ActorMorphSearch", "Filter by name", search, 64)) {
            page = 0;
        }
        for (String known : MorphSearch.knownCategories()) {
            if (ImGui.button(labelFor(known) + "##ActorMorphCat" + known)) {
                category = known.equals(category) ? "" : known;
                page = 0;
            }
            ImGui.sameLine();
        }
        ImGui.textDisabled(category.isEmpty() ? "All categories" : labelFor(category));
    }

    private static String labelFor(String categoryId) {
        if (categoryId.isEmpty()) {
            return "All";
        }
        return categoryId.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
            + categoryId.substring(1);
    }


    private static void list(Actor actor) {
        MorphRegistry registry = Morph.registry();
        String query = ImGuiHelper.getString(search);
        int pages = MorphSearch.pageCount(registry, query, category, MorphSearch.PAGE_SIZE);
        if (page >= pages) {
            page = Math.max(0, pages - 1);
        }
        List<MorphDefinition> rows =
            MorphSearch.search(registry, query, category, page, MorphSearch.PAGE_SIZE);
        if (rows.isEmpty()) {
            ImGui.textDisabled("Nothing matches that filter.");
            return;
        }

        MorphSelection current = Morph.selectionFor(actor.take.id);
        String currentId = current == null ? null : current.morphId();
        for (int i = 0; i < rows.size(); i++) {
            MorphDefinition morph = rows.get(i);
            ImGui.pushID(0x1F0BAC70 + i);
            boolean selected = morph.id().equals(currentId);
            if (ImGui.selectable(rowLabel(morph), selected)) {
                applyMorph(actor, morph);
                syncTuning(actor);
            }
            if (!MorphCompatibility.canApply(morph, true)) {
                ImGui.sameLine();
                ImGui.textDisabled("(needs renderer support)");
            }
            ImGui.popID();
        }

        ImGui.separator();
        if (ImGui.button("<##ActorMorphPrev") && page > 0) {
            page--;
        }
        ImGui.sameLine();
        ImGui.textDisabled("Page " + (page + 1) + " of " + pages);
        ImGui.sameLine();
        if (ImGui.button(">##ActorMorphNext") && page < pages - 1) {
            page++;
        }
    }


    private static String rowLabel(MorphDefinition morph) {
        StringBuilder row = new StringBuilder(morph.displayName())
            .append("  (").append(morph.id()).append(")  x").append(morph.scale());
        if (morph.quadruped()) {
            row.append(", quadruped");
        }
        if (morph.elytraCapable()) {
            row.append(", flies");
        }
        return row.toString();
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

