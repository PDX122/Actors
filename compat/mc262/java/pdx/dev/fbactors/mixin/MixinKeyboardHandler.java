package pdx.dev.fbactors.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.input.Hotkey;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void fbactors$keyPress(long window, int action, KeyEvent event,
                                   CallbackInfo ci) {
        if (action == 1) {
            if (Hotkey.endRunOnEscape(event.key())) { ci.cancel(); return; }
            Hotkey.press(event.key(), event.modifiers());
        }
    }
}
