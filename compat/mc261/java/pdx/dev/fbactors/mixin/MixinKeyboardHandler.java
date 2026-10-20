package pdx.dev.fbactors.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
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
 * <h2>Why this copy declares the {@code KeyEvent} shape</h2>
 *
 * <p>On this target {@code KeyboardHandler.keyPress} is
 * {@code keyPress(long, int, KeyEvent)}: the window handle, the press action,
 * and a record carrying key, scancode and modifiers. Verified against the game
 * jar - the adapter that builds the event is the old five-argument GLFW
 * callback, so the {@code int} is still the action and only {@code 1} is a
 * press, exactly as on the older targets. A handler declaring the old five-int
 * shape fails validation with {@code InvalidInjectionException} and takes the
 * whole client down at startup (seen on 1.21.11) - {@code require = 0} does not
 * save it, because the method is found by name and <em>then</em> the descriptor
 * mismatches. So the hook is one copy per compat dir, in the same package the
 * single {@code fbactors.mixins.json} resolves, with exactly the shape its own
 * game build has. {@code tools/verify_mixins.py} re-checks every copy against
 * the real game jars, and {@code tools/smoke_all.ps1} boots every client.
 *
 * <p>Duplicated per version rather than shared because javac rejects two
 * source roots declaring the same class, and because {@code KeyEvent} does not
 * exist at all before 1.21.10.
 */
@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    /**
     * The key hook for this target: {@code keyPress(long, int, KeyEvent)}.
     *
     * <p>Key and modifiers come out of the event record; the action filter is
     * the same edge detection the older shape uses.
     */
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"))
    private void fbactors$keyPress(long window, int action, KeyEvent event,
                                   CallbackInfo ci) {
        if (action == 1) {
            Hotkey.press(event.key(), event.modifiers());
        }
    }
}
