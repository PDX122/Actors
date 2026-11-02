package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import pdx.dev.fbactors.morph.CatMorphPoseAccess;

@Mixin(Cat.class)
public abstract class MixinCatMorphPose implements CatMorphPoseAccess {
    @Shadow private float lieDownAmount;
    @Shadow private float lieDownAmountO;
    @Shadow private float lieDownAmountTail;
    @Shadow private float lieDownAmountOTail;
    @Shadow private float relaxStateOneAmount;
    @Shadow private float relaxStateOneAmountO;

    @Override public void fbactors$relaxing(boolean relaxing) {
        float amount=relaxing?1:0;
        lieDownAmount=lieDownAmountO=amount;
        lieDownAmountTail=lieDownAmountOTail=amount;
        relaxStateOneAmount=relaxStateOneAmountO=amount;
    }
}
