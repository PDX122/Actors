package pdx.dev.fbactors.actor;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.take.ActorBlockEdit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlockEdits {

    private static boolean applying = false;

    private BlockEdits() {
    }

    public static void beginApply() {
        applying = true;
    }

    public static void endApply() {
        applying = false;
    }

    public static boolean isApplying() {
        return applying;
    }

    public static String serialize(BlockState state) {
        return BlockStateParser.serialize(state);
    }

    @Nullable
    public static BlockState parse(ServerLevel level, String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return null;
        }
        try {
            return BlockStateParser.parseForBlock(
                level.registryAccess().lookupOrThrow(Registries.BLOCK), serialized, false)
                .blockState();
        } catch (Exception e) {
            return null;
        }
    }


    public static void apply(ServerLevel level, ActorBlockEdit edit, boolean natural,
                             @Nullable Entity breaker,
                             @Nullable List<pdx.dev.fbactors.take.ActorSound> cues) {
        BlockState before = level.getBlockState(edit.pos());
        if (serialize(before).equals(edit.after())) {
            return;
        }
        BlockState after = parse(level, edit.after());
        if (after == null) {
            return;
        }

        boolean breaking = !before.isAir() && after.isAir();
        BlockEntity contents = breaking ? level.getBlockEntity(edit.pos()) : null;
        ItemStack tool = breaker instanceof LivingEntity living
            ? living.getItemBySlot(EquipmentSlot.MAINHAND)
            : ItemStack.EMPTY;

        level.setBlock(edit.pos(), after, Block.UPDATE_ALL);

        if (breaking) {

            try {
                level.levelEvent(null, BREAK_EVENT, edit.pos(), Block.getId(before));
            } catch (RuntimeException | LinkageError e) {
                play(level, edit.pos(), before.getSoundType().getBreakSound());
            }


            if (natural) {
                try {
                    Block.dropResources(before, level, edit.pos(), contents, breaker, tool);
                } catch (Exception e) {

                    pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                        "Failed to drop resources for a block an actor broke at {}",
                        edit.pos(), e);
                }
            }
        } else if (natural) {
            playEditSounds(level, edit.pos(), before, after, cues);
        }
    }


    private static final int BREAK_EVENT = 2001;

    private static void playEditSounds(ServerLevel level, BlockPos pos,
                                       BlockState before, BlockState after,
                                       @Nullable List<pdx.dev.fbactors.take.ActorSound> cues) {

        if (cuedNearby(cues, pos)) {
            return;
        }
        boolean wasAir = before.isAir();
        boolean nowAir = after.isAir();

        if (wasAir && !nowAir) {
            play(level, pos, after.getSoundType().getPlaceSound());
        } else if (!wasAir && nowAir) {
            play(level, pos, before.getSoundType().getBreakSound());
        } else if (!wasAir) {
            play(level, pos, before.getSoundType().getBreakSound());
            play(level, pos, after.getSoundType().getPlaceSound());
        }
    }


    private static boolean cuedNearby(
        @Nullable List<pdx.dev.fbactors.take.ActorSound> cues, BlockPos pos) {
        if (cues == null || cues.isEmpty()) {
            return false;
        }
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (pdx.dev.fbactors.take.ActorSound cue : cues) {
            double dx = cue.x() - cx;
            double dy = cue.y() - cy;
            double dz = cue.z() - cz;
            if (dx * dx + dy * dy + dz * dz <= 6.25) {
                return true;
            }
        }
        return false;
    }

    private static void play(ServerLevel level, BlockPos pos, SoundEvent sound) {
        if (sound != null) {
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                sound, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    public static int revert(ServerLevel level, List<ActorBlockEdit> edits) {
        Map<BlockPos, String> firstBefore = new HashMap<>();
        Map<BlockPos, String> lastAfter = new HashMap<>();
        for (ActorBlockEdit edit : edits) {
            BlockPos pos = edit.pos();
            firstBefore.putIfAbsent(pos, edit.before());
            lastAfter.put(pos, edit.after());
        }

        int restored = 0;
        beginApply();
        try {
            for (Map.Entry<BlockPos, String> entry : lastAfter.entrySet()) {
                if (!serialize(level.getBlockState(entry.getKey())).equals(entry.getValue())) {
                    continue;
                }
                BlockState original = parse(level, firstBefore.get(entry.getKey()));
                if (original != null) {
                    level.setBlock(entry.getKey(), original, Block.UPDATE_ALL);
                    restored++;
                }
            }
        } finally {
            endApply();
        }
        return restored;
    }
}

