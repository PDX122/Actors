package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.rendertype.RenderTypes.class)
public abstract class MixinActorLookMaterials {
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = {"armorCutoutNoCull", "armorTranslucent", "entitySolid", "entitySolidZOffsetForward", "entityCutout", "entityCutoutNoCull", "entityCutoutNoCullZOffset", "entityTranslucent", "entityTranslucentEmissive", "entitySmoothCutout", "entityDecal", "entityNoOutline", "eyes", "breezeEyes", "breezeWind", "energySwirl"}, at = @org.spongepowered.asm.mixin.injection.At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static net.minecraft.resources.Identifier fbactors$materialLook(net.minecraft.resources.Identifier texture) {
        if (pdx.dev.fbactors.actor.BoatTextures.active())
            texture = pdx.dev.fbactors.compat.Compat.boatTextureId(pdx.dev.fbactors.actor.BoatTextures.texture(texture.toString()));
        return pdx.dev.fbactors.filters.ActorLookBridge.apply(texture);
    }
}
