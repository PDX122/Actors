package pdx.dev.fbactors.undo;

import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.take.ActorDeath;
import pdx.dev.fbactors.take.ActorTake;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


public final class UndoStack {


    private static final int MAX_ENTRIES = 50;

    private static final Deque<ActorTake> undo = new ArrayDeque<>();
    private static final Deque<ActorTake> redo = new ArrayDeque<>();


    public interface Edit {

        void undo();


        void redo();
    }

    private static final Deque<Edit> editUndo = new ArrayDeque<>();
    private static final Deque<Edit> editRedo = new ArrayDeque<>();

    private UndoStack() {
    }


    public static void pushEdit(Edit edit) {
        if (edit == null) {
            return;
        }
        editUndo.push(edit);
        while (editUndo.size() > MAX_ENTRIES) {
            editUndo.removeLast();
        }
        editRedo.clear();
    }


    public static void pushEdit(Runnable undoAction, Runnable redoAction) {
        if (undoAction == null || redoAction == null) {
            return;
        }
        pushEdit(new Edit() {
            @Override
            public void undo() {
                undoAction.run();
            }

            @Override
            public void redo() {
                redoAction.run();
            }
        });
    }


    public static boolean undoEdit() {
        Edit edit = editUndo.poll();
        if (edit == null) {
            return false;
        }
        edit.undo();
        editRedo.push(edit);
        return true;
    }


    public static boolean redoEdit() {
        Edit edit = editRedo.poll();
        if (edit == null) {
            return false;
        }
        edit.redo();
        editUndo.push(edit);
        return true;
    }


    public static boolean canUndoEdit() {
        return !editUndo.isEmpty();
    }


    public static boolean canRedoEdit() {
        return !editRedo.isEmpty();
    }


    public static void pushRename(ActorTake take, String before, String after) {
        if (take == null || Objects.equals(before, after)) {
            return;
        }
        pushEdit(() -> take.name = before, () -> take.name = after);
    }


    public static void pushOffset(ActorTake take, int before, int after) {
        if (take == null || before == after) {
            return;
        }
        pushEdit(() -> take.offsetTicks = before, () -> take.offsetTicks = after);
    }


    public static void pushTrim(ActorTake take, int beforeIn, int beforeOut,
                                int afterIn, int afterOut) {
        if (take == null || (beforeIn == afterIn && beforeOut == afterOut)) {
            return;
        }
        pushEdit(() -> {
            take.trimIn = beforeIn;
            take.trimOut = beforeOut;
        }, () -> {
            take.trimIn = afterIn;
            take.trimOut = afterOut;
        });
    }


    public static void pushEnabled(ActorTake take, boolean before, boolean after) {
        if (take == null || before == after) {
            return;
        }
        pushEdit(() -> take.enabled = before, () -> take.enabled = after);
    }


    public static void pushLocked(ActorTake take, boolean before, boolean after) {
        if (take == null || before == after) {
            return;
        }
        pushEdit(() -> take.locked = before, () -> take.locked = after);
    }


    public static void pushColour(ActorTake take, int before, int after) {
        if (take == null || before == after) {
            return;
        }
        pushEdit(() -> take.colour = before, () -> take.colour = after);
    }


    public static void pushSpeakerColor(ActorTake take, int before, int after) {
        if (take == null || before == after) {
            return;
        }
        pushEdit(() -> take.speakerColorArgb = before,
            () -> take.speakerColorArgb = after);
    }


    public static void pushDeathSwap(ActorTake take, int index,
                                     ActorDeath before, ActorDeath after) {
        if (take == null || before == null || after == null || before.equals(after)) {
            return;
        }
        pushEdit(() -> {
            if (index >= 0 && index < take.deaths.size()) {
                take.deaths.set(index, before);
            }
        }, () -> {
            if (index >= 0 && index < take.deaths.size()) {
                take.deaths.set(index, after);
            }
        });
    }


    public static void pushDeathRemove(ActorTake take, ActorDeath removed, int index) {
        if (take == null || removed == null) {
            return;
        }
        pushEdit(() -> {
            int at = Math.max(0, Math.min(index, take.deaths.size()));
            take.deaths.add(at, removed);
        }, () -> {
            if (index >= 0 && index < take.deaths.size()
                && take.deaths.get(index).equals(removed)) {
                take.deaths.remove(index);
            } else {
                take.deaths.remove(removed);
            }
        });
    }


    public interface SoloCell {

        UUID id();


        boolean solo();


        void setSolo(boolean value);
    }


    public static List<SoloCell> cellsOf(List<Actor> actors) {
        List<SoloCell> cells = new ArrayList<>();
        if (actors == null) {
            return cells;
        }
        for (Actor actor : actors) {
            if (actor == null || actor.take == null) {
                continue;
            }
            Actor captured = actor;
            cells.add(new SoloCell() {
                @Override
                public UUID id() {
                    return captured.take.id;
                }

                @Override
                public boolean solo() {
                    return captured.solo;
                }

                @Override
                public void setSolo(boolean value) {
                    captured.solo = value;
                }
            });
        }
        return cells;
    }


    public static void pushSolo(List<SoloCell> cells, UUID targetId, boolean after) {
        if (cells == null || cells.isEmpty() || targetId == null) {
            return;
        }
        List<SoloCell> snapshot = new ArrayList<>(cells);
        Map<UUID, Boolean> before = new HashMap<>();
        for (SoloCell cell : snapshot) {
            if (cell == null || cell.id() == null) {
                continue;
            }
            before.put(cell.id(), cell.solo());
        }
        if (before.isEmpty()) {
            return;
        }
        boolean same = true;
        for (SoloCell cell : snapshot) {
            if (cell == null || cell.id() == null) {
                continue;
            }
            boolean want = after && cell.id().equals(targetId);
            if (cell.solo() != want) {
                same = false;
                break;
            }
        }
        if (same) {
            return;
        }
        pushEdit(() -> {
            for (SoloCell cell : snapshot) {
                if (cell == null || cell.id() == null) {
                    continue;
                }
                Boolean was = before.get(cell.id());
                if (was != null) {
                    cell.setSolo(was);
                }
            }
        }, () -> {
            for (SoloCell cell : snapshot) {
                if (cell == null || cell.id() == null) {
                    continue;
                }
                cell.setSolo(after && cell.id().equals(targetId));
            }
        });
    }


    public static void clearForTests() {
        undo.clear();
        redo.clear();
        editUndo.clear();
        editRedo.clear();
    }


    public static void pushRemoval(ActorTake take) {
        undo.push(take);
        while (undo.size() > MAX_ENTRIES) {
            undo.removeLast();
        }
        redo.clear();
    }


    @Nullable
    public static ActorTake undo() {
        ActorTake take = undo.poll();
        if (take == null) {
            return null;
        }
        redo.push(take);
        return take;
    }


    @Nullable
    public static ActorTake redo() {
        ActorTake take = redo.poll();
        if (take == null) {
            return null;
        }
        undo.push(take);
        return take;
    }


    public static boolean canUndo() {
        return !undo.isEmpty();
    }


    public static boolean canRedo() {
        return !redo.isEmpty();
    }
}

