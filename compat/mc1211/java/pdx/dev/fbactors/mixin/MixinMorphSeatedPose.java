package pdx.dev.fbactors.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pdx.dev.fbactors.morph.MorphActionAccess;

@Mixin(LivingEntityRenderer.class)
public class MixinMorphSeatedPose {
    @ModifyExpressionValue(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z",ordinal=0))
    private boolean fbactors$seatedModel(boolean original,@Local(argsOnly=true) LivingEntity entity) {
        return original || MorphActionAccess.seated(entity);
    }
}
