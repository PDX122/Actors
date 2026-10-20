package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.editor.ui.ReplayUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.input.Hotkey;
import pdx.dev.fbactors.ui.ActorGizmo;
import pdx.dev.fbactors.ui.MorphPicker;
import pdx.dev.fbactors.ui.SkinPicker;


@Mixin(value = ReplayUI.class, remap = false)
public class MixinReplayUI {


    @Inject(method = "drawOverlayInternal",
        at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;render()V",
            shift = At.Shift.AFTER))
    private static void fbactors$renderSkinPicker(CallbackInfo ci) {
        SkinPicker.render();
        MorphPicker.render();
        ActorGizmo.render();


        Hotkey.renderHelp();
    }


    @Inject(method = "handleBasicInputs", at = @At("HEAD"), cancellable = true)
    private static void fbactors$suppressCameraGrabForGizmo(CallbackInfo ci) {
        if (ActorGizmo.wantsMouse()) {
            ci.cancel();
        }
    }
}

