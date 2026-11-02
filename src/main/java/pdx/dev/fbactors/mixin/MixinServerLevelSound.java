package pdx.dev.fbactors.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.actor.ItemActionReplay;
import pdx.dev.fbactors.record.TakeRecorder;

@Mixin(ServerLevel.class)
public class MixinServerLevelSound {
    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSeededSound", args = {net.minecraft.world.entity.Entity.class, double.class, double.class, double.class, net.minecraft.core.Holder.class, net.minecraft.sounds.SoundSource.class, float.class, float.class, long.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$position(@org.spongepowered.asm.mixin.injection.Coerce Entity except, double x, double y, double z, Holder<SoundEvent> sound,
                                  SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        if (sound != null) TakeRecorder.capturePlaySound((ServerLevel)(Object)this, except, x,y,z,sound.value(),source,volume,pitch);
    }
    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSeededSound", args = {net.minecraft.world.entity.player.Player.class, double.class, double.class, double.class, net.minecraft.core.Holder.class, net.minecraft.sounds.SoundSource.class, float.class, float.class, long.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$positionLegacy(@org.spongepowered.asm.mixin.injection.Coerce Entity except, double x, double y, double z, Holder<SoundEvent> sound,
                                        SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        if (sound != null) TakeRecorder.capturePlaySound((ServerLevel)(Object)this, except, x,y,z,sound.value(),source,volume,pitch);
    }
    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSeededSound", args = {net.minecraft.world.entity.Entity.class, net.minecraft.world.entity.Entity.class, net.minecraft.core.Holder.class, net.minecraft.sounds.SoundSource.class, float.class, float.class, long.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$entity(@org.spongepowered.asm.mixin.injection.Coerce Entity except, Entity target, Holder<SoundEvent> sound,
                                SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        if (sound != null && target != null) TakeRecorder.capturePlaySound((ServerLevel)(Object)this,
            TakeRecorder.soundCause(except,target),target.getX(),target.getY(),target.getZ(),sound.value(),source,volume,pitch);
    }
    @Inject(target = @org.spongepowered.asm.mixin.injection.Desc(value = "playSeededSound", args = {net.minecraft.world.entity.player.Player.class, net.minecraft.world.entity.Entity.class, net.minecraft.core.Holder.class, net.minecraft.sounds.SoundSource.class, float.class, float.class, long.class}),
        at = @At("HEAD"), require = 0, cancellable = true)
    private void fbactors$entityLegacy(@org.spongepowered.asm.mixin.injection.Coerce Entity except, Entity target, Holder<SoundEvent> sound,
                                      SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (ItemActionReplay.suppressSounds()) { ci.cancel(); return; }
        if (sound != null && target != null) TakeRecorder.capturePlaySound((ServerLevel)(Object)this,
            TakeRecorder.soundCause(except,target),target.getX(),target.getY(),target.getZ(),sound.value(),source,volume,pitch);
    }
}
