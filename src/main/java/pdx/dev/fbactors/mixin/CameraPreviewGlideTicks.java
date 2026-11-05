package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.world.entity.LivingEntity.class)
public interface CameraPreviewGlideTicks {
    @org.spongepowered.asm.mixin.gen.Accessor("fallFlyTicks")
    void fbactors$glideTicks(int ticks);
}
