package pdx.dev.fbactors.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean fbactors$viewportChange(BlockPos pos, BlockState state, int flags, int depth, Operation<Boolean> original) {
        if (!pdx.dev.fbactors.actor.ActorTerrain.isEditing()) return original.call(pos, state, flags, depth);
        Level level = (Level)(Object)this;
        BlockState before = level.getBlockState(pos);
        boolean changed = original.call(pos, state, flags, depth);
        if (changed) pdx.dev.fbactors.actor.ActorTerrain.changed(level, pos, before, level.getBlockState(pos));
        return changed;
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void fbactors$propagateSimulation(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) pdx.dev.fbactors.actor.ActorWorldSimulation.changed((Level)(Object)this, pos);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"))
    private void fbpactors$captureBlockEdit(BlockPos pos, BlockState state, int flags, int depth,
                                           CallbackInfoReturnable<Boolean> cir) {
        TakeRecorder.captureBlockChange((Level) (Object) this, pos, state);
    }
}
