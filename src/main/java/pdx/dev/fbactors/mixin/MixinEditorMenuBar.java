package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.editor.ui.windows.MainMenuBar;
import imgui.moulberry90.ImGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.director.CameraPlayback;
import pdx.dev.fbactors.director.DirectorMenu;
import pdx.dev.fbactors.director.DirectorShotsWindow;
import pdx.dev.fbactors.theme.ThemeMenu;
import pdx.dev.fbactors.settings.FbSettingsMenu;
import pdx.dev.fbactors.subtitle.SubtitleMenu;
import pdx.dev.fbactors.actor.ActorSetupMenu;


@Mixin(value = MainMenuBar.class, remap = false)
public class MixinEditorMenuBar {


    private static boolean addedThisFrame;


    @Inject(method = "renderInner",
        at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;separator()V",
            ordinal = 1, shift = At.Shift.AFTER),
        require = 0)
    private static void fbactors$menuItems(CallbackInfo ci) {
        addedThisFrame = true;
        DirectorMenu.renderMenuItem();
        DirectorMenu.renderShotsMenuItem();
        ThemeMenu.renderMenuItem();
        SubtitleMenu.renderMenuItem();
        FbSettingsMenu.renderMenuItem();
        pdx.dev.fbactors.filters.LooksMenu.renderMenuItem();
        if (ImGui.menuItem("Edit look clip...")) pdx.dev.fbactors.filters.EffectsMenu.open();
    }


    @Inject(method = "renderInner", at = @At("TAIL"), require = 0)
    private static void fbactors$menuItemsFallback(CallbackInfo ci) {
        if (!addedThisFrame) {
            ImGui.separator();
            DirectorMenu.renderMenuItem();
            DirectorMenu.renderShotsMenuItem();
            ThemeMenu.renderMenuItem();
            SubtitleMenu.renderMenuItem();
            FbSettingsMenu.renderMenuItem();
            pdx.dev.fbactors.filters.LooksMenu.renderMenuItem();
            if (ImGui.menuItem("Edit look clip...")) pdx.dev.fbactors.filters.EffectsMenu.open();
        }
        addedThisFrame = false;
    }


    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private static void fbactors$frames(CallbackInfo ci) {
        try { DirectorMenu.flushPending(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Director flush failed; skipping frame", e);
        }
        try { CameraPlayback.tick(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Camera playback tick failed; skipping frame", e);
        }
        try { pdx.dev.fbactors.settings.UiTheme.applyCurrent(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("UI theme apply failed; skipping frame", e);
        }
        try { pdx.dev.fbactors.station.StationWindow.render(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Station window failed; skipping frame", e);
        }
        try { DirectorMenu.render(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Director menu failed; skipping frame", e);
        }
        try { DirectorShotsWindow.render(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Director shots window failed; skipping frame", e);
        }
        try { ThemeMenu.render(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Theme menu failed; skipping frame", e);
        }
        try { FbSettingsMenu.render(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Settings menu failed; skipping frame", e);
        }
        try { pdx.dev.fbactors.filters.LooksMenu.renderWindow(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Looks menu failed; skipping frame", e);
        }
        try { pdx.dev.fbactors.filters.EffectsMenu.renderWindow(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Effects menu failed; skipping frame", e);
        }
        try { pdx.dev.fbactors.settings.ThemeMakerMenu.render(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Theme maker failed; skipping frame", e);
        }
        try { SubtitleMenu.render(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Subtitle menu failed; skipping frame", e);
        }
        try { ActorSetupMenu.render(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Actor setup menu failed; skipping frame", e);
        }
    }
}

