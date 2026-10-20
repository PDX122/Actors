package pdx.dev.fbactors.take;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;


public record ActorContainerInteraction(
    int tick,
    int x,
    int y,
    int z,
    String action,
    int slot,
    @Nullable String itemId,
    int count,
    @Nullable String nbt
) {


    public BlockPos pos() {
        return new BlockPos(this.x, this.y, this.z);
    }
}

