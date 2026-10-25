package pdx.dev.fbactors.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.filters.Looks;

@Mixin(GameRenderer.class)
public class MixinLookRenderer {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void fbactors$beginMosaic(CallbackInfo ci) { pdx.dev.fbactors.filters.ActorMosaic.beginFrame(); }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void fbactors$applyLook(CallbackInfo ci) { Looks.render(); }

    @Inject(method = "close", at = @At("HEAD"))
    private void fbactors$closeLooks(CallbackInfo ci) { Looks.close(); }
}
