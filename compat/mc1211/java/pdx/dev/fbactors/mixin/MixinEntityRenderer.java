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

/**
 * Hides a morphed actor's player body so only its mob puppet shows.
 *
 * <h2>Why this targets {@code EntityRenderDispatcher} and not {@code EntityRenderer}</h2>
 *
 * <p>Player rendering is dispatched to {@code LivingEntityRenderer} and
 * {@code PlayerRenderer} overrides, so an inject on the base
 * {@code EntityRenderer.render} never fires for actors. The dispatcher's
 * entity-targeted {@code render} is the one entry point every entity passes
 * through.
 *
 * <h2>Why a veto and not a model swap</h2>
 *
 * <p>An earlier revision cancelled here and drew the mob itself, which made
 * every frame depend on that one interception holding: any path that rendered
 * past it drew the player (or nothing) for that frame. The mob now renders as
 * itself - a real client-side entity in the level, drawn through the ordinary
 * pipeline - and this mixin only hides the player body underneath it.
 *
 * <p>On 1.21.1 that entry point takes both yaw and tick delta:
 * {@code render(E, double, double, double, float, float, PoseStack,
 * MultiBufferSource, int)}. The single-float shape the 1.21.4+ mixins declare
 * does not exist here, so this mixin declares both floats.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderer {

    /**
     * Hides the player body of a morphed actor.
     *
     * <p>The lookup also feeds the puppet sync with the actor entity it just
     * saw, which is how the sync finds the client body without scanning the
     * level.
     *
     * @param entity the entity being rendered
     * @param x the entity's x position
     * @param y the entity's y position
     * @param z the entity's z position
     * @param yaw the entity's yaw
     * @param tickDelta the render tick delta
     * @param poseStack the pose stack
     * @param buffers the buffer source
     * @param light the packed light coordinates
     * @param ci the callback info, used to cancel the default rendering
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fbactors$renderMorph(Entity entity, double x, double y, double z,
            float yaw, float tickDelta, PoseStack poseStack, MultiBufferSource buffers,
            int light, CallbackInfo ci) {
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
