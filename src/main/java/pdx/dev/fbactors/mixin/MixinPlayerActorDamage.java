package pdx.dev.fbactors.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.actor.ActorClonePlayer;

@Mixin(Player.class)
public abstract class MixinPlayerActorDamage {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"), require = 0)
    private void fbactors$liftImmunityLegacy(DamageSource source, float amount,
                                             CallbackInfoReturnable<Boolean> cir) {
        fbactors$liftForPlayerAttack(source);
    }

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("RETURN"), require = 0)
    private void fbactors$restoreImmunityLegacy(DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> cir) {
        fbactors$finishDamage(cir.getReturnValueZ());
    }

    @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;"
        + "Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"), require = 0)
    private void fbactors$liftImmunity(ServerLevel level, DamageSource source, float amount,
                                       CallbackInfoReturnable<Boolean> cir) {
        fbactors$liftForPlayerAttack(source);
    }

    @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;"
        + "Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("RETURN"), require = 0)
    private void fbactors$restoreImmunity(ServerLevel level, DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> cir) {
        fbactors$finishDamage(cir.getReturnValueZ());
    }

    @org.spongepowered.asm.mixin.Unique
    private pdx.dev.fbactors.actor.ActorCombat.DamageStart fbactors$damageStart;

    private void fbactors$liftForPlayerAttack(DamageSource source) {
        if ((Object)this instanceof ActorClonePlayer clone) {
            var actor=pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(clone.getUUID());
            if(actor!=null)fbactors$damageStart=actor.combat().beginDamage(clone,source);
        }
    }
    private void fbactors$finishDamage(boolean accepted) {
        if ((Object)this instanceof ActorClonePlayer clone) {
            var actor=pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(clone.getUUID());
            if(actor!=null)actor.combat().finishDamage(clone,fbactors$damageStart,accepted);
            fbactors$damageStart=null;
        }
    }

    @Inject(method = "crit(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void fbactors$noteCrit(Entity target, CallbackInfo ci) {
        fbactors$noteCrit(target, false);
    }

    @Inject(method = "magicCrit(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void fbactors$noteMagicCrit(Entity target, CallbackInfo ci) {
        fbactors$noteCrit(target, true);
    }

    private void fbactors$noteCrit(Entity target, boolean magic) {
        if (!(target instanceof ActorClonePlayer clone)) {
            return;
        }
        pdx.dev.fbactors.actor.Actor victim =
            pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(clone.getUUID());
        if (victim != null) {
            victim.combat().noteCrit(magic);
        }
    }
}
