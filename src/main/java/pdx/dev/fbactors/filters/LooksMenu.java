package pdx.dev.fbactors.filters;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiDragDropFlags;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import java.util.ArrayList;
import java.util.Collections;

public final class LooksMenu {
    private static final ImBoolean OPEN = new ImBoolean(false);
    private static final ImString SEARCH = new ImString(128);
    private static final ImString PRESET_NAME = new ImString(64);
    private static EffectClip editing;
    private static java.util.UUID lastClip;
    private static String category = "All";
    private static String selected = "aurora";
    private static String savedPreset = "";
    private static String dragPreviewLoggedId;
    private static boolean dragPreviewArmed;
    private LooksMenu() {}

    public static void open() { OPEN.set(true); }

    public static void renderMenuItem() {
        if (ImGui.menuItem("Looks...", OPEN.get())) open();
    }

    public static void renderWindow() {
        if (!OPEN.get()) return;
        ImGui.setNextWindowSize(460, 520, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Looks###ActorLooks", OPEN)) {
            if (com.moulberry.flashback.Flashback.getReplayServer() == null) {
                ImGui.textWrapped("Open a replay to preview and save looks.");
            } else renderPicker();
        }
        ImGui.end();
    }

    public static void renderClip(EffectClip clip, String section) {
        if (!clip.id.equals(lastClip)) {
            lastClip = clip.id;
            if (!clip.look.filters.isEmpty()) selected = clip.look.filters.getFirst();
        }
        editing = clip;
        try {
            switch (section) {
                case "Grade" -> renderGrade();
                case "Presets" -> renderPresets();
                default -> renderContents();
            }
        } finally { editing = null; }
    }

    private static LookSettings settings() { return editing == null ? Looks.settings() : editing.look; }
    private static void changed() { if (editing == null) Looks.changed(); else Effects.changed(); }
    private static void apply(LookSettings look) {
        if (editing == null) Looks.apply(look);
        else { editing.look = look.copy(); editing.look.normalize(); Effects.changed(); }
    }

    public static void renderContents() {
        LookSettings look = settings();
        if (!look.filters.contains(selected) && !look.filters.isEmpty()) selected = look.filters.getFirst();
        if (ImGui.checkbox("Enable look", look.enabled)) {
            look.enabled = !look.enabled;
            Looks.compare = false;
            changed();
        }
        ImGui.sameLine();
        if (ImGui.checkbox("Show original", Looks.compare)) Looks.compare = !Looks.compare;
        ImGui.setItemTooltip("Compare the unfiltered scene. Export still uses your enabled look.");
        look.intensity = slider("Intensity", look.intensity, 0, 1);
        ImGui.spacing();
        ImGui.textDisabled(look.filters.size() + " / " + LookSettings.MAX_FILTERS + " filters. Applied top to bottom.");
        for (int i = 0; i < look.filters.size(); i++) {
            String id = look.filters.get(i);
            if (ImGui.selectable((i + 1) + ". " + FilterCatalog.find(id).name(), id.equals(selected))) selected = id;
        }
        if (look.filters.isEmpty()) ImGui.textDisabled("No filter selected. Grading controls still work.");
        int at = look.filters.indexOf(selected);
        float third = Math.max(1, (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3);
        ImGui.beginDisabled(at <= 0);
        if (ImGui.button("Move up", third, 0)) { Collections.swap(look.filters, at, at - 1); changed(); }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(at < 0 || at >= look.filters.size() - 1);
        if (ImGui.button("Move down", third, 0)) { Collections.swap(look.filters, at, at + 1); changed(); }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(at < 0);
        if (ImGui.button("Remove", third, 0)) { look.filters.remove(selected); changed(); }
        ImGui.endDisabled();
        if (look.filters.contains(selected)) {
            for (var param : FilterCatalog.params(selected)) {
                float value = slider(param.label(), look.parameter(selected, param), param.min(), param.max());
                look.parameter(selected, param, value);
            }
        }
        ImGui.spacing();
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##AddFilter", "Add a look...")) {
            for (var entry : FilterCatalog.entries()) {
                ImGui.beginDisabled(look.filters.contains(entry.id()) || look.filters.size() >= LookSettings.MAX_FILTERS);
                if (ImGui.selectable(entry.name(), false)) {
                    if (look.add(entry.id())) { selected = entry.id(); Looks.compare = false; changed(); }
                }
                ImGui.setItemTooltip(entry.description());
                ImGui.endDisabled();
            }
            ImGui.endCombo();
        }
        var entry = FilterCatalog.find(selected);
        if (entry != null && look.filters.contains(selected)) {
            ImGui.textWrapped(entry.description());
            if (ImGui.collapsingHeader("Preview")) {
                Looks.requestPreviews();
                thumbnail(entry, Math.max(1, Math.min(240, ImGui.getContentRegionAvailX())));
                ImGui.textDisabled("Demo photo at full strength.");
            }
        }
    }

