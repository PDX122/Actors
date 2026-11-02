package pdx.dev.fbactors.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerLevel.class)
public interface ServerLevelActorAccess {
    @Invoker("runBlockEvents") void fbactors$runBlockEvents();
    @Invoker("tickBlock") void fbactors$tickBlock(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block);
    @Invoker("tickFluid") void fbactors$tickFluid(net.minecraft.core.BlockPos pos, net.minecraft.world.level.material.Fluid fluid);
}
