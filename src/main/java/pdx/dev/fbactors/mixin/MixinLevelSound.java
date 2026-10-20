package pdx.dev.fbactors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.record.TakeRecorder;


@Mixin(Level.class)
public class MixinLevelSound {

    @Inject(method = "playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
        at = @At("HEAD"))
    private void fbactors$captureSoundAtPos(Entity except, BlockPos pos, SoundEvent sound,
                                            SoundSource source, float volume, float pitch,
                                            CallbackInfo ci) {
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            sound, source, volume, pitch);
    }

    @Inject(method = "playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
        at = @At("HEAD"), require = 0)
    private void fbactors$captureSoundAtPosLegacy(Player except, BlockPos pos, SoundEvent sound,
                                                  SoundSource source, float volume, float pitch,
                                                  CallbackInfo ci) {
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            sound, source, volume, pitch);
    }

    @Inject(method = "playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
        at = @At("HEAD"), require = 0)
    private void fbactors$captureSoundAtCoords(Entity except, double x, double y, double z,
                                               SoundEvent sound, SoundSource source,
                                               float volume, float pitch, CallbackInfo ci) {
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            x, y, z, sound, source, volume, pitch);
    }

    @Inject(method = "playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
        at = @At("HEAD"), require = 0)
    private void fbactors$captureSoundAtCoordsLegacy(Player except, double x, double y, double z,
                                                     SoundEvent sound, SoundSource source,
                                                     float volume, float pitch, CallbackInfo ci) {
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            x, y, z, sound, source, volume, pitch);
    }

    @Inject(method = "playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;)V",
        at = @At("HEAD"), require = 0)
    private void fbactors$captureSoundAtCoordsDefaulted(Entity except, double x, double y, double z,
                                                        SoundEvent sound, SoundSource source,
                                                        CallbackInfo ci) {
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            x, y, z, sound, source, 1.0f, 1.0f);
    }

    @Inject(method = "playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;)V",
        at = @At("HEAD"), require = 0)
    private void fbactors$captureSoundAtCoordsDefaultedLegacy(Player except, double x, double y,
                                                              double z, SoundEvent sound,
                                                              SoundSource source, CallbackInfo ci) {
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            x, y, z, sound, source, 1.0f, 1.0f);
    }

    @Inject(method = "playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
        at = @At("HEAD"), require = 0)
    private void fbactors$captureSoundAtEntity(Entity except, Entity target, SoundEvent sound,
                                               SoundSource source, float volume, float pitch,
                                               CallbackInfo ci) {
        if (target != null) {
            TakeRecorder.capturePlaySound((Level) (Object) this, except,
                target.getX(), target.getY(), target.getZ(),
                sound, source, volume, pitch);
        }
    }

    @Inject(method = "playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
        at = @At("HEAD"), require = 0)
    private void fbactors$captureSoundAtEntityLegacy(Player except, Entity target, SoundEvent sound,
                                                     SoundSource source, float volume, float pitch,
                                                     CallbackInfo ci) {
        if (target != null) {
            TakeRecorder.capturePlaySound((Level) (Object) this, except,
                target.getX(), target.getY(), target.getZ(),
                sound, source, volume, pitch);
        }
    }


    @Inject(method = "playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FF)V",
        at = @At("HEAD"), require = 0)
    private void fbactors$captureSoundAtEntityHolder(Entity except, Entity target,
                                                      net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> holder,
                                                      SoundSource source, float volume, float pitch,
                                                      CallbackInfo ci) {
        if (target == null) {
            return;
        }
        SoundEvent sound = null;
        try {
            sound = holder == null ? null : holder.value();
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (sound != null) {
            TakeRecorder.capturePlaySound((Level) (Object) this, except,
                target.getX(), target.getY(), target.getZ(),
                sound, source, volume, pitch);
        }
    }
}

