package pdx.dev.fbactors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.record.TakeRecorder;


@Mixin(Level.class)
public class MixinServerLevelBlockEntity {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"))
    private void fbactors$captureBlockEntityBeforeBreak(BlockPos pos, BlockState newState, int flags, int depth,
                                                       CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        BlockState before = level.getBlockState(pos);
        if (!before.isAir() && Compat.hasBlockEntity(level, pos)) {


            String type = blockEntityType(level, before);
            TakeRecorder.captureBlockEntity(level, pos, type);
        }
    }


    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("RETURN"))
    private void fbactors$captureBlockEntityAfterPlace(BlockPos pos, BlockState newState, int flags, int depth,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        Level level = (Level) (Object) this;
        if (!newState.isAir() && Compat.hasBlockEntity(level, pos)) {
            String type = blockEntityType(level, newState);
            TakeRecorder.captureBlockEntity(level, pos, type);
        }
    }


    private static String blockEntityType(Level level, BlockState state) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null ? key.getPath() : "unknown";
    }
}

