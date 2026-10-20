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
        }
        addedThisFrame = false;
    }


    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private static void fbactors$frames(CallbackInfo ci) {
        DirectorMenu.flushPending();
        CameraPlayback.tick();
        pdx.dev.fbactors.settings.UiTheme.applyCurrent();
        pdx.dev.fbactors.station.StationWindow.render();
        DirectorMenu.render();
        DirectorShotsWindow.render();
        ThemeMenu.render();
        FbSettingsMenu.render();
        pdx.dev.fbactors.settings.ThemeMakerMenu.render();
        SubtitleMenu.render();
        ActorSetupMenu.render();
    }
}

