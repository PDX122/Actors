package pdx.dev.fbactors.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class CompatAdditions {

    private CompatAdditions() {
    }

    public static String itemStackNbt(Level level, ItemStack stack) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    public static ItemStack itemStackFromNbt(Level level, String itemId, int count, String nbt) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    public static boolean hasBlockEntity(Level level, BlockPos pos) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    public static String blockEntityNbt(Level level, BlockPos pos) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    public static void applyBlockEntityNbt(ServerLevel level, BlockPos pos,
                                           String type, String nbt) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    public static Container containerAt(ServerLevel level, BlockPos pos) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    public static String containerNbt(ServerLevel level, BlockPos pos) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    public static void applyContainerNbt(ServerLevel level, BlockPos pos,
                                         String nbt) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    public static void sendContainerOpen(ServerLevel level, BlockPos pos,
                                         int containerId) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    public static void sendContainerClose(ServerLevel level, BlockPos pos) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    public static void sendMiningProgress(ServerLevel level, int breakerId,
                                         BlockPos pos, int progress) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }
}
