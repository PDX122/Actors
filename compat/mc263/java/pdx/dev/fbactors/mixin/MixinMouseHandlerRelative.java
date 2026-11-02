package pdx.dev.fbactors.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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
