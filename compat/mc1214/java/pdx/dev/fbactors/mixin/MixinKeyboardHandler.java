package pdx.dev.fbactors.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.input.Hotkey;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void fbactors$keyPress(long window, int key, int scancode, int action, int mods,
                                   CallbackInfo ci) {
        if (action == 1) {
            if (Hotkey.endRunOnEscape(key)) { ci.cancel(); return; }
            Hotkey.press(key, mods);
        }
    }
}
