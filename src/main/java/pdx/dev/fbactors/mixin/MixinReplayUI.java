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


    @Inject(method = "drawOverlayInternal",
        at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/windows/TimelineWindow;render()V",
            shift = At.Shift.AFTER))
    private static void fbactors$renderSkinPicker(CallbackInfo ci) {
        try { SkinPicker.render(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Skin picker overlay failed; skipping frame", e);
        }
        try { MorphPicker.render(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Morph picker overlay failed; skipping frame", e);
        }
        try { Hotkey.renderHelp(); } catch (Exception | LinkageError e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Hotkey help overlay failed; skipping frame", e);
        }
    }
}

