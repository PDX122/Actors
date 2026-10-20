package pdx.dev.fbactors.take;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public final class TransformBake {

    private TransformBake() {
    }


    public static void apply(ActorTake take, double dx, double dy, double dz,
                             double yawDegrees, double pivotX, double pivotZ) {
        if (take == null) {
            return;
        }
        boolean translate = dx != 0.0 || dy != 0.0 || dz != 0.0;
        boolean rotate = yawDegrees != 0.0;
        if (!translate && !rotate) {
            return;
        }
        double radians = Math.toRadians(yawDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        List<ActorFrame> frames = new ArrayList<>(take.frames.size());
        for (ActorFrame frame : take.frames) {
            double x = frame.x() + dx;
            double y = frame.y() + dy;
            double z = frame.z() + dz;
            float yRot = frame.yRot();
            float yHeadRot = frame.yHeadRot();
            if (rotate) {
                double ox = frame.x() - pivotX;
                double oz = frame.z() - pivotZ;
                x = pivotX + ox * cos - oz * sin + dx;
                z = pivotZ + ox * sin + oz * cos + dz;
                yRot = wrapYaw(frame.yRot() + (float) yawDegrees);
                yHeadRot = wrapYaw(frame.yHeadRot() + (float) yawDegrees);
            }
            frames.add(new ActorFrame(x, y, z, yRot, frame.xRot(), yHeadRot,
                frame.pose(), frame.flags()));
        }
        take.frames.clear();
        take.frames.addAll(frames);

        List<ActorBlockEdit> blocks = new ArrayList<>(take.blocks.size());
        for (ActorBlockEdit block : take.blocks) {
            blocks.add(new ActorBlockEdit(block.tick(),
                moveInt(block.x(), dx, block.z(), pivotX, pivotZ, cos, sin, rotate, true),
                block.y() + (int) Math.round(dy),
                moveInt(block.z(), dz, block.x(), pivotX, pivotZ, cos, sin, rotate, false),
                block.before(), block.after()));
        }
        take.blocks.clear();
        take.blocks.addAll(blocks);

        List<ActorBlockEntityEdit> blockEntities = new ArrayList<>(take.blockEntities.size());
        for (ActorBlockEntityEdit edit : take.blockEntities) {
            blockEntities.add(new ActorBlockEntityEdit(edit.tick(),
                moveInt(edit.x(), dx, edit.z(), pivotX, pivotZ, cos, sin, rotate, true),
                edit.y() + (int) Math.round(dy),
                moveInt(edit.z(), dz, edit.x(), pivotX, pivotZ, cos, sin, rotate, false),
                edit.type(), edit.nbt()));
        }
        take.blockEntities.clear();
        take.blockEntities.addAll(blockEntities);

        List<ActorContainerInteraction> containers = new ArrayList<>(take.containers.size());
        for (ActorContainerInteraction interaction : take.containers) {
            containers.add(new ActorContainerInteraction(interaction.tick(),
                moveInt(interaction.x(), dx, interaction.z(), pivotX, pivotZ, cos, sin, rotate, true),
                interaction.y() + (int) Math.round(dy),
                moveInt(interaction.z(), dz, interaction.x(), pivotX, pivotZ, cos, sin, rotate, false),
                interaction.action(), interaction.slot(), interaction.itemId(),
                interaction.count(), interaction.nbt()));
        }
        take.containers.clear();
        take.containers.addAll(containers);

        List<ActorMining> mining = new ArrayList<>(take.mining.size());
        for (ActorMining step : take.mining) {
            BlockPos pos = step.pos();
            mining.add(new ActorMining(step.tick(),
                new BlockPos(
                    moveInt(pos.getX(), dx, pos.getZ(), pivotX, pivotZ, cos, sin, rotate, true),
                    pos.getY() + (int) Math.round(dy),
                    moveInt(pos.getZ(), dz, pos.getX(), pivotX, pivotZ, cos, sin, rotate, false)),
                step.stage()));
        }
        take.mining.clear();
        take.mining.addAll(mining);

        List<ActorMiningState> miningStates = new ArrayList<>(take.miningStates.size());
        for (ActorMiningState state : take.miningStates) {
            miningStates.add(new ActorMiningState(state.tick(),
                moveInt(state.x(), dx, state.z(), pivotX, pivotZ, cos, sin, rotate, true),
                state.y() + (int) Math.round(dy),
                moveInt(state.z(), dz, state.x(), pivotX, pivotZ, cos, sin, rotate, false),
                state.stage(), state.swinging()));
        }
        take.miningStates.clear();
        take.miningStates.addAll(miningStates);

        List<ActorSound> sounds = new ArrayList<>(take.sounds.size());
        for (ActorSound cue : take.sounds) {
            double x = cue.x() + dx;
            double y = cue.y() + dy;
            double z = cue.z() + dz;
            if (rotate) {
                double ox = cue.x() - pivotX;
                double oz = cue.z() - pivotZ;
                x = pivotX + ox * cos - oz * sin + dx;
                z = pivotZ + ox * sin + oz * cos + dz;
            }
            sounds.add(new ActorSound(cue.tick(), cue.soundId(), x, y, z,
                cue.volume(), cue.pitch(), cue.source()));
        }
        take.sounds.clear();
        take.sounds.addAll(sounds);

        if (rotate) {
            List<ActorShot> shots = new ArrayList<>(take.shots.size());
            for (ActorShot shot : take.shots) {
                double dirX = shot.dirX() * cos - shot.dirZ() * sin;
                double dirZ = shot.dirX() * sin + shot.dirZ() * cos;
                shots.add(new ActorShot(shot.tick(), dirX, shot.dirY(), dirZ,
                    shot.power(), shot.itemId(), shot.target()));
            }
            take.shots.clear();
            take.shots.addAll(shots);
        }
    }


    public static float wrapYaw(double yaw) {
        double wrapped = (yaw + 180.0) % 360.0;
        if (wrapped < 0.0) {
            wrapped += 360.0;
        }
        return (float) (wrapped - 180.0);
    }


    private static int moveInt(int value, double delta, int other,
                               double pivotValue, double pivotOther,
                               double cos, double sin, boolean rotate, boolean isX) {
        double moved = value + delta;
        if (rotate) {
            double o = value - pivotValue;
            double oo = other - pivotOther;
            double rotated = isX ? o * cos - oo * sin : o * sin + oo * cos;
            moved = (isX ? pivotValue : pivotOther) + rotated + delta;
        }
        long rounded = Math.round(moved);
        if (rounded > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (rounded < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) rounded;
    }


    public static UUID bakeWithUndo(pdx.dev.fbactors.actor.Actor actor, double dx, double dy,
                                    double dz, double yawDegrees, double pivotX, double pivotZ,
                                    UUID replayId) {
        if (actor == null || actor.take == null) {
            return null;
        }
        apply(actor.take, dx, dy, dz, yawDegrees, pivotX, pivotZ);
        pdx.dev.fbactors.undo.UndoStack.pushEdit(
            () -> apply(actor.take, -dx, -dy, -dz, -yawDegrees, pivotX, pivotZ),
            () -> apply(actor.take, dx, dy, dz, yawDegrees, pivotX, pivotZ));
        if (replayId != null) {
            TakeStore.save(replayId, actor.take);
        }
        return actor.take.id;
    }
}
