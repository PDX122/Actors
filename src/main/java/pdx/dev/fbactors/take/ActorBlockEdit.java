package pdx.dev.fbactors.take;

import net.minecraft.core.BlockPos;

public record ActorBlockEdit(int tick, int x, int y, int z, String before, String after) {

    public BlockPos pos() {
        return new BlockPos(this.x, this.y, this.z);
    }
}

