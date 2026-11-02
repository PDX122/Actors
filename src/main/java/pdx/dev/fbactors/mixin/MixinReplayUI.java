package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.editor.ui.ReplayUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.input.Hotkey;
import pdx.dev.fbactors.ui.MorphPicker;
import pdx.dev.fbactors.ui.SkinPicker;

@Mixin(value = ReplayUI.class, remap = false)
public class MixinReplayUI {
    @Inject(method = "drawOverlayInternal", at = @At("RETURN"))
    private static void fbactors$releaseEvidence(CallbackInfo ci) {
        pdx.dev.fbactors.debug.ErrorRecovery.run("Deferred morph previews",
            () -> pdx.dev.fbactors.morph.MorphPreviews.renderQueue().finishFrame());
        pdx.dev.fbactors.selftest.ReleaseVisualChecks.captureFrame();
    }

    @Inject(method = "drawOverlayInternal",
        at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;render()V",
            shift = At.Shift.AFTER))
    private static void fbactors$renderSkinPicker(CallbackInfo ci) {
        pdx.dev.fbactors.debug.ErrorRecovery.run("Morph previews",() -> pdx.dev.fbactors.morph.MorphPreviews.beginFrame(
            (MorphPicker.previewsVisible() || pdx.dev.fbactors.ui.MorphLibrary.isOpen()) && pdx.dev.fbactors.settings.FbSettings.morphEnabled()));
        pdx.dev.fbactors.selftest.ReleaseVisualChecks.layoutFrame();
        pdx.dev.fbactors.ui.EditorWindows.render();
        pdx.dev.fbactors.debug.ErrorRecovery.run("Actor move handles",pdx.dev.fbactors.ui.ActorMoveGizmo::render);
        pdx.dev.fbactors.debug.ErrorRecovery.run("Skin picker",SkinPicker::render);
        pdx.dev.fbactors.debug.ErrorRecovery.run("Morph picker",MorphPicker::render);
        pdx.dev.fbactors.debug.ErrorRecovery.run("Morph library",pdx.dev.fbactors.ui.MorphLibrary::render);
        pdx.dev.fbactors.debug.ErrorRecovery.run("Shortcut help",Hotkey::renderHelp);

        pdx.dev.fbactors.ui.ErrorWindow.render();
    }
}
