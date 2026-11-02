package pdx.dev.fbactors.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.actor.ActorClonePlayer;

@Mixin(ServerLevel.class)
public class MixinActorPassengerTick {
    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void fbactors$seatWithoutDoubleTick(Entity vehicle, Entity passenger, CallbackInfo ci) {
        if (passenger instanceof ActorClonePlayer) {
            if (!passenger.isRemoved() && passenger.getVehicle() == vehicle) vehicle.positionRider(passenger);
            else passenger.stopRiding();
            ci.cancel();
        }
    }
}
