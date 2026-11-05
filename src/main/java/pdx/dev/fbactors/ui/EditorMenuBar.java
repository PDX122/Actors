package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImGui;
import pdx.dev.fbactors.filters.EffectsMenu;
import pdx.dev.fbactors.filters.LooksMenu;
import pdx.dev.fbactors.settings.FbSettingsMenu;
import pdx.dev.fbactors.subtitle.SubtitleMenu;
import pdx.dev.fbactors.settings.ThemeMakerMenu;

public final class EditorMenuBar {
    private EditorMenuBar() {}

    public static void renderActorsMenu() {
        if (ImGui.beginMenu("Actors##FbActorsMenu")) {
            try {
                var settings = pdx.dev.fbactors.settings.FbSettings.settings();
                if (ImGui.menuItem("Actors Studio", settings.stationSidebar)) {
                    settings.stationSidebar = !settings.stationSidebar;
                    settings.save();
                }
                if (ImGui.menuItem("Actor setup...")) pdx.dev.fbactors.actor.ActorSetupMenu.open();
                if (ImGui.menuItem("Camera modes...")) pdx.dev.fbactors.camera.CameraModes.open();
                if (ImGui.menuItem("Morph library...")) MorphLibrary.open();
                SubtitleMenu.renderMenuItem();
                ImGui.separator();
                ThemeMakerMenu.renderMenuItem();
                FbSettingsMenu.renderMenuItem();
                ImGui.separator();
                if (ImGui.menuItem("Error report...")) ErrorWindow.open();
                if (ImGui.menuItem("Changelog...")) ChangelogWindow.open();
                if (ImGui.menuItem("About Flashback Actors...")) AboutWindow.open();
            } finally { ImGui.endMenu(); }
        }
        if (ImGui.beginMenu("Visual##FbActorsVisual")) {
            try {
                if (ImGui.menuItem("Events library...")) pdx.dev.fbactors.events.EventsMenu.open();
                LooksMenu.renderMenuItem();
                if (ImGui.menuItem("Edit look clip...")) EffectsMenu.open();
            } finally { ImGui.endMenu(); }
        }
    }
}
