package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pdx.dev.fbactors.morph.MorphActionAccess;

@Mixin(LivingEntity.class)
public class MixinMorphActionState implements MorphActionAccess {
    @Unique private String fbactors$action;
    @Override public void fbactors$action(String value) { fbactors$action=value; }
    @Override public String fbactors$action() { return fbactors$action; }
}
