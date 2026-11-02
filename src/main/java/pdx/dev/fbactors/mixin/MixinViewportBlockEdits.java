package pdx.dev.fbactors.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import pdx.dev.fbactors.actor.ActorTerrain;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinViewportBlockEdits {
    @Shadow @Final protected ServerPlayer player;

    @WrapMethod(method = "destroyBlock")
    private boolean fbactors$viewportBreak(BlockPos pos, Operation<Boolean> original) {
        boolean editing = ActorTerrain.begin(player);
        try { return original.call(pos); }
        finally { if (editing) ActorTerrain.end(); }
    }
    @WrapMethod(method = "useItemOn")
    private InteractionResult fbactors$viewportPlace(ServerPlayer player, Level level, ItemStack stack,
            InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
        boolean editing = ActorTerrain.begin(player);
        try { return original.call(player, level, stack, hand, hit); }
        finally { if (editing) ActorTerrain.end(); }
    }
}
