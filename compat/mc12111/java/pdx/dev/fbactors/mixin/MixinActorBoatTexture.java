package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.entity.AbstractBoatRenderer.class)
public abstract class MixinActorBoatTexture {

    @org.spongepowered.asm.mixin.injection.Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;Lnet/minecraft/client/renderer/entity/state/BoatRenderState;F)V", at = @org.spongepowered.asm.mixin.injection.At("TAIL"))
    private void fbactors$boatState(net.minecraft.world.entity.vehicle.boat.AbstractBoat entity, net.minecraft.client.renderer.entity.state.BoatRenderState state, float partial, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        pdx.dev.fbactors.actor.BoatTextures.note(state, entity);
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "submit(Lnet/minecraft/client/renderer/entity/state/BoatRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V")
    private void fbactors$boatTexture(net.minecraft.client.renderer.entity.state.BoatRenderState state, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.CameraRenderState camera, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        boolean previous = pdx.dev.fbactors.actor.BoatTextures.begin(state);
        try { original.call(state, pose, collector, camera); } finally { pdx.dev.fbactors.actor.BoatTextures.end(previous); }
    }
}
