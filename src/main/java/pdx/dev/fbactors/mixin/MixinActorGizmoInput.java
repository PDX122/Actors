package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.ui.ActorMoveGizmo;

@Mixin(value = ReplayUI.class, remap = false)
public class MixinActorGizmoInput {
    @Shadow private static HitResult getLookTarget() { throw new AssertionError(); }

    @Inject(method = "handleBasicInputs", at = @At("HEAD"), cancellable = true)
    private static void fbactors$selectOrDragActor(CallbackInfo ci) {
        if (pdx.dev.fbactors.events.EventPlacement.viewportInput()) { ci.cancel(); return; }
        if (!ActorMoveGizmo.canEdit()) return;
        if (ActorMoveGizmo.consumesInput()) { ci.cancel(); return; }
        if (!ImGui.isMouseClicked(0)) return;
        HitResult target = getLookTarget();
        if (target instanceof EntityHitResult hit) {
            var actor = FlashbackActors.ACTORS.byUuid(hit.getEntity().getUUID());
            if (actor != null && !actor.take.locked) {
                ActorMoveGizmo.select(actor);
                ci.cancel();
                return;
            }
        }
        ActorMoveGizmo.clearSelection();
    }
}
