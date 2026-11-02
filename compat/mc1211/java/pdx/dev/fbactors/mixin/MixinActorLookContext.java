package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.entity.LivingEntityRenderer.class)
public abstract class MixinActorLookContext {

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void fbactors$actorLook(net.minecraft.world.entity.LivingEntity entity, float yaw, float partial, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        java.util.UUID previous = pdx.dev.fbactors.filters.ActorLooks.begin(entity);
        try { original.call(entity, yaw, partial, pose, buffers, light); } finally { pdx.dev.fbactors.filters.ActorLooks.end(previous); }
    }
}
