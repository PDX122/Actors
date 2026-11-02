package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.entity.BoatRenderer.class)
public abstract class MixinActorBoatTexture {

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void fbactors$boatTexture(net.minecraft.world.entity.vehicle.Boat entity, float yaw, float partial, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        pdx.dev.fbactors.actor.BoatTextures.note(entity, entity);
        boolean previous = pdx.dev.fbactors.actor.BoatTextures.begin(entity);
        try { original.call(entity, yaw, partial, pose, buffers, light); } finally { pdx.dev.fbactors.actor.BoatTextures.end(previous); }
    }
}
