package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.rendertype.RenderTypes.class)
public abstract class MixinActorLookMaterials {
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = {"armorCutoutNoCull", "armorCutoutNoCullGlint", "armorTrim", "wolfArmorCracks", "entitySolid", "entitySolidGlint", "entitySolidZOffsetForward", "entityCutoutCull", "entityCutout", "entityCutoutZOffset", "entityCutoutDissolve", "entityTranslucentCull", "entityTranslucent", "entityTranslucentEmissive", "eyes", "breezeEyes", "breezeWind", "energySwirl"}, at = @org.spongepowered.asm.mixin.injection.At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static net.minecraft.resources.Identifier fbactors$materialLook(net.minecraft.resources.Identifier texture) {
        return pdx.dev.fbactors.filters.ActorLookBridge.apply(texture);
    }
}
