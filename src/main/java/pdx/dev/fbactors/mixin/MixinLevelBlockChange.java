package pdx.dev.fbactors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.record.TakeRecorder;

@Mixin(Level.class)
public class MixinLevelBlockChange {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"))
    private void fbpactors$captureBlockEdit(BlockPos pos, BlockState state, int flags, int depth,
                                           CallbackInfoReturnable<Boolean> cir) {
        TakeRecorder.captureBlockChange((Level) (Object) this, pos, state);
    }
}

