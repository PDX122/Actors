package pdx.dev.fbactors.ui;

import java.util.ArrayList;
import java.util.List;
import com.moulberry.flashback.Flashback;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.MotionSmoothing;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.undo.UndoStack;

public final class MotionEdits {
    private MotionEdits() {}

    public static boolean apply(Actor actor, int radius, float strength, MotionSmoothing.Mode mode) {
        if (actor.take.locked || ActorMode.phase() != ActorMode.Phase.IDLE) return false;
        var before = new ArrayList<>(actor.take.frames);
        var after = MotionSmoothing.smooth(actor.take, radius, strength, mode);
        if (before.equals(after)) return false;
        MotionSmoothing.replaceFrames(actor.take, after);
        refresh(actor);
        FlashbackActors.save(actor.take);
        UndoStack.pushEdit(() -> restore(actor, before), () -> restore(actor, after));
        return true;
    }

    private static void restore(Actor actor, List<ActorFrame> frames) {
        var replay = Flashback.getReplayServer();
        if (replay == null) return;
        replay.execute(() -> {
            Actor current = FlashbackActors.ACTORS.byTakeId(actor.take.id);
            if (current == null) return;
            MotionSmoothing.replaceFrames(current.take, frames);
            refresh(current);
            FlashbackActors.save(current.take);
        });
    }

    private static void refresh(Actor actor) {
        int tick = actor.lastAppliedTakeTick();
        var clone = actor.clone();
        if (clone == null || clone.isRemoved() || clone.deathTime > 0 || clone.getVehicle() != null
            || tick == Integer.MIN_VALUE || !actor.sim().usesRecordedBaseline(tick)) return;
        ActorFrame frame = actor.take.frameAt(tick);
        if (frame == null) return;
        Compat.snapTo(clone, frame.x(), frame.y(), frame.z(), frame.yRot(), frame.xRot());
        clone.setYHeadRot(frame.yHeadRot());
        actor.sim().driveRecordedLimbs(clone, tick);
    }
}
