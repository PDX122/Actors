package pdx.dev.fbactors.take;

import net.minecraft.core.BlockPos;


public record ActorBlockEntityEdit(int tick, int x, int y, int z, String type, String nbt) {


    public BlockPos pos() {
        return new BlockPos(this.x, this.y, this.z);
    }
}

