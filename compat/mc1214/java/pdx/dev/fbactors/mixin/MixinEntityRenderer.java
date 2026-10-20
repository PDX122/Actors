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
 * <p>On 1.21.4+, the per-entity {@code EntityRenderer.render} takes an extracted
 * render state rather than the entity, so it is no longer an interception point
 * that receives the actor. The dispatcher's entity-targeted {@code render} is the
 * public entry point that still takes an {@code Entity} directly.
 *
 * <h2>Why a veto and not a model swap</h2>
 *
 * <p>An earlier revision cancelled here and drew the mob itself, which made
 * every frame depend on that one interception holding: any path that rendered
 * past it drew the player (or nothing) for that frame. The mob now renders as
 * itself - a real client-side entity in the level, drawn through the ordinary
 * pipeline - and this mixin only hides the player body underneath it.
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
     * @param poseStack the pose stack
     * @param buffers the buffer source
     * @param light the packed light coordinates
     * @param ci the callback info, used to cancel the default rendering
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fbactors$renderMorph(Entity entity, double x, double y, double z,
            float yaw, PoseStack poseStack, MultiBufferSource buffers, int light,
            CallbackInfo ci) {
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
