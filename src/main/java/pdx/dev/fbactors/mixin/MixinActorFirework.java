package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import pdx.dev.fbactors.actor.ProjectileAttachment;

@Mixin(FireworkRocketEntity.class)
public abstract class MixinActorFirework implements ProjectileAttachment {
    @Shadow private LivingEntity attachedToEntity;
    @Override public LivingEntity fbactors$attachedTo() { return attachedToEntity; }
}
