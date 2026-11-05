package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.RenderType.class)
public abstract class MixinActorLookMaterials {
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = {"armorCutoutNoCull", "entitySolid", "entityCutout", "entityCutoutNoCull", "entityCutoutNoCullZOffset", "entityTranslucentCull", "entityTranslucent", "entityTranslucentEmissive", "entitySmoothCutout", "entityDecal", "entityNoOutline", "eyes", "breezeEyes", "breezeWind", "energySwirl"}, at = @org.spongepowered.asm.mixin.injection.At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static net.minecraft.resources.ResourceLocation fbactors$materialLook(net.minecraft.resources.ResourceLocation texture) {
        if (pdx.dev.fbactors.actor.BoatTextures.active())
            texture = pdx.dev.fbactors.compat.Compat.boatTextureId(pdx.dev.fbactors.actor.BoatTextures.texture(texture.toString()));
        if(texture!=null) pdx.dev.fbactors.camera.CameraPreviewCapture.material(texture.toString());
        return pdx.dev.fbactors.filters.ActorLookBridge.apply(texture);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method={"armorCutoutNoCull", "entitySolid", "entityCutout", "entityCutoutNoCull", "entityCutoutNoCullZOffset", "entityTranslucentCull", "entityTranslucent", "entityTranslucentEmissive", "entitySmoothCutout", "entityDecal", "entityNoOutline", "eyes", "breezeEyes", "breezeWind", "energySwirl"},at=@org.spongepowered.asm.mixin.injection.At("RETURN"),require=0)
    private static void fbactors$previewMaterial(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Object> result,
        @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.minecraft.resources.ResourceLocation texture) {
        if(texture!=null) pdx.dev.fbactors.camera.CameraPreviewCapture.material(result.getReturnValue(),texture.toString());
    }

}
