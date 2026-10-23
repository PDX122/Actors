package pdx.dev.fbactors.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.record.TakeRecorder;

/**
 * Captures performer-placed entities with measured state.
 *
 * <p>Fires on the entity vanilla really added, with concrete position,
 * rotation and type - never predicted. Attribution comes from recency in
 * {@link TakeRecorder#captureSpawn}: a use in the last 3 ticks within 8
 * blocks. Playback-spawned entities are excluded by the applying guard.
 */
@Mixin(ServerLevel.class)
public class MixinServerLevelSpawn {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("HEAD"))
    private void fbactors$captureSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) {
            return;
        }
        ServerLevel level = (ServerLevel) (Object) this;
        TakeRecorder.captureSpawn(level, entity);
    }
}
