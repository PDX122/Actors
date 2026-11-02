package pdx.dev.fbactors.actor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TerrainEdits {
    public record Entry(String dimension, int x, int y, int z, String before, String after) {}
    private record Cell(String dimension, int x, int y, int z) {}
    private final Map<Cell, Entry> entries = new LinkedHashMap<>();

    public void record(Entry edit) {
        var key = new Cell(edit.dimension(), edit.x(), edit.y(), edit.z());
        var previous = entries.get(key);
        String original = previous == null ? edit.before() : previous.before();
        if (original.equals(edit.after())) entries.remove(key);
        else entries.put(key, new Entry(edit.dimension(), edit.x(), edit.y(), edit.z(), original, edit.after()));
    }
    public Collection<Entry> entries() { return java.util.List.copyOf(entries.values()); }
    public boolean affects(String dimension, double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ) {
        for (var edit : entries.values()) {
            if (dimension.equals(edit.dimension()) && maxX > edit.x() && minX < edit.x() + 1
                && maxY > edit.y() && minY < edit.y() + 1 && maxZ > edit.z() && minZ < edit.z() + 1) return true;
        }
        return false;
    }
}
