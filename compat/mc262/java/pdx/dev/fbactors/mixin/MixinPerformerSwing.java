package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import pdx.dev.fbactors.record.TakeRecorder;

@Mixin(LivingEntity.class)
public class MixinPerformerSwing {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void fbactors$swing(InteractionHand hand, boolean broadcast, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        TakeRecorder.captureSwing((LivingEntity)(Object)this, hand);
    }
}
