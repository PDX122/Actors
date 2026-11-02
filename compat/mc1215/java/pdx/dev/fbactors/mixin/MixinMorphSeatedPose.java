package pdx.dev.fbactors.mixin;

import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.morph.MorphActionAccess;

@Mixin(HumanoidMobRenderer.class)
public class MixinMorphSeatedPose {
    @Inject(method="extractHumanoidRenderState",at=@At("RETURN"))
    private static void fbactors$seatedModel(LivingEntity entity,HumanoidRenderState state,float partial,ItemModelResolver items,CallbackInfo ci) {
        if(MorphActionAccess.seated(entity)) state.isPassenger=true;
    }
}
