package pdx.dev.fbactors.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.record.TakeRecorder;


@Mixin(BlockEntity.class)
public class MixinBlockEntityChanged {

    @Inject(method = "setChanged()V", at = @At("RETURN"))
    private void fbactors$recaptureOnDirty(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() == null) {
            return;
        }
        TakeRecorder.captureBlockEntityRefresh(self.getLevel(), self.getBlockPos());
    }
}

