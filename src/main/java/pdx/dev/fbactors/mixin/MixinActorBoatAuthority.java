package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.actor.BoatPhysics;

@Mixin(Entity.class)
public class MixinActorBoatAuthority {
    @Group(name = "fbactors$boatAuthority", min = 1)
    @Inject(method = {"isControlledByLocalInstance", "isLocalInstanceAuthoritative"},
        at = @At("RETURN"), cancellable = true, require = 0)
    private void fbactors$serverDrivesActorBoat(CallbackInfoReturnable<Boolean> ci) {
        Entity entity = (Entity)(Object)this;
        if (BoatPhysics.driven(entity)) ci.setReturnValue(true);
    }
}
