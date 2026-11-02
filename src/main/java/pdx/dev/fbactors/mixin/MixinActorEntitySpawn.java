package pdx.dev.fbactors.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.ext.ServerLevelExt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.ActorClonePlayer;
import pdx.dev.fbactors.actor.ActorEntityIds;
import pdx.dev.fbactors.actor.BlockEdits;
import pdx.dev.fbactors.record.TakeRecorder;

@Mixin(value = ServerLevel.class, priority = 800)
public abstract class MixinActorEntitySpawn {
    @Unique private boolean fbactors$canSpawnEntities = true;

    @Inject(method = "flashback$setCanSpawnEntities", at = @At("HEAD"), remap = false)
    private void fbactors$rememberSpawnGate(boolean allowed, CallbackInfo ci) {
        fbactors$canSpawnEntities = allowed;
    }

    @WrapMethod(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z")
    private boolean fbactors$allowActorSpawn(Entity entity, Operation<Boolean> original) {
        ServerLevel level = (ServerLevel) (Object) this;
        boolean authored = level.getServer() instanceof ReplayServer && entity != null
            && (BlockEdits.isApplying() || pdx.dev.fbactors.actor.ActorWorldSimulation.isSimulating() || TakeRecorder.canCaptureSpawn(level, entity)
                || entity instanceof Projectile projectile
                    && (pdx.dev.fbactors.actor.ActorWorldSimulation.owner(projectile) instanceof ActorClonePlayer
                        || pdx.dev.fbactors.actor.ActorWorldSimulation.owner(projectile) != null
                            && FlashbackActors.RECORDER.isPerformer(pdx.dev.fbactors.actor.ActorWorldSimulation.owner(projectile).getUUID())));
        if (!authored) return original.call(entity);

        entity.setId(ActorEntityIds.next());
        boolean previous = fbactors$canSpawnEntities;
        ServerLevelExt gate = (ServerLevelExt) level;
        gate.flashback$setCanSpawnEntities(true);
        try {
            boolean spawned = original.call(entity);
            if (spawned) pdx.dev.fbactors.actor.BoatTextures.track(entity);
            if (spawned && pdx.dev.fbactors.actor.ActorWorldSimulation.isSimulating()) pdx.dev.fbactors.actor.ActorWorldSimulation.track(entity);
            return spawned;
        } finally {
            gate.flashback$setCanSpawnEntities(previous);
        }
    }
}
