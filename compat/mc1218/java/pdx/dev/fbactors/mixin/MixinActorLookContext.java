package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.entity.LivingEntityRenderer.class)
public abstract class MixinActorLookContext {

    @org.spongepowered.asm.mixin.injection.Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @org.spongepowered.asm.mixin.injection.At("TAIL"))
    private void fbactors$lookState(net.minecraft.world.entity.LivingEntity entity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, float partial, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        pdx.dev.fbactors.filters.ActorLooks.note(state, entity);
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void fbactors$actorLook(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        java.util.UUID previous = pdx.dev.fbactors.filters.ActorLooks.begin(state);
        try { original.call(state, pose, buffers, light); } finally { pdx.dev.fbactors.filters.ActorLooks.end(previous); }
    }
}
