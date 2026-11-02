package pdx.dev.fbactors.mixin;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.renderpearl.api.pipeline.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.filters.NativeActorMasks;

@Mixin(value = PipelineCache.class, remap = false)
public abstract class MixinNativePipelineSource {
    @Shadow @Final private ShaderSource shaderSource;
    @Inject(method = "get", at = @At("RETURN"))
    private void fbactors$source(RenderPipeline pipeline, CallbackInfoReturnable<CompiledRenderPipeline> ci) {
        NativeActorMasks.source(pipeline, ci.getReturnValue(), shaderSource);
    }
}
