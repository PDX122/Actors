package pdx.dev.fbactors.ui;

import pdx.dev.fbactors.debug.ErrorRecovery;

public final class EditorWindows {
    private EditorWindows() {}
    public static void render() {
        ErrorRecovery.run("About window",AboutWindow::render);
        ErrorRecovery.run("Changelog",ChangelogWindow::render);
        ErrorRecovery.run("Camera modes",pdx.dev.fbactors.camera.BoatCam::render);
        ErrorRecovery.run("Settings",pdx.dev.fbactors.settings.FbSettingsMenu::render);
        ErrorRecovery.run("Looks gallery",pdx.dev.fbactors.filters.LooksMenu::renderWindow);
        ErrorRecovery.run("Effects editor",pdx.dev.fbactors.filters.EffectsMenu::renderWindow);
        ErrorRecovery.run("UI themes",pdx.dev.fbactors.settings.ThemeMakerMenu::render);
        ErrorRecovery.run("Subtitles editor",pdx.dev.fbactors.subtitle.SubtitleMenu::render);
        ErrorRecovery.run("Actor setup",pdx.dev.fbactors.actor.ActorSetupMenu::render);
    }
}
