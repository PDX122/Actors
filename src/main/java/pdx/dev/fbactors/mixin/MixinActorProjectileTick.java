package pdx.dev.fbactors.mixin;

import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.actor.ActorWorldSimulation;

@Mixin(value = TickRateManager.class, priority = 900)
public class MixinActorProjectileTick {
    @Inject(method = "isEntityFrozen", at = @At("RETURN"), cancellable = true)
    private void fbactors$projectileTick(Entity entity, CallbackInfoReturnable<Boolean> ci) {

        if (pdx.dev.fbactors.actor.BoatPhysics.driven(entity) || ActorWorldSimulation.manualFirework(entity)) ci.setReturnValue(true);
        else if (ActorWorldSimulation.shouldTick(entity)) ci.setReturnValue(false);
    }
}
