package pdx.dev.fbactors.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps editor camera drags on SDL relative deltas (26.3 and later).
 *
 * <p>26.3 replaced GLFW with SDL3 and reworked
 * {@code MouseHandler.onMove} to accumulate the SDL <em>relative</em> deltas
 * only while vanilla {@code mouseGrabbed} is true, falling back to absolute
 * cursor deltas otherwise. Absolute deltas die at the screen edge, so a long
 * drag stops turning partway through (about half a turn) until the button is
 * released and pressed again.
 *
 * <p>Flashback drives editor drags through its own SDL grab
 * ({@code CustomImGuiWindowerSdl.setGrabbed}, relative mode on) and cancels
 * vanilla's grab/release calls, so vanilla's flag is never set in the editor
 * and every drag silently runs on the edge-locked absolute path. While
 * Flashback holds the editor grab, report grabbed so the relative deltas are
 * used. Outside a grab the vanilla value passes through untouched, and on
 * older targets this mixin does not exist at all.
 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerRelative {

    @ModifyExpressionValue(method = "onMove",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MouseHandler;mouseGrabbed:Z"))
    private boolean fbactors$editorDragUsesRelative(boolean grabbed) {
        if (grabbed) {
            return true;
        }
        try {
            if (ReplayUI.isActive()
                && ReplayUI.imguiWindower != null
                && ReplayUI.imguiWindower.isGrabbed()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