    private static void renderGrade() {
        LookSettings look = settings();
        look.exposure = slider("Exposure", look.exposure, -3, 3);
        look.contrast = slider("Contrast", look.contrast, 0.5f, 1.8f);
        look.saturation = slider("Saturation", look.saturation, 0, 2.5f);
        look.temperature = slider("Temperature", look.temperature, -1, 1);
        look.tint = slider("Tint", look.tint, -1, 1);
        look.vignette = slider("Vignette", look.vignette, 0, 1);
        look.grain = slider("Film grain", look.grain, 0, 0.2f);
        if (ImGui.button("Reset grade")) {
            look.exposure = look.temperature = look.tint = look.vignette = look.grain = 0;
            look.contrast = look.saturation = 1;
            changed();
        }
    }

    private static void renderPresets() {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##LookPresetName", "Preset name...", PRESET_NAME);
        if (ImGui.button("Save preset")) Looks.savePreset(PRESET_NAME.get(), settings());
        ImGui.setItemTooltip("An existing name replaces that saved preset.");
        if (ImGui.beginCombo("Saved", savedPreset.isEmpty() ? "Choose a saved look..." : savedPreset)) {
            for (String name : Looks.presets().keySet()) {
                if (ImGui.selectable(name, name.equals(savedPreset))) {
                    savedPreset = name;
                    apply(Looks.presets().get(name));
                }
            }
            ImGui.endCombo();
        }
        ImGui.beginDisabled(!Looks.presets().containsKey(savedPreset));
        if (ImGui.button("Remove saved preset")) { Looks.deletePreset(savedPreset); savedPreset = ""; }
        ImGui.endDisabled();

        if (ImGui.collapsingHeader("Advanced")) {
            if (ImGui.button("Reset this look")) apply(new LookSettings());
            if (ImGui.button("Reload shaders")) Looks.reload();
            ImGui.textDisabled("Effects run at the native render size. The interface stays unfiltered.");
        }
    }

    public static String selectedFilter() { return selected; }

    public static void renderPicker() {
        Looks.requestPreviews();
        ImGui.textDisabled("Drag onto an actor or Misc.");
        ImGui.setItemTooltip("Actor tracks affect that actor. Misc affects the whole scene. Double click a clip to edit it.");
        dragPreviewArmed = false;
        browser();
        if (!dragPreviewArmed) {
            // No drag source ran this frame: our drag (if any) ended, so the
            // next drag counts as new and logs its preview geometry again.
            dragPreviewLoggedId = null;
        }
    }

    private static void browser() {
        float width = ImGui.getContentRegionAvailX();
        boolean inline = width >= 300;
        ImGui.setNextItemWidth(inline ? Math.max(1, width - 128 - ImGui.getStyle().getItemSpacingX()) : -1);
        ImGui.inputTextWithHint("##FilterSearch", "Search looks...", SEARCH);
        if (inline) ImGui.sameLine();
        ImGui.setNextItemWidth(inline ? 128 : -1);
        if (ImGui.beginCombo("##FilterCategory", category)) {
            var categories = new ArrayList<String>();
            categories.add("All");
            FilterCatalog.entries().stream().map(FilterCatalog.Entry::category).distinct().forEach(categories::add);
            for (String name : categories) if (ImGui.selectable(name, category.equals(name))) category = name;
            ImGui.endCombo();
        }
        var matches = FilterCatalog.search(SEARCH.get(), category);
        if (matches.isEmpty()) ImGui.textDisabled("No matching looks.");
        if (ImGui.beginChild("##FilterBrowser", 0, 0, false)) {
            float available = ImGui.getContentRegionAvailX();
            int columns = Math.max(1, (int) ((available + ImGui.getStyle().getItemSpacingX()) / 150));
            float tile = Math.max(1, (available - ImGui.getStyle().getItemSpacingX() * (columns - 1)) / columns);
            int index = 0;
            for (var entry : matches) {
                if (index++ % columns != 0) ImGui.sameLine();
                ImGui.beginGroup();
                if (thumbnail(entry, tile)) selected = entry.id();
                dragSource(entry);
                ImGui.setItemTooltip(entry.description());
                if (ImGui.selectable(entry.name() + "##" + entry.id(), entry.id().equals(selected), 0, tile, 0)) selected = entry.id();
                dragSource(entry);
                ImGui.endGroup();
            }
        }
        ImGui.endChild();
    }

