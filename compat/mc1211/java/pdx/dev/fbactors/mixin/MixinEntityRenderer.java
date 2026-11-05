package pdx.dev.fbactors.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.morph.MorphRenderInfo;
import pdx.dev.fbactors.morph.MorphRenderState;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderer {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fbactors$renderMorph(Entity entity, double x, double y, double z,
            float yaw, float tickDelta, PoseStack poseStack, MultiBufferSource buffers,
            int light, CallbackInfo ci) {
        if (pdx.dev.fbactors.camera.BoatCam.hides(entity.getUUID())) {
            ci.cancel();
            return;
        }
        MorphRenderInfo info = MorphRenderState.get(entity.getUUID());
        MorphRenderState.noteRenderLookup(entity.getUUID(), info != null && !info.isPlayer());
        if (info == null || info.isPlayer()) {
            return;
        }
        Compat.noteActor(entity);
        MorphRenderState.noteVisibility(entity.getUUID(), false);
        ci.cancel();
    }
}
