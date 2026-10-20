package pdx.dev.fbactors.settings;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;


public final class ThemeMakerMenu {

    private static final float PANEL_WIDTH = 420.0f;
    private static final float PANEL_HEIGHT = 340.0f;

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
        ImGui.setNextWindowSize(PANEL_WIDTH, PANEL_HEIGHT);
        OPEN.set(open);
        if (!ImGui.begin("Theme Maker", OPEN)) {
            ImGui.end();
            return;
        }
        open = OPEN.get();

        renderDropdown();
        ImGui.separator();
        renderCodeBox();
        ImGui.separator();
        renderFooter();

        ImGui.end();
        open = OPEN.get();
        if (!open) {
            FbSettings.settings().save();
        }
    }

    private static void renderDropdown() {
        FbSettings settings = FbSettings.settings();
        UiTheme current = UiTheme.byId(settings.uiTheme);
        ImGui.text("Theme");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.beginCombo("##ThemeMakerDrop", current.label())) {
            for (UiTheme candidate : UiTheme.values()) {
                boolean selected = candidate == current;
                String row = candidate.label() + "  |  " + candidate.blurb();
                if (ImGui.menuItem(row, selected)) {
                    settings.uiTheme = candidate.id();
                    UiTheme.applyNow(candidate);
                    settings.save();
                    if (candidate == UiTheme.CUSTOM) {
                        refreshCodeFromSettings();
                    }
                }
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
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        ImGui.inputTextMultiline("##ThemeMakerCode", CODE, PANEL_WIDTH - 40.0f, 64.0f);
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Format: accent=4D8DE6;bg=171A26;rounding=6.0;light=0");
        }
        if (ImGui.button("Apply code##ThemeMakerApply")) {
            String error = UiTheme.applyConfigText(CODE.get());
            if (error == null) {
                status = "Applied. Custom theme is live.";
                refreshCodeFromSettings();
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
        if (ImGui.button("Back to Flashback default##ThemeMakerDefault", PANEL_WIDTH - 40.0f, 0.0f)) {
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
        if (ImGui.button("Close##ThemeMakerClose", PANEL_WIDTH - 40.0f, 0.0f)) {
            close();
        }
    }
}

