package pdx.dev.fbactors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.record.TakeRecorder;

import java.util.UUID;

@Mixin(targets = "net.minecraft.server.network.ServerGamePacketListenerImpl")
public class MixinServerGamePacketListenerUse {

    @Inject(method = "handleUseItemOn(Lnet/minecraft/network/protocol/game/ServerboundUseItemOnPacket;)V",
        at = @At("HEAD"))
    private void fbactors$captureUseOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = self.player;
        if (player == null) {
            return;
        }
        BlockPos pos = Compat.useOnPos(packet);
        if (pos == null) {
            return;
        }
        pdx.dev.fbactors.actor.ActorWorldSimulation.touch((ServerLevel)player.level(), pos);
        TakeRecorder.captureUseBlock((ServerLevel) player.level(), pos, Compat.useOnOffHand(packet));
    }

    @Inject(method = "handleUseItem(Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;)V",
        at = @At("HEAD"))
    private void fbactors$captureUseAir(ServerboundUseItemPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = self.player;
        if (player == null) {
            return;
        }
        TakeRecorder.captureUseAir((ServerLevel) player.level(), Compat.useItemOffHand(packet));
    }

    @Inject(method = "handleInteract(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;)V",
        at = @At("HEAD"))
    private void fbactors$captureUseEntity(ServerboundInteractPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = self.player;
        if (player == null) {
            return;
        }
        if (Compat.interactIsAttack(packet)) {
            return;
        }
        UUID target = Compat.interactTarget(packet, (ServerLevel) player.level());
        TakeRecorder.captureUseEntity((ServerLevel) player.level(), target, Compat.interactOffHand(packet));
    }
}
