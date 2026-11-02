package pdx.dev.fbactors.settings;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;

public final class ThemeMakerMenu {

    private static final float PANEL_WIDTH = 520.0f;
    private static final float PANEL_HEIGHT = 450.0f;

    private static boolean open;

    private static final ImBoolean OPEN = new ImBoolean();

    private static final ImString CODE = new ImString(1024);

    private static boolean codeSeeded;

    private static String status = "";

    private ThemeMakerMenu() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static void open() {
        open = true;
        OPEN.set(true);
        refreshCodeFromSettings();
    }

    public static void close() {
        open = false;
        OPEN.set(false);
        FbSettings.settings().save();
    }

    public static void renderMenuItem() {
        if(ImGui.menuItem("UI themes...##ThemeMakerOpen",open)) open();
        ImGui.setItemTooltip("Customize editor colors and rounding, or import and share a UI theme");
    }

    private static void refreshCodeFromSettings() {
        CODE.set(UiTheme.customConfigText());
        codeSeeded = true;
        status = "";
    }

    public static void render() {
        if (!open) {
            return;
        }
        if (!codeSeeded) {
            refreshCodeFromSettings();
        }
        var viewport=ImGui.getMainViewport();
        float width=Math.max(1,viewport.getWorkSizeX()-24),height=Math.max(1,viewport.getWorkSizeY()-24);
        ImGui.setNextWindowSize(Math.min(PANEL_WIDTH,width),Math.min(PANEL_HEIGHT,height),imgui.moulberry90.flag.ImGuiCond.Appearing);
        ImGui.setNextWindowSizeConstraints(Math.min(340,width),Math.min(320,height),width,height);
        OPEN.set(open);
        boolean visible=ImGui.begin("UI themes###Theme Maker",OPEN);
        try {
            if(visible) {
                ImGui.pushTextWrapPos(0);
                try {
                    ImGui.textWrapped("Customize the editor interface. Presets apply immediately.");
                    renderDropdown();
                    ImGui.spacing(); ImGui.separator(); ImGui.spacing();
                    renderCodeBox();
                    ImGui.spacing(); ImGui.separator(); ImGui.spacing();
                    renderFooter();
                } finally { ImGui.popTextWrapPos(); }
            }
        } finally { ImGui.end(); }
        if(!OPEN.get()) close();
    }

    private static void renderDropdown() {
        FbSettings settings = FbSettings.settings();
        UiTheme current = UiTheme.byId(settings.uiTheme);
        ImGui.text("UI preset");
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##ThemeMakerDrop", current.label())) {
            for (UiTheme candidate : UiTheme.values()) {
                boolean selected = candidate == current;
                String row = candidate.label();
                if (ImGui.menuItem(row, selected)) {
                    settings.uiTheme = candidate.id();
                    UiTheme.applyNow(candidate);
                    settings.save();
                    if (candidate == UiTheme.CUSTOM) {
                        refreshCodeFromSettings();
                    }
                }
                ImGui.setItemTooltip(candidate.blurb());
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Pick a built in theme. Applies at once.");
        }
        ImGui.textDisabled("Current: " + current.label());
    }

    private static void renderCodeBox() {
        ImGui.text("Theme code");
        ImGui.textDisabled("Paste a shared line, then Apply. Copy shares yours.");
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextMultiline("##ThemeMakerCode", CODE, Math.max(1,ImGui.getContentRegionAvailX()), 80.0f);
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Format: accent=4D8DE6;bg=171A26;rounding=6.0;light=0");
        }
        if (ImGui.button("Apply code##ThemeMakerApply")) {
            String error = UiTheme.applyConfigText(CODE.get());
            if (error == null) {
                refreshCodeFromSettings();
                status = "Applied. Custom theme is live.";
            } else {
                status = error;
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Copy current##ThemeMakerCopy")) {
            refreshCodeFromSettings();
            try {
                ImGui.setClipboardText(CODE.get());
                status = "Copied to clipboard.";
            } catch (Exception e) {
                status = "Code refreshed. Copy it by hand.";
            }
        }
        if (!status.isEmpty()) {
            ImGui.textDisabled(status);
        }
    }

    private static void renderFooter() {
        if (ImGui.button("Restore Flashback default##ThemeMakerDefault", Math.max(1,ImGui.getContentRegionAvailX()), 0.0f)) {
            FbSettings settings = FbSettings.settings();
            settings.uiTheme = UiTheme.FLASHBACK.id();
            UiTheme.applyNow(UiTheme.FLASHBACK);
            settings.save();
            status = "";
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Restore the stock editor look");
        }
        ImGui.textDisabled("Choice saves automatically.");
        if (ImGui.button("Done##ThemeMakerClose", Math.max(1,ImGui.getContentRegionAvailX()), 0.0f)) {
            close();
        }
    }
}
