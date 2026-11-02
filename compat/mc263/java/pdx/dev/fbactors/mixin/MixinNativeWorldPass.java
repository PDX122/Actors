package pdx.dev.fbactors.mixin;

import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import com.mojang.renderpearl.api.commands.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.filters.NativeActorMasks;

@Mixin(value = FrontendCommandEncoder.class, remap = false)
public abstract class MixinNativeWorldPass {
    @Inject(method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)Lcom/mojang/renderpearl/api/commands/RenderPass;", at = @At("RETURN"))
    private void fbactors$pass(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPass> ci) {
        NativeActorMasks.track((CommandEncoder) (Object) this, ci.getReturnValue(), descriptor);
    }
}
