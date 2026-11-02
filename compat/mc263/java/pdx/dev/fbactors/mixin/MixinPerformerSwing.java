package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import pdx.dev.fbactors.record.TakeRecorder;

@Mixin(LivingEntity.class)
public class MixinPerformerSwing {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("RETURN"))
    private void fbactors$swing(InteractionHand hand, net.minecraft.world.item.component.SwingAnimation animation, boolean broadcast, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) TakeRecorder.captureSwing((LivingEntity)(Object)this, hand, animation.type().name(), animation.duration());
    }
}
