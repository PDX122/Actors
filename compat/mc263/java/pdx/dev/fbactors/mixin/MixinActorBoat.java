package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.actor.ActorClonePlayer;
import pdx.dev.fbactors.actor.ActorImpulses;
import pdx.dev.fbactors.actor.BoatPhysics;

@Mixin(AbstractBoat.class)
public abstract class MixinActorBoat implements BoatPhysics.Boat {
    @Shadow private float deltaRotation;
    @Shadow protected abstract void controlBoat();
    @Shadow public abstract void setInput(boolean left, boolean right, boolean forward, boolean back);
    @Shadow public abstract float getRowingTime(int side, float partial);
    @Shadow public abstract boolean getPaddleState(int side);
    @Shadow public abstract void setPaddleState(boolean left, boolean right);
    @Override public float fbactors$rowingTime(int side, float partial) { return getRowingTime(side, partial); }
    @Override public boolean fbactors$paddle(int side) { return getPaddleState(side); }
    @Override public void fbactors$paddles(boolean left, boolean right) { setPaddleState(left, right); }
    @Inject(method = "getRowingTime", at = @At("HEAD"), cancellable = true)
    private void fbactors$recordedStroke(int side, float partial, CallbackInfoReturnable<Float> ci) {
        float phase = BoatPhysics.rowing((Entity)(Object)this, side, partial);
        if (Float.isFinite(phase)) ci.setReturnValue(phase);
    }
    @Unique private net.minecraft.world.phys.Vec3 fbactors$beforeBump;
    @Unique private net.minecraft.world.phys.Vec3 fbactors$beforeFloat;
    @Inject(method = "floatBoat", at = @At("HEAD"))
    private void fbactors$beforeFloat(CallbackInfo ci) {
        fbactors$beforeFloat = ((Entity)(Object)this).getDeltaMovement();
    }

    @Inject(method = "floatBoat", at = @At("RETURN"))
    private void fbactors$steer(CallbackInfo ci) {
        Entity boat = (Entity)(Object)this;
        if (boat.getFirstPassenger() instanceof ActorClonePlayer driver) {
            setInput(driver.xxa > 0.002f, driver.xxa < -0.002f, driver.zza > 0.002f, driver.zza < -0.002f);
            BoatPhysics.grip(boat, fbactors$beforeFloat);
            var before = boat.getDeltaMovement();
            float turn = deltaRotation;
            float heading = boat.getYRot();
            controlBoat();
            var settings = pdx.dev.fbactors.settings.FbSettings.settings();
            var after = boat.getDeltaMovement();
            boat.setDeltaMovement(pdx.dev.fbactors.actor.BoatHandling.propulsion(before.x, after.x, settings.boatSpeed), after.y,
                pdx.dev.fbactors.actor.BoatHandling.propulsion(before.z, after.z, settings.boatSpeed));
            deltaRotation = (float)pdx.dev.fbactors.actor.BoatHandling.propulsion(turn, deltaRotation, settings.boatSteering);
            boat.setYRot((float)pdx.dev.fbactors.actor.BoatHandling.propulsion(heading, boat.getYRot(), settings.boatSteering));
        }
    }
    @Inject(method = "tick", at = @At("RETURN"))
    private void fbactors$afterTick(CallbackInfo ci) {
        Entity boat = (Entity)(Object)this;
        BoatPhysics.ticked(boat);

    }
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void fbactors$beforeBump(Entity other, CallbackInfo ci) {
        fbactors$beforeBump = ((Entity)(Object)this).getDeltaMovement();
    }
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void fbactors$afterBump(Entity other, CallbackInfo ci) {
        Entity boat = (Entity)(Object)this;
        if (fbactors$beforeBump != null && boat.getDeltaMovement().subtract(fbactors$beforeBump).lengthSqr() > 1.0e-12
            && boat.getFirstPassenger() instanceof ActorClonePlayer) ActorImpulses.vehicleChanged(boat, fbactors$beforeBump);
    }
}
