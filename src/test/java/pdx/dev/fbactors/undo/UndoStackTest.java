package pdx.dev.fbactors.undo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.Pose;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorTake;


class UndoStackTest {

    @BeforeEach
    void clear() {
        UndoStack.clearForTests();
    }

    private static ActorTake take() {
        List<ActorFrame> frames = new ArrayList<>();
        frames.add(new ActorFrame(0, 64, 0, 0, 0, 0,
            (byte) Pose.STANDING.ordinal(), (byte) 0));
        return new ActorTake(UUID.randomUUID(), "T", "minecraft:overworld", 0,
            frames, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    @Test
    void removalRoundTrips() {
        ActorTake take = take();
        assertFalse(UndoStack.canUndo());
        UndoStack.pushRemoval(take);
        assertTrue(UndoStack.canUndo());
        assertEquals(take, UndoStack.undo());
        assertTrue(UndoStack.canRedo());
        assertEquals(take, UndoStack.redo());
    }

    @Test
    void editUndoRedoRunsPair() {
        int[] box = {1};
        UndoStack.pushEdit(() -> box[0] = 1, () -> box[0] = 2);
        box[0] = 2;
        assertTrue(UndoStack.canUndoEdit());
        assertTrue(UndoStack.undoEdit());
        assertEquals(1, box[0]);
        assertTrue(UndoStack.redoEdit());
        assertEquals(2, box[0]);
    }

    @Test
    void newEditClearsRedo() {
        int[] box = {0};
        UndoStack.pushEdit(() -> box[0] = 0, () -> box[0] = 1);
        box[0] = 1;
        assertTrue(UndoStack.undoEdit());
        assertTrue(UndoStack.canRedoEdit());
        UndoStack.pushEdit(() -> box[0] = 9, () -> box[0] = 10);
        assertFalse(UndoStack.canRedoEdit());
    }

    @Test
    void editStackIsBounded() {
        for (int i = 0; i < 60; i++) {
            int value = i;
            UndoStack.pushEdit(() -> {
            }, () -> {
            });
        }
        int undone = 0;
        while (UndoStack.undoEdit()) {
            undone++;
        }
        assertEquals(50, undone);
    }
}

