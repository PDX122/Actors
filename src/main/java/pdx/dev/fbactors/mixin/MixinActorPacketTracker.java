package pdx.dev.fbactors.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import pdx.dev.fbactors.actor.ActorClonePlayer;
import pdx.dev.fbactors.settings.FbSettings;

@Mixin(ServerEntity.class)
public class MixinActorPacketTracker {
    @Shadow @Final private Entity entity;

    @Group(name = "fbactors$packetCadence", min = 1)
    @ModifyExpressionValue(method = "sendChanges", at = @At(value = "FIELD",
        target = "Lnet/minecraft/server/level/ServerEntity;updateInterval:I"), require = 0)
    private int fbactors$everyTickInterval(int interval) {
        return this.entity instanceof ActorClonePlayer && FbSettings.settings().actorPacketMovement ? 1 : interval;
    }

    @Group(name = "fbactors$packetCadence", min = 1)
    @ModifyExpressionValue(method = "sendChanges", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/UpdateInterval;test(I)Z"), require = 0)
    private boolean fbactors$everyTickSchedule(boolean scheduled) {
        return scheduled || this.entity instanceof ActorClonePlayer && FbSettings.settings().actorPacketMovement;
    }
}
