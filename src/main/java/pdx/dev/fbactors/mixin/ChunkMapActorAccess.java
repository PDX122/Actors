package pdx.dev.fbactors.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapActorAccess {
    @Invoker("updateChunkTracking")
    void fbactors$updateChunkTracking(ServerPlayer player);
}
