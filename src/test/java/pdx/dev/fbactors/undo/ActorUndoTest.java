package pdx.dev.fbactors.undo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.Pose;
import pdx.dev.fbactors.take.ActorDeath;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorTake;


class ActorUndoTest {

    @BeforeEach
    void clear() {
        UndoStack.clearForTests();
    }

    private static ActorTake take(String name) {
        List<ActorFrame> frames = new ArrayList<>();
        frames.add(new ActorFrame(0, 64, 0, 0, 0, 0,
            (byte) Pose.STANDING.ordinal(), (byte) 0));
        return new ActorTake(UUID.randomUUID(), name, "minecraft:overworld", 0,
            frames, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    @Test
    void renameUndoRedo() {
        ActorTake take = take("Guard");
        String before = take.name;
        take.name = "Captain";
        UndoStack.pushRename(take, before, "Captain");
        assertTrue(UndoStack.undoEdit());
        assertEquals("Guard", take.name);
        assertTrue(UndoStack.redoEdit());
        assertEquals("Captain", take.name);
    }

    @Test
    void offsetUndoRedo() {
        ActorTake take = take("Guard");
        take.offsetTicks = 40;
        UndoStack.pushOffset(take, 0, 40);
        assertTrue(UndoStack.undoEdit());
        assertEquals(0, take.offsetTicks);
        assertTrue(UndoStack.redoEdit());
        assertEquals(40, take.offsetTicks);
    }

    @Test
    void trimUndoRedo() {
        ActorTake take = take("Guard");
        take.trimIn = 3;
        take.trimOut = 4;
        UndoStack.pushTrim(take, 0, 0, 3, 4);
        assertTrue(UndoStack.undoEdit());
        assertEquals(0, take.trimIn);
        assertEquals(0, take.trimOut);
        assertTrue(UndoStack.redoEdit());
        assertEquals(3, take.trimIn);
        assertEquals(4, take.trimOut);
    }

    @Test
    void trimResetUndoRedo() {
        ActorTake take = take("Guard");
        take.trimIn = 3;
        take.trimOut = 4;
        take.resetTrim();
        UndoStack.pushTrim(take, 3, 4, 0, 0);
        assertEquals(0, take.trimIn);
        assertTrue(UndoStack.undoEdit());
        assertEquals(3, take.trimIn);
        assertEquals(4, take.trimOut);
        assertTrue(UndoStack.redoEdit());
        assertEquals(0, take.trimIn);
        assertEquals(0, take.trimOut);
    }

    @Test
    void muteUndoRedo() {
        ActorTake take = take("Guard");
        take.enabled = false;
        UndoStack.pushEnabled(take, true, false);
        assertTrue(UndoStack.undoEdit());
        assertTrue(take.enabled);
        assertTrue(UndoStack.redoEdit());
        assertFalse(take.enabled);
    }

    @Test
    void lockUndoRedo() {
        ActorTake take = take("Guard");
        take.locked = true;
        UndoStack.pushLocked(take, false, true);
        assertTrue(UndoStack.undoEdit());
        assertFalse(take.locked);
        assertTrue(UndoStack.redoEdit());
        assertTrue(take.locked);
    }

    @Test
    void colourUndoRedo() {
        ActorTake take = take("Guard");
        take.colour = 0x00FF00FF;
        UndoStack.pushColour(take, 0, 0x00FF00FF);
        assertTrue(UndoStack.undoEdit());
        assertEquals(0, take.colour);
        assertTrue(UndoStack.redoEdit());
        assertEquals(0x00FF00FF, take.colour);
    }

    @Test
    void speakerColorUndoRedo() {
        ActorTake take = take("Guard");
        take.speakerColorArgb = (int) 0xFFFFAA33L;
        UndoStack.pushSpeakerColor(take, 0, (int) 0xFFFFAA33L);
        assertTrue(UndoStack.undoEdit());
        assertEquals(0, take.speakerColorArgb);
        assertTrue(UndoStack.redoEdit());
        assertEquals((int) 0xFFFFAA33L, take.speakerColorArgb);
    }

    @Test
    void deathCycleUndoRedo() {
        ActorTake take = take("Guard");
        take.deaths.add(new ActorDeath(10, ActorDeath.Kind.TNT));
        ActorDeath before = take.deaths.get(0);
        ActorDeath after = new ActorDeath(10, ActorDeath.Kind.LAVA);
        take.deaths.set(0, after);
        UndoStack.pushDeathSwap(take, 0, before, after);
        assertTrue(UndoStack.undoEdit());
        assertEquals(ActorDeath.Kind.TNT, take.deaths.get(0).kind());
        assertTrue(UndoStack.redoEdit());
        assertEquals(ActorDeath.Kind.LAVA, take.deaths.get(0).kind());
    }

    @Test
    void deathRemoveUndoRedo() {
        ActorTake take = take("Guard");
        ActorDeath marker = new ActorDeath(10, ActorDeath.Kind.TNT);
        take.deaths.add(marker);
        take.deaths.remove(marker);
        UndoStack.pushDeathRemove(take, marker, 0);
        assertTrue(take.deaths.isEmpty());
        assertTrue(UndoStack.undoEdit());
        assertEquals(1, take.deaths.size());
        assertEquals(marker, take.deaths.get(0));
        assertTrue(UndoStack.redoEdit());
        assertTrue(take.deaths.isEmpty());
    }


    private static final class FakeCell implements UndoStack.SoloCell {
        private final UUID id;
        private boolean solo;

        FakeCell(UUID id) {
            this.id = id;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public boolean solo() {
            return solo;
        }

        @Override
        public void setSolo(boolean value) {
            solo = value;
        }
    }

    @Test
    void soloUndoRedoRestoresWholeCast() {
        FakeCell a = new FakeCell(UUID.randomUUID());
        FakeCell b = new FakeCell(UUID.randomUUID());
        List<UndoStack.SoloCell> cast = new ArrayList<>(List.of(a, b));
        UndoStack.pushSolo(cast, a.id(), true);
        for (UndoStack.SoloCell cell : cast) {
            cell.setSolo(cell == a);
        }
        assertTrue(a.solo());
        assertFalse(b.solo());
        assertTrue(UndoStack.undoEdit());
        assertFalse(a.solo());
        assertFalse(b.solo());
        assertTrue(UndoStack.redoEdit());
        assertTrue(a.solo());
        assertFalse(b.solo());
    }

    @Test
    void cutUndoRestoresTake() {
        ActorTake cut = take("Guard");
        UndoStack.pushRemoval(cut);
        assertEquals(cut, UndoStack.undo());
        assertEquals(cut, UndoStack.redo());
    }

    @Test
    void pastedTakeUndoRemovesCopy() {
        ActorTake source = take("Guard");
        List<ActorTake> takes = new ArrayList<>(List.of(source));
        ActorTake added = source.copyAs(UUID.randomUUID(), source.name);
        takes.add(added);
        UndoStack.pushEdit(
            () -> takes.removeIf(t -> t.id.equals(added.id)),
            () -> {
                if (takes.stream().noneMatch(t -> t.id.equals(added.id))) {
                    takes.add(added);
                }
            });
        assertEquals(2, takes.size());
        assertTrue(UndoStack.undoEdit());
        assertEquals(1, takes.size());
        assertTrue(UndoStack.redoEdit());
        assertEquals(2, takes.size());
        assertEquals(added.id, takes.get(1).id);
    }

    @Test
    void rippleUndoRestoresRemovalAndShiftInOrder() {
        ActorTake victim = take("Victim");
        ActorTake survivor = take("Survivor");
        List<ActorTake> takes = new ArrayList<>(List.of(victim, survivor));
        int shiftBefore = survivor.offsetTicks;
        UndoStack.pushRemoval(victim);
        takes.remove(victim);
        survivor.offsetTicks = shiftBefore - 10;
        UndoStack.pushOffset(survivor, shiftBefore, shiftBefore - 10);

        assertTrue(UndoStack.undoEdit());
        assertEquals(shiftBefore, survivor.offsetTicks);
        takes.add(UndoStack.undo());
        assertEquals(2, takes.size());
    }

    @Test
    void noOpPushesRecordNothing() {
        ActorTake take = take("Guard");
        UndoStack.pushOffset(take, 5, 5);
        UndoStack.pushRename(take, "Guard", "Guard");
        UndoStack.pushTrim(take, 1, 2, 1, 2);
        UndoStack.pushEnabled(take, true, true);
        UndoStack.pushLocked(take, false, false);
        UndoStack.pushColour(take, 7, 7);
        UndoStack.pushSpeakerColor(take, 7, 7);
        assertFalse(UndoStack.canUndoEdit());
        FakeCell a = new FakeCell(UUID.randomUUID());
        List<UndoStack.SoloCell> cast = new ArrayList<>(List.of(a));
        UndoStack.pushSolo(cast, a.id(), false);
        assertFalse(UndoStack.canUndoEdit());
    }
}

