package pdx.dev.fbactors.morph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.BooleanSupplier;

public final class PreviewRenderQueue {
    private final LinkedHashMap<Object, Runnable> pending = new LinkedHashMap<>();
    private final ArrayList<BooleanSupplier> retiring = new ArrayList<>();

    public void schedule(Object owner, Runnable render) { pending.put(owner, render); }
    public void retire(Object owner, BooleanSupplier release) {
        pending.remove(owner);
        retiring.add(release);
    }
    public void finishFrame() {
        var work = new ArrayList<>(pending.values());
        pending.clear();
        for (var render : work) render.run();
        retiring.removeIf(BooleanSupplier::getAsBoolean);
    }
}
