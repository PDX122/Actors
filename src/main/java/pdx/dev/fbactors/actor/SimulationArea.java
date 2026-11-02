package pdx.dev.fbactors.actor;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SimulationArea {
    private record Cell(int x, int y, int z) {}
    private static final int MAX_CELLS = 512;
    private final Set<Cell> cells = new LinkedHashSet<>();
    public void touch(int x, int y, int z) {
        var cell = new Cell(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16));
        cells.remove(cell);
        cells.add(cell);
        while (cells.size() > MAX_CELLS) cells.remove(cells.iterator().next());
    }
    public boolean contains(int x, int y, int z) {
        int cx = Math.floorDiv(x, 16), cy = Math.floorDiv(y, 16), cz = Math.floorDiv(z, 16);
        for (var cell : cells) if (Math.abs((long) cx - cell.x) <= 1
            && Math.abs((long) cy - cell.y) <= 1 && Math.abs((long) cz - cell.z) <= 1) return true;
        return false;
    }
    public int size() { return cells.size(); }
}
