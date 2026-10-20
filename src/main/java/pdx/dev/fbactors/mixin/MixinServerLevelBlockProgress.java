package pdx.dev.fbactors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.record.TakeRecorder;


@Mixin(ServerLevel.class)
public class MixinServerLevelBlockProgress {

    @Inject(method = "destroyBlockProgress(ILnet/minecraft/core/BlockPos;I)V", at = @At("HEAD"))
    private void fbpactors$captureDestroyProgress(int id, BlockPos pos, int progress,
                                                  CallbackInfo ci) {
        TakeRecorder.captureDestroyProgress((ServerLevel) (Object) this, id, pos, progress);
    }
}
