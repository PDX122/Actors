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
    private static java.util.UUID target;
    private static String dragPreviewLoggedId;
    private static boolean dragPreviewArmed;
    private static String auditionId;
    private static LookSettings audition;
    private static boolean livePreview=true;
    private LooksMenu() {}

    public static void open() { OPEN.set(true); }

    public static void renderMenuItem() {
        if (ImGui.menuItem("Looks...", OPEN.get())) open();
    }

    public static void renderWindow() {
        if(!OPEN.get()) { Looks.stopPreview(); return; }
        var viewport=ImGui.getMainViewport();
        ImGui.setNextWindowSize(Math.min(720,viewport.getWorkSizeX()-32),Math.min(540,viewport.getWorkSizeY()-32),ImGuiCond.Always);
        ImGui.setNextWindowBgAlpha(1);pdx.dev.fbactors.ui.SkinPickerChrome.push();
        boolean visible=ImGui.begin("Looks gallery###ActorLooks",OPEN,imgui.moulberry90.flag.ImGuiWindowFlags.NoResize);
        try {if(visible) renderPicker();else Looks.stopPreview();}finally{ImGui.end();pdx.dev.fbactors.ui.SkinPickerChrome.pop();}
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
            if (ImGui.selectable((i + 1) + ". " + (FilterCatalog.find(id)==null ? "Missing shader" : FilterCatalog.find(id).name()), id.equals(selected))) selected = id;
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

        dragPreviewArmed = false;
        browser();
        ShaderLibraryMenu.render();
        String imported=ShaderLibraryMenu.takeImported();
        if(imported!=null){selected=imported;category="Custom";SEARCH.set("");}
        if (!dragPreviewArmed) {

            dragPreviewLoggedId = null;
        }
    }

    private static void browser() {
        float width=ImGui.getContentRegionAvailX();
        ImGui.setNextItemWidth(Math.max(1,width-136));ImGui.inputTextWithHint("##FilterSearch","Search looks and shaders",SEARCH);
        ImGui.sameLine();if(pdx.dev.fbactors.ui.SkinPickerChrome.button("Import shader",false,128))ShaderLibraryMenu.openImport();
        ImGui.setNextItemWidth(Math.min(180,width));
        if(ImGui.beginCombo("##FilterCategory",category)) {
            try {
                var categories=new ArrayList<String>();categories.add("All");categories.add("Custom");
                FilterCatalog.entries().stream().map(FilterCatalog.Entry::category).distinct().filter(name->!categories.contains(name)).forEach(categories::add);
                for(String name:categories)if(ImGui.selectable(name,category.equals(name)))category=name;
            }finally{ImGui.endCombo();}
        }
        ImGui.sameLine();ImGui.textDisabled(FilterCatalog.search(SEARCH.get(),category).size()+" looks");
        float height=Math.max(1,ImGui.getContentRegionAvailY()-ImGui.getTextLineHeightWithSpacing()*2);
        boolean beside=width>=570;float browserWidth=beside?Math.max(290,width*.58f):width;
        boolean library=ImGui.beginChild("##FilterBrowser",browserWidth,beside?height:Math.max(150,height*.52f),false);
        try {
            if(library) {
                var matches=FilterCatalog.search(SEARCH.get(),category);
                if(matches.isEmpty())ImGui.textWrapped("No matching looks. Import a shader or change the filters.");
                float available=ImGui.getContentRegionAvailX();
                int columns=Math.max(1,(int)((available+8)/150));float tile=Math.max(1,(available-(columns-1)*8)/columns);
                int index=0;
                for(var entry:matches) {
                    if(index++%columns!=0)ImGui.sameLine();
                    ImGui.beginGroup();
                    try {
                        if(thumbnail(entry,tile))selected=entry.id();dragSource(entry);
                        if(pdx.dev.fbactors.ui.SkinPickerChrome.button(entry.name()+"##Look"+entry.id(),entry.id().equals(selected),tile))selected=entry.id();
                        dragSource(entry);ImGui.setItemTooltip(entry.description());
                    }finally{ImGui.endGroup();}
                }
            }
        }finally{ImGui.endChild();}
        if(beside)ImGui.sameLine();
        boolean details=ImGui.beginChild("##LookInspector",beside?0:width,beside?height:0,false);
        try {if(details)inspector();}finally{ImGui.endChild();}
        if(!ManagedShaders.status.isEmpty())ImGui.textWrapped(ManagedShaders.status);
    }

    private static void inspector() {
        var entry=FilterCatalog.find(selected);
        if(entry==null){ImGui.textWrapped("Choose a look to inspect it.");return;}
        ImGui.textUnformatted(entry.name());
        ImGui.textDisabled(ManagedShaders.custom(entry.id())?"YOUR SHADER":entry.category().toUpperCase(java.util.Locale.ROOT));
        ImGui.spacing();detailPreview(entry,Math.max(1,ImGui.getContentRegionAvailX()));
        ImGui.spacing();ImGui.textWrapped(entry.description());ImGui.spacing();
        if(!entry.id().equals(auditionId)) { auditionId=entry.id(); audition=new LookSettings(); audition.add(entry.id()); }
        if(ImGui.checkbox("Preview live on whole scene",livePreview)) { livePreview=!livePreview; if(!livePreview) Looks.stopPreview(); }
        if(CameraTelemetry.supports(entry.id())) {
            for(var param:FilterCatalog.params(entry.id())) {
                float[] value={audition.parameter(entry.id(),param)};
                ImGui.setNextItemWidth(-1);
                if(ImGui.sliderFloat(param.label(),value,param.min(),param.max(),param.max()>100?"%.0f":"%.2f")) audition.parameter(entry.id(),param,value[0]);
            }
            ImGui.textWrapped("Speed, altitude, heading and climb use the subject's replay motion. Battery and link are editable display values. Clock uses replay time.");
        }
        if(livePreview) Looks.preview(audition);
        var replay=com.moulberry.flashback.Flashback.getReplayServer();
        ImGui.beginDisabled(replay==null);
        try {
            ImGui.setNextItemWidth(-1);
            var actor=target==null?null:pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(target);
            if(target!=null && actor==null)target=null;
            if(ImGui.beginCombo("##LookTarget",actor==null?"Whole scene":actor.displayName())) {
                try {
                    if(ImGui.selectable("Whole scene",target==null))target=null;
                    for(var item:pdx.dev.fbactors.FlashbackActors.ACTORS.actors().toArray(pdx.dev.fbactors.actor.Actor[]::new))
                        if(item!=null && ImGui.selectable(item.displayName()+"##LookTarget"+item.take.id,item.take.id.equals(target)))target=item.take.id;
                }finally{ImGui.endCombo();}
            }
            boolean locked=actor==null?Effects.timeline().locked:actor.take.locked;
            ImGui.beginDisabled(locked);
            try {if(pdx.dev.fbactors.ui.SkinPickerChrome.button("Add effect with these settings",true,ImGui.getContentRegionAvailX())) {
                Effects.add(entry.id(),Math.max(0,pdx.dev.fbactors.FlashbackActors.replayTick()),target,audition);
            }}
            finally{ImGui.endDisabled();}
            if(locked)ImGui.textWrapped("Unlock the target track to add effects.");
        }finally{ImGui.endDisabled();}
        ImGui.textDisabled("Or drag onto an actor / Misc track.");
        ImGui.spacing();
        if(pdx.dev.fbactors.ui.SkinPickerChrome.button("Delete shader",false,ImGui.getContentRegionAvailX()))ShaderLibraryMenu.confirmDelete(entry.id());
    }

    private static void detailPreview(FilterCatalog.Entry entry,float width) {
        if(!ManagedShaders.custom(entry.id())) {thumbnail(entry,width);return;}
        try {
            long texture=CustomShaderPreview.texture(entry.id());
            if(texture!=0) {ImGui.image(texture,width,width/Looks.previewAspect());return;}
        }catch(RuntimeException failure) {ManagedShaders.status="Custom preview failed: "+String.valueOf(failure.getMessage());}
        thumbnail(entry,width);
    }

    private static void dragSource(FilterCatalog.Entry entry) {

        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceNoPreviewTooltip)) {
            ImGui.setDragDropPayload("ACTORS_LOOK", entry.id());
            pdx.dev.fbactors.debug.DragDiag.log("drag start: filter=%s mouse=(%.1f,%.1f)",
                entry.id(), ImGui.getMousePosX(), ImGui.getMousePosY());
            dragPreviewArmed = true;
            drawDragPreview(entry);
            ImGui.endDragDropSource();
        }
    }

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
        int index = FilterCatalog.builtins().indexOf(entry);
        if (texture != 0 && index >= 0) {
            int columns = FilterGpuPipeline.PREVIEW_COLUMNS;
            int rows = Math.max(1, (FilterCatalog.builtins().size() + columns - 1) / columns);
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

    static final class DragPreview {
        static final float IMG_W = 180;
        static final float PAD = 7;
        static final float CURSOR_GAP = 16;
        static final int MAX_LABEL = 24;

        private DragPreview() {}

        static float clampPos(float desired, float viewMin, float viewSize, float boxSize) {
            return Math.min(Math.max(desired, viewMin), viewMin + viewSize - boxSize);
        }

        static boolean fits(float viewW, float viewH, float boxW, float boxH) {
            return viewW >= boxW && viewH >= boxH;
        }

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
        int index = FilterCatalog.builtins().indexOf(entry);
        if (texture != 0 && index >= 0) {
            int columns = FilterGpuPipeline.PREVIEW_COLUMNS;
            int rows = (FilterCatalog.builtins().size() + columns - 1) / columns;
            float u0 = (index % columns) / (float) columns;
            float u1 = (index % columns + 1) / (float) columns;
            float v0 = (index / columns) / (float) rows;
            float v1 = (index / columns + 1) / (float) rows;
            float insetU = 0.5f / (columns * FilterGpuPipeline.PREVIEW_WIDTH);
            float insetV = 0.5f / (rows * (FilterGpuPipeline.PREVIEW_WIDTH / Looks.previewAspect()));
            ImGui.getWindowDrawList().addImage((long) texture, x, y, x + width, y + height,
                u0 + insetU, v0 + insetV, u1 - insetU, v1 - insetV);
        } else {
            var draw=ImGui.getWindowDrawList();
            draw.addRectFilled(x,y,x+width,y+height,0xFF3B3027,10);
            draw.addText(x+12,y+12,0xFFC6C0BA,ManagedShaders.custom(entry.id())?"Custom GLSL":"Loading preview...");
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
