package pdx.dev.fbactors.mixin;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.playback.ReplayPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.settings.FbSettings;

@Mixin(ReplayPlayer.class)
public abstract class MixinPerformerDamage extends ServerPlayer {
    protected MixinPerformerDamage(MinecraftServer server, ServerLevel level,
                                    GameProfile profile, ClientInformation info) {
        super(server, level, profile, info);
    }

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void fbactors$damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if (ActorMode.phase() == ActorMode.Phase.RECORDING && FbSettings.combatEnabled()) {
            ci.setReturnValue(super.hurt(source, amount));
        }
    }

    @Inject(method = "isInvulnerableTo(Lnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("HEAD"), cancellable = true)
    private void fbactors$immunity(DamageSource source, CallbackInfoReturnable<Boolean> ci) {
        if (ActorMode.phase() == ActorMode.Phase.RECORDING && FbSettings.combatEnabled()) {
            ci.setReturnValue(super.isInvulnerableTo(source));
        }
    }
}
