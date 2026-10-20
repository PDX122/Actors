package pdx.dev.fbactors.take;

import net.minecraft.core.BlockPos;


public record ActorMining(int tick, BlockPos pos, int stage) {

    public boolean clears() {
        return this.stage < 0;
    }
}
