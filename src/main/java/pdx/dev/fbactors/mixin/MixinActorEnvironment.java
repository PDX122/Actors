package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.actor.ActorClonePlayer;
import pdx.dev.fbactors.actor.ActorImpulses;
import pdx.dev.fbactors.actor.BoatPhysics;

@Mixin(Entity.class)
public class MixinActorEnvironment {
    @Unique private Vec3 fbactors$beforeForce;
    @Unique private Vec3 fbactors$beforePiston;
    @Unique private Vec3 fbactors$boatPosition;
    @Unique private Vec3 fbactors$boatVelocity;
    @Unique private boolean fbactors$participates() {
        return (Object)this instanceof ActorClonePlayer || BoatPhysics.driven((Entity)(Object)this);
    }
    @Inject(method = "addDeltaMovement", at = @At("HEAD"))
    private void fbactors$beforeForce(Vec3 force, CallbackInfo ci) {
        if (fbactors$participates() && pdx.dev.fbactors.actor.ActorWorldSimulation.isSimulating()) fbactors$beforeForce = ((Entity)(Object)this).getDeltaMovement();
    }
    @Inject(method = "addDeltaMovement", at = @At("RETURN"))
    private void fbactors$afterForce(Vec3 force, CallbackInfo ci) {
        if (fbactors$participates() && pdx.dev.fbactors.actor.ActorWorldSimulation.isSimulating()) ActorImpulses.environmentChanged((Entity)(Object)this, fbactors$beforeForce);
    }
    @Inject(method = "move", at = @At("HEAD"))
    private void fbactors$beforePiston(MoverType type, Vec3 motion, CallbackInfo ci) {
        Entity entity = (Entity)(Object)this;
        if (type == MoverType.SELF && BoatPhysics.driven(entity)) {
            fbactors$boatPosition = entity.position();
            fbactors$boatVelocity = entity.getDeltaMovement();
        }
        if (type == MoverType.PISTON && fbactors$participates()) fbactors$beforePiston = ((Entity)(Object)this).position();
    }
    @Inject(method = "move", at = @At("RETURN"))
    private void fbactors$afterPiston(MoverType type, Vec3 motion, CallbackInfo ci) {
        Entity entity = (Entity)(Object)this;
        if (type == MoverType.SELF && fbactors$boatPosition != null && BoatPhysics.driven(entity))
            BoatPhysics.bounce(entity, fbactors$boatPosition, fbactors$boatVelocity, motion);
        if (type == MoverType.PISTON && fbactors$beforePiston != null && fbactors$participates()
            && pdx.dev.fbactors.settings.FbSettings.settings().environmentForces
            && entity.position().subtract(fbactors$beforePiston).lengthSqr() > 1.0e-12)
            ActorImpulses.release(entity instanceof ActorClonePlayer ? entity : entity.getFirstPassenger());
    }
}
