package pdx.dev.fbactors.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pdx.dev.fbactors.actor.ActorClonePlayer;
import pdx.dev.fbactors.actor.ActorImpulses;

@Mixin(Player.class)
public class MixinActorKnockback {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"), require = 1)
    private void fbactors$keepServerImpulse(Entity victim, Vec3 reset, Operation<Void> original) {
        if (victim instanceof ActorClonePlayer clone) ActorImpulses.changed(clone);
        else original.call(victim, reset);
    }
}
