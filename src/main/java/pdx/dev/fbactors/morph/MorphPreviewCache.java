package pdx.dev.fbactors.morph;

import java.util.LinkedHashMap;
import java.util.function.Function;

public final class MorphPreviewCache<T extends AutoCloseable> implements AutoCloseable {
    private final LinkedHashMap<String, T> values = new LinkedHashMap<>(32, 0.75f, true);
    private final int capacity;
    private final int budget;
    private int remaining;

    public MorphPreviewCache(int capacity, int budget) {
        if (capacity < 1 || budget < 1) throw new IllegalArgumentException("Preview limits must be positive");
        this.capacity = capacity;
        this.budget = budget;
    }
    public void beginFrame() { remaining = budget; }

    public boolean contains(String id) { return values.containsKey(id); }
    public T get(String id, Function<String, T> loader) {
        if (values.containsKey(id)) return values.get(id);
        if (remaining == 0) return null;
        remaining--;
        T value = loader.apply(id);

        if (value == null) return null;
        if (values.size() == capacity) {
            var oldest = values.entrySet().iterator();
            release(oldest.next().getValue());
            oldest.remove();
        }
        values.put(id, value);
        return value;
    }
    public void remove(String id) { release(values.remove(id)); }

    private void release(T value) {
        if (value != null) try { value.close(); } catch (Exception | LinkageError ignored) {}
    }
    @Override public void close() {
        values.values().forEach(this::release);
        values.clear();
        remaining = 0;
    }
}
