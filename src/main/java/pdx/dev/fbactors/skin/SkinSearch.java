package pdx.dev.fbactors.skin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class SkinSearch<T> {
    private final Function<SkinId, CompletableFuture<T>> lookup;
    private CompletableFuture<T> pending;
    private SkinId source;
    private T result;
    private String error;

    public SkinSearch(Function<SkinId, CompletableFuture<T>> lookup) { this.lookup = lookup; }

    public void search(String query) {
        try {
            var requested = SkinQuery.parse(query);
            if (pending != null && !pending.isDone() && requested.equals(source)) return;
            clear(); source = requested;
            pending = lookup.apply(source);
        } catch (IllegalArgumentException invalid) { clear(); error = invalid.getMessage(); }
        catch (RuntimeException | LinkageError failure) {
            clear(); error = "Could not start skin lookup. Try again later.";
        }
    }

    public void poll() {
        if (pending == null || !pending.isDone()) return;
        var completed = pending; pending = null;
        try {
            result = completed.join();
            if (result == null) error = "Player or skin not found. Check the name, or try again later.";
        } catch (RuntimeException failure) {
            var cause = failure.getCause() == null ? failure : failure.getCause();
            error = cause instanceof java.util.concurrent.TimeoutException
                ? "Skin lookup timed out. Try again." : "Could not load that skin. Check your connection and try again.";
        }
    }

    public void clear() {
        if (pending != null) pending.cancel(true);
        pending = null; source = null; result = null; error = null;
    }
    public boolean loading() { return pending != null; }
    public SkinId source() { return source; }
    public T result() { return result; }
    public String error() { return error; }
}
