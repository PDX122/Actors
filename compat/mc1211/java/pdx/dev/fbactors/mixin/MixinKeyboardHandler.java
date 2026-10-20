package pdx.dev.fbactors.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.input.Hotkey;

/**
 * The hotkey hook: turns raw key events into {@link Hotkey} presses.
 *
 * <h2>Why a mixin and not a {@code KeyMapping}</h2>
 *
 * <p>Fabric's key-binding API registers a {@code KeyMapping} per key and answers
 * one question - "is this key down?" - with no way to ask which modifiers were
 * held. Every shortcut here is a chord ({@code Ctrl+R}, {@code Ctrl+Shift+M}),
 * and a {@code KeyMapping} cannot distinguish {@code Ctrl+R} from {@code R}. The
 * keyboard handler is the one place the modifiers bitmask exists, so that is
 * where the hook goes.
 *
 * <h2>Why this copy declares the GLFW shape</h2>
 *
 * <p>On this target {@code KeyboardHandler.keyPress} is still the GLFW callback
 * shape {@code keyPress(long, int, int, int, int)}. From 1.21.10 it is
 * {@code keyPress(long, int, KeyEvent)}, and a handler declaring the wrong
 * parameter list fails validation with {@code InvalidInjectionException} and
 * takes the whole client down at startup - {@code require = 0} does not save
 * it, because the method is found by name and <em>then</em> the descriptor
 * mismatches. So the hook is one copy per compat dir, in the same package the
 * single {@code fbactors.mixins.json} resolves, with exactly the shape its own
 * game build has. {@code tools/verify_mixins.py} re-checks every copy against
 * the real game jars, and {@code tools/smoke_all.ps1} boots every client.
 *
 * <p>Duplicated per version rather than shared because javac rejects two
 * source roots declaring the same class.
 */
@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    /**
     * The key hook for this target: {@code keyPress(long, int, int, int, int)}.
     *
     * <p>The action argument is the GLFW action, and only {@code GLFW_PRESS} (1)
     * is a press - release (0) and repeat (2) are ignored, which is the edge
     * detection the hotkeys need.
     */
    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"))
    private void fbactors$keyPress(long window, int key, int scancode, int action, int mods,
                                   CallbackInfo ci) {
        if (action == 1) {
            Hotkey.press(key, mods);
        }
    }
}
