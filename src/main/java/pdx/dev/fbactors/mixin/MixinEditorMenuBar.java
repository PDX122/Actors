package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.editor.ui.windows.MainMenuBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.ui.EditorMenuBar;

@Mixin(value = MainMenuBar.class, remap = false)
public class MixinEditorMenuBar {

    @Inject(method = "renderInner", at = @At(value = "CONSTANT", args = "stringValue=flashback.hide_replay_ui"))
    private static void fbactors$actorsMenu(CallbackInfo ci) {
        pdx.dev.fbactors.debug.ErrorRecovery.run("Actors menus",EditorMenuBar::renderActorsMenu);
        imgui.moulberry90.ImGui.separator();
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private static void fbactors$frames(CallbackInfo ci) {
        pdx.dev.fbactors.debug.ErrorRecovery.run("UI theme application",pdx.dev.fbactors.settings.UiTheme::applyCurrent);
    }
}
