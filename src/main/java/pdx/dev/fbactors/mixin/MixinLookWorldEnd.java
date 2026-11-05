package pdx.dev.fbactors.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.filters.Looks;

@Mixin(LevelRenderer.class)
public class MixinLookWorldEnd {
    @Inject(method = {"renderLevel", "render"}, at = @At("TAIL"), require = 1)
    private void fbactors$worldLook(CallbackInfo ci) { pdx.dev.fbactors.events.EventRenderer.render(); Looks.render(); }
}
