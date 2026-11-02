package pdx.dev.fbactors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.record.TakeRecorder;

@Mixin(Level.class)
public class MixinLevelSound {

    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSound", args = {net.minecraft.world.entity.Entity.class, net.minecraft.core.BlockPos.class, net.minecraft.sounds.SoundEvent.class, net.minecraft.sounds.SoundSource.class, float.class, float.class}),
        at = @At("HEAD"), cancellable = true)
    private void fbactors$captureSoundAtPos(@org.spongepowered.asm.mixin.injection.Coerce Entity except, BlockPos pos, SoundEvent sound,
                                            SoundSource source, float volume, float pitch,
                                            CallbackInfo ci) {
        if (pdx.dev.fbactors.actor.ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            sound, source, volume, pitch);
    }

    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSound", args = {net.minecraft.world.entity.player.Player.class, net.minecraft.core.BlockPos.class, net.minecraft.sounds.SoundEvent.class, net.minecraft.sounds.SoundSource.class, float.class, float.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$captureSoundAtPosLegacy(@org.spongepowered.asm.mixin.injection.Coerce Entity except, BlockPos pos, SoundEvent sound,
                                                  SoundSource source, float volume, float pitch,
                                                  CallbackInfo ci) {
        if (pdx.dev.fbactors.actor.ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            sound, source, volume, pitch);
    }

    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSound", args = {net.minecraft.world.entity.Entity.class, double.class, double.class, double.class, net.minecraft.sounds.SoundEvent.class, net.minecraft.sounds.SoundSource.class, float.class, float.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$captureSoundAtCoords(@org.spongepowered.asm.mixin.injection.Coerce Entity except, double x, double y, double z,
                                               SoundEvent sound, SoundSource source,
                                               float volume, float pitch, CallbackInfo ci) {
        if (pdx.dev.fbactors.actor.ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            x, y, z, sound, source, volume, pitch);
    }

    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSound", args = {net.minecraft.world.entity.player.Player.class, double.class, double.class, double.class, net.minecraft.sounds.SoundEvent.class, net.minecraft.sounds.SoundSource.class, float.class, float.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$captureSoundAtCoordsLegacy(@org.spongepowered.asm.mixin.injection.Coerce Entity except, double x, double y, double z,
                                                     SoundEvent sound, SoundSource source,
                                                     float volume, float pitch, CallbackInfo ci) {
        if (pdx.dev.fbactors.actor.ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            x, y, z, sound, source, volume, pitch);
    }

    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSound", args = {net.minecraft.world.entity.Entity.class, double.class, double.class, double.class, net.minecraft.sounds.SoundEvent.class, net.minecraft.sounds.SoundSource.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$captureSoundAtCoordsDefaulted(@org.spongepowered.asm.mixin.injection.Coerce Entity except, double x, double y, double z,
                                                        SoundEvent sound, SoundSource source,
                                                        CallbackInfo ci) {
        if (pdx.dev.fbactors.actor.ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            x, y, z, sound, source, 1.0f, 1.0f);
    }

    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSound", args = {net.minecraft.world.entity.player.Player.class, double.class, double.class, double.class, net.minecraft.sounds.SoundEvent.class, net.minecraft.sounds.SoundSource.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$captureSoundAtCoordsDefaultedLegacy(@org.spongepowered.asm.mixin.injection.Coerce Entity except, double x, double y,
                                                              double z, SoundEvent sound,
                                                              SoundSource source, CallbackInfo ci) {
        if (pdx.dev.fbactors.actor.ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        TakeRecorder.capturePlaySound((Level) (Object) this, except,
            x, y, z, sound, source, 1.0f, 1.0f);
    }

    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSound", args = {net.minecraft.world.entity.Entity.class, net.minecraft.world.entity.Entity.class, net.minecraft.sounds.SoundEvent.class, net.minecraft.sounds.SoundSource.class, float.class, float.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$captureSoundAtEntity(@org.spongepowered.asm.mixin.injection.Coerce Entity except, Entity target, SoundEvent sound,
                                               SoundSource source, float volume, float pitch,
                                               CallbackInfo ci) {
        if (pdx.dev.fbactors.actor.ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        if (target != null) {
            TakeRecorder.capturePlaySound((Level) (Object) this, TakeRecorder.soundCause(except, target),
                target.getX(), target.getY(), target.getZ(),
                sound, source, volume, pitch);
        }
    }

    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSound", args = {net.minecraft.world.entity.player.Player.class, net.minecraft.world.entity.Entity.class, net.minecraft.sounds.SoundEvent.class, net.minecraft.sounds.SoundSource.class, float.class, float.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$captureSoundAtEntityLegacy(@org.spongepowered.asm.mixin.injection.Coerce Entity except, Entity target, SoundEvent sound,
                                                     SoundSource source, float volume, float pitch,
                                                     CallbackInfo ci) {
        if (pdx.dev.fbactors.actor.ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        if (target != null) {
            TakeRecorder.capturePlaySound((Level) (Object) this, TakeRecorder.soundCause(except, target),
                target.getX(), target.getY(), target.getZ(),
                sound, source, volume, pitch);
        }
    }

    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSound", args = {net.minecraft.world.entity.Entity.class, net.minecraft.world.entity.Entity.class, net.minecraft.core.Holder.class, net.minecraft.sounds.SoundSource.class, float.class, float.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$captureSoundAtEntityHolder(@org.spongepowered.asm.mixin.injection.Coerce Entity except, Entity target,
                                                      net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> holder,
                                                      SoundSource source, float volume, float pitch,
                                                      CallbackInfo ci) {
        if (pdx.dev.fbactors.actor.ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        if (target == null) {
            return;
        }
        SoundEvent sound = null;
        try {
            sound = holder == null ? null : holder.value();
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (sound != null) {
            TakeRecorder.capturePlaySound((Level) (Object) this, TakeRecorder.soundCause(except, target),
                target.getX(), target.getY(), target.getZ(),
                sound, source, volume, pitch);
        }
    }
}
