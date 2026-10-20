package pdx.dev.fbactors.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.record.TakeRecorder;


@Mixin(ServerPlayer.class)
public class MixinContainerOpen {

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
        at = @At("RETURN"))
    private void fbactors$captureContainerOpen(MenuProvider menuProvider,
                                               CallbackInfoReturnable<java.util.OptionalInt> cir) {
        if (menuProvider == null || cir.getReturnValue().isEmpty()) {
            return;
        }
        if (!(menuProvider instanceof BlockEntity be)) {
            return;
        }

        ServerPlayer self = (ServerPlayer) (Object) this;
        ServerLevel level = (ServerLevel) self.level();
        TakeRecorder.captureContainerOpen(level, be.getBlockPos());
    }
}

