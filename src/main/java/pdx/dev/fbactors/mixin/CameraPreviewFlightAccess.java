package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.world.entity.Entity.class)
public interface CameraPreviewFlightAccess {
    @org.spongepowered.asm.mixin.gen.Invoker("setSharedFlag")
    void fbactors$flightFlag(int flag,boolean value);
}