    private static void dragSource(FilterCatalog.Entry entry) {
        // No floating preview tooltip: ImGui anchors it below the cursor, so
        // dragging a look to the bottom screen edge pushes the whole preview
        // window off-screen. Its clip rect then has zero height and Flashback's
        // GL backend throws (194x0 scissor) instead of skipping the draw.
        // We draw our own preview into the foreground draw list instead (see
        // drawDragPreview), clamped fully inside the main viewport, so it can
        // never leave the screen. The drop targets still highlight on hover.
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceNoPreviewTooltip)) {
            ImGui.setDragDropPayload("ACTORS_LOOK", entry.id());
            pdx.dev.fbactors.debug.DragDiag.log("drag start: filter=%s mouse=(%.1f,%.1f)",
                entry.id(), ImGui.getMousePosX(), ImGui.getMousePosY());
            dragPreviewArmed = true;
            drawDragPreview(entry);
            ImGui.endDragDropSource();
        }
    }

    /**
     * Mouse-following drag preview drawn by us, not ImGui: no preview window
     * is ever created, so there is no window clip rect that can collapse to
     * zero area. The box is clamped fully inside the main viewport every
     * frame, and skipped entirely when clamping is impossible (viewport
     * smaller than the box). All rects keep a size of at least 1px, so no
     * zero-area draw is possible even in theory.
     */
    private static void drawDragPreview(FilterCatalog.Entry entry) {
        float aspect = Looks.previewAspect();
        if (!(aspect > 0)) return;
        float imgW = DragPreview.IMG_W;
        float imgH = imgW / aspect;
        float textH = Math.max(1, ImGui.getTextLineHeight());
        float boxW = imgW + DragPreview.PAD * 2;
        float boxH = DragPreview.PAD + imgH + 4 + textH + DragPreview.PAD;
        if (!(boxW >= 1) || !(boxH >= 1) || !(imgW >= 1) || !(imgH >= 1)) return;
        var viewport = ImGui.getMainViewport();
        if (viewport == null) return;
        float vx = viewport.getPosX(), vy = viewport.getPosY();
        float vw = viewport.getSizeX(), vh = viewport.getSizeY();
        if (!(vw >= 1) || !(vh >= 1)) return;
        if (!DragPreview.fits(vw, vh, boxW, boxH)) return;
        float x = DragPreview.clampPos(ImGui.getMousePosX() + DragPreview.CURSOR_GAP, vx, vw, boxW);
        float y = DragPreview.clampPos(ImGui.getMousePosY() + DragPreview.CURSOR_GAP, vy, vh, boxH);
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;
        if (!entry.id().equals(dragPreviewLoggedId)) {
            dragPreviewLoggedId = entry.id();
            pdx.dev.fbactors.debug.DragDiag.log(
                "look drag preview: id=%s x=%.1f y=%.1f w=%.1f h=%.1f viewport=(%.1f,%.1f %.1fx%.1f)",
                entry.id(), x, y, boxW, boxH, vx, vy, vw, vh);
        }
        var draw = ImGui.getForegroundDrawList();
        if (draw == null) return;
        draw.addRectFilled(x, y, x + boxW, y + boxH, 0xE8141414, 6);
        draw.addRect(x, y, x + boxW, y + boxH, 0xFF8A8A8A, 6);
        float ix = x + DragPreview.PAD, iy = y + DragPreview.PAD;
        long texture = Looks.previewTexture();
        int index = FilterCatalog.entries().indexOf(entry);
        if (texture != 0 && index >= 0) {
            int columns = FilterGpuPipeline.PREVIEW_COLUMNS;
            int rows = Math.max(1, (FilterCatalog.entries().size() + columns - 1) / columns);
            float u0 = (index % columns) / (float) columns;
            float u1 = (index % columns + 1) / (float) columns;
            float v0 = (index / columns) / (float) rows;
            float v1 = (index / columns + 1) / (float) rows;
            float insetU = 0.5f / (columns * FilterGpuPipeline.PREVIEW_WIDTH);
            float insetV = 0.5f / (rows * (FilterGpuPipeline.PREVIEW_WIDTH / Looks.previewAspect()));
            draw.addImage(texture, ix, iy, ix + imgW, iy + imgH,
                u0 + insetU, v0 + insetV, u1 - insetU, v1 - insetV);
        } else {
            draw.addText(ix, iy, 0xffaaaaaa, "Preparing preview...");
        }
        draw.addText(ix, iy + imgH + 4, 0xFFFFFFFF, DragPreview.label(entry.name()));
    }

    /**
     * Viewport-clamped drag-preview math. Pure Java (no ImGui): initializing
     * this class never touches the outer class state or the ImGui binding,
     * so unit tests can exercise it headless.
     */
    static final class DragPreview {
        static final float IMG_W = 180;
        static final float PAD = 7;
        static final float CURSOR_GAP = 16;
        static final int MAX_LABEL = 24;

        private DragPreview() {}

        /**
         * Clamp one axis of the preview box fully inside the viewport:
         * the box's far edge never passes the viewport's far edge, and its
         * near edge never passes the viewport's near edge. Callers must check
         * {@link #fits} first: when the viewport is smaller than the box the
         * result sits below {@code viewMin} (no valid position exists).
         */
        static float clampPos(float desired, float viewMin, float viewSize, float boxSize) {
            return Math.min(Math.max(desired, viewMin), viewMin + viewSize - boxSize);
        }

        /** Whether the box can be clamped inside the viewport at all. NaN-safe. */
        static boolean fits(float viewW, float viewH, float boxW, float boxH) {
            return viewW >= boxW && viewH >= boxH;
        }

        /** Short, never-null label that stays inside the preview box. */
        static String label(String name) {
            if (name == null) return "";
            if (name.length() > MAX_LABEL) return name.substring(0, MAX_LABEL - 3) + "...";
            return name;
        }
    }

    private static boolean thumbnail(FilterCatalog.Entry entry, float width) {
        float height = Math.max(1, width / Looks.previewAspect());
        if (width <= 0 || height <= 0) {
            pdx.dev.fbactors.debug.DragDiag.warnRare("look-thumbnail",
                "skipped look thumbnail %s: w=%.1f h=%.1f", entry.id(), width, height);
            return false;
        }
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##Preview" + entry.id() + width, width, height);
        long texture = Looks.previewTexture();
        if (texture != 0) {
            int index = FilterCatalog.entries().indexOf(entry);
            int columns = FilterGpuPipeline.PREVIEW_COLUMNS;
            int rows = (FilterCatalog.entries().size() + columns - 1) / columns;
            float u0 = (index % columns) / (float) columns;
            float u1 = (index % columns + 1) / (float) columns;
            float v0 = (index / columns) / (float) rows;
            float v1 = (index / columns + 1) / (float) rows;
            float insetU = 0.5f / (columns * FilterGpuPipeline.PREVIEW_WIDTH);
            float insetV = 0.5f / (rows * (FilterGpuPipeline.PREVIEW_WIDTH / Looks.previewAspect()));
            ImGui.getWindowDrawList().addImage((long) texture, x, y, x + width, y + height,
                u0 + insetU, v0 + insetV, u1 - insetU, v1 - insetV);
        } else {
            ImGui.getWindowDrawList().addText(x + 8, y + 8, 0xffaaaaaa, "Preparing preview...");
        }
        return clicked;
    }

    private static float slider(String label, float value, float min, float max) {
        float[] edit = {value};
        ImGui.setNextItemWidth(-110);
        if (ImGui.sliderFloat(label, edit, min, max, "%.2f")) changed();
        return edit[0];
    }
}
