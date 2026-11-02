package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.actor.ActorClonePlayer;
import pdx.dev.fbactors.actor.ActorImpulses;

@Mixin(LivingEntity.class)
public class MixinActorImpulse {
    @Unique private Vec3 fbactors$beforeKnockback;
    @Inject(method = "knockback", at = @At("HEAD"))
    private void fbactors$before(CallbackInfo ci) {
        if ((Object)this instanceof ActorClonePlayer clone) fbactors$beforeKnockback = clone.getDeltaMovement();
    }
    @Inject(method = "knockback", at = @At("RETURN"))
    private void fbactors$after(CallbackInfo ci) {
        if ((Object)this instanceof ActorClonePlayer clone && fbactors$beforeKnockback != null
            && clone.getDeltaMovement().subtract(fbactors$beforeKnockback).lengthSqr() > 1.0e-12)
            ActorImpulses.changed(clone);
    }
}
