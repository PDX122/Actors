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

/**
 * Hides a morphed actor's player body so only its mob puppet shows.
 *
 * <h2>Why a veto and not a model swap</h2>
 *
 * <p>An earlier revision intercepted state creation and returned the mob's
 * state in place of the player's. That made every frame's rendering depend on
 * which code path created the state: any path that built it another way drew
 * the player (or nothing) for that frame, which reads as flicker. The mob now
 * renders as itself - a real client-side entity in the level, drawn through
 * the ordinary pipeline with the right type, box, shadow and culling - and
 * this mixin only hides the player body underneath it.
 *
 * <p>A missed veto reads as a double render (player and mob together), never
 * as a disappearance, which is what makes this shape diagnosable where the
 * swap was not: there is no failure mode here that removes the actor.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    /**
     * Hides the player body of a morphed actor.
     *
     * <p>Runs before anything is extracted or submitted, so a vetoed actor
     * costs one map lookup and nothing else. The lookup also feeds the puppet
     * sync with the actor entity it just saw, which is how the sync finds the
     * client body without scanning the level.
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void fbactors$hideMorphedPlayer(Entity entity, Frustum frustum, double x,
            double y, double z, float tickDelta, CallbackInfoReturnable<Boolean> cir) {
        MorphRenderInfo info = MorphRenderState.get(entity.getUUID());
        MorphRenderState.noteRenderLookup(entity.getUUID(), info != null && !info.isPlayer());
        if (info == null || info.isPlayer()) {
            return;
        }
        Compat.noteActor(entity);
        MorphRenderState.noteVisibility(entity.getUUID(), false);
        cir.setReturnValue(false);
    }

    /**
     * Watches the visibility decision for formerly morphed actors.
     *
     * <p>Morphed actors never reach this point (the veto above returns
     * first), so this only ever fires for actors whose morph just cleared -
     * exactly the transition worth one log line.
     */
    @Inject(method = "shouldRender", at = @At("RETURN"))
    private void fbactors$noteVisibility(Entity entity, Frustum frustum, double x,
            double y, double z, float tickDelta, CallbackInfoReturnable<Boolean> cir) {
        MorphRenderState.noteVisibility(entity.getUUID(), cir.getReturnValue());
    }
}
