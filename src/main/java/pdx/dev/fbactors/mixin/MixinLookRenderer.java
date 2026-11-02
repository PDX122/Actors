package pdx.dev.fbactors.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinLookRenderer {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void fbactors$beginMosaic(CallbackInfo ci) {
        pdx.dev.fbactors.filters.ActorMosaic.beginFrame();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void fbactors$closeLooks(CallbackInfo ci) {
        pdx.dev.fbactors.filters.Looks.close();
        pdx.dev.fbactors.morph.MorphTracks.finish();
        pdx.dev.fbactors.camera.BoatEdits.finish();
    }
}
