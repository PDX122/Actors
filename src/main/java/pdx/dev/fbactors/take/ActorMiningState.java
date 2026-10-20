package pdx.dev.fbactors.take;

import net.minecraft.core.BlockPos;


public record ActorMiningState(int tick, int x, int y, int z, int stage, boolean swinging) {


    public BlockPos pos() {
        return new BlockPos(this.x, this.y, this.z);
    }
}

