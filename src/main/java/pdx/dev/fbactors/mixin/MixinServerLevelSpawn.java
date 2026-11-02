package pdx.dev.fbactors.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.record.TakeRecorder;

@Mixin(ServerLevel.class)
public class MixinServerLevelSpawn {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("RETURN"))
    private void fbactors$captureSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null || !cir.getReturnValueZ()) {
            return;
        }
        ServerLevel level = (ServerLevel) (Object) this;
        TakeRecorder.captureSpawn(level, entity);
        TakeRecorder.captureProjectile(entity);
    }

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
    private void fbactors$trackProjectile(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && entity != null) pdx.dev.fbactors.actor.ItemActionReplay.trackProjectile(entity);
    }
}
