package pdx.dev.fbactors.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(LivingEntityRenderer.class)
public abstract class MixinSittingClone {

    @Redirect(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z"),
        require = 0)
    private boolean fbactors$seatedCountsAsPassenger(LivingEntity entity) {
        if (entity.getPose() == Pose.SITTING) {
            return true;
        }
        return entity.isPassenger();
    }

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z"),
        require = 0)
    private boolean fbactors$seatedCountsAsPassengerOld(LivingEntity entity) {
        if (entity.getPose() == Pose.SITTING) {
            return true;
        }
        return entity.isPassenger();
    }
}

