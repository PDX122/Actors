package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.actor.ActorClonePlayer;
import pdx.dev.fbactors.actor.ActorImpulses;

@Mixin(Entity.class)
public class MixinActorPush {
    @Unique private Vec3 fbactors$beforePush;
    @Inject(method = "push(DDD)V", at = @At("HEAD"))
    private void fbactors$beforePush(double x, double y, double z, CallbackInfo ci) {
        Entity entity = (Entity)(Object)this;
        if (entity instanceof ActorClonePlayer || pdx.dev.fbactors.actor.BoatPhysics.driven(entity)) fbactors$beforePush = entity.getDeltaMovement();
    }
    @Inject(method = "push(DDD)V", at = @At("RETURN"))
    private void fbactors$afterPush(double x, double y, double z, CallbackInfo ci) {
        if ((Object)this instanceof ActorClonePlayer clone && fbactors$beforePush != null
            && clone.getDeltaMovement().subtract(fbactors$beforePush).lengthSqr() > 1.0e-12)
            ActorImpulses.changed(clone);
        else if (pdx.dev.fbactors.actor.BoatPhysics.driven((Entity)(Object)this))
            ActorImpulses.vehicleChanged((Entity)(Object)this, fbactors$beforePush);
    }
}
