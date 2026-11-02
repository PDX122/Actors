package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.editor.ui.windows.VisualsWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.settings.FbSettings;

@Mixin(value = VisualsWindow.class, remap = false)
public class MixinVisualsWindow {

    @Inject(method = "render()V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void fbactors$suppressVisuals(CallbackInfo ci) {
        if (!FbSettings.stationSidebar()) return;
        pdx.dev.fbactors.debug.ErrorRecovery.run("Actors Studio",pdx.dev.fbactors.station.StationWindow::render);
        ci.cancel();
    }
}
