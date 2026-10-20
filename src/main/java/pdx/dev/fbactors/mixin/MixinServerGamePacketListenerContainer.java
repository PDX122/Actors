package pdx.dev.fbactors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.record.TakeRecorder;


@Mixin(targets = "net.minecraft.server.network.ServerGamePacketListenerImpl")
public class MixinServerGamePacketListenerContainer {

    @Inject(method = "handleContainerClick(Lnet/minecraft/network/protocol/game/ServerboundContainerClickPacket;)V",
        at = @At("RETURN"))
    private void fbactors$captureContainerSlot(ServerboundContainerClickPacket packet,
                                                CallbackInfo ci) {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = self.player;
        if (player == null) {
            return;
        }

        int slot = Compat.containerSlot(packet);
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) {
            return;
        }
        if (slot < 0 || slot >= menu.slots.size()) {
            return;
        }

        ItemStack stack = menu.getSlot(slot).getItem();
        BlockPos pos = getContainerPos(player);
        if (pos == null) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        TakeRecorder.captureContainerSlot(level, pos, slot, stack);
    }


    @Inject(method = "handleContainerClose(Lnet/minecraft/network/protocol/game/ServerboundContainerClosePacket;)V",
        at = @At("HEAD"))
    private void fbactors$captureContainerClose(ServerboundContainerClosePacket packet,
                                                 CallbackInfo ci) {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = self.player;
        if (player == null) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) {
            return;
        }

        BlockPos pos = getContainerPos(player);
        if (pos == null) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        TakeRecorder.captureContainerClose(level, pos);
    }


    @Nullable
    private static BlockPos getContainerPos(Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) {
            return null;
        }
        for (Slot slot : menu.slots) {
            if (slot.container instanceof BlockEntity be) {
                return be.getBlockPos();
            }
        }
        return null;
    }
}

