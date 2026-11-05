package pdx.dev.fbactors.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.morph.MorphRenderInfo;
import pdx.dev.fbactors.morph.MorphRenderState;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void fbactors$hideMorphedPlayer(Entity entity, Frustum frustum, double x,
            double y, double z, float tickDelta, CallbackInfoReturnable<Boolean> cir) {
        if (pdx.dev.fbactors.camera.BoatCam.hides(entity.getUUID())) {
            cir.setReturnValue(false);
            return;
        }
        MorphRenderInfo info = MorphRenderState.get(entity.getUUID());
        MorphRenderState.noteRenderLookup(entity.getUUID(), info != null && !info.isPlayer());
        if (info == null || info.isPlayer()) {
            return;
        }
        Compat.noteActor(entity);
        MorphRenderState.noteVisibility(entity.getUUID(), false);
        cir.setReturnValue(false);
    }

    @Inject(method = "shouldRender", at = @At("RETURN"))
    private void fbactors$noteVisibility(Entity entity, Frustum frustum, double x,
            double y, double z, float tickDelta, CallbackInfoReturnable<Boolean> cir) {
        MorphRenderState.noteVisibility(entity.getUUID(), cir.getReturnValue());
    }
}
