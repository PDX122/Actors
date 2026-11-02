package pdx.dev.fbactors.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(Level.class)
public interface LevelActorTicks {
    @Accessor("blockEntityTickers") List<TickingBlockEntity> fbactors$blockEntityTickers();
    @Accessor("pendingBlockEntityTickers") List<TickingBlockEntity> fbactors$pendingBlockEntityTickers();
}
