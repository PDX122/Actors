package pdx.dev.fbactors.skin;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class SkinLookupExecutor {
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(6), task -> {
            var thread = new Thread(task, "fbactors-online-skins");
            thread.setDaemon(true); return thread;
        });
    private SkinLookupExecutor() {}

    static <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        var result = new CompletableFuture<T>();
        try {
            var task = WORKERS.submit(() -> {
                if (result.isDone()) return;
                try { result.complete(supplier.get()); }
                catch (Exception | LinkageError failure) { result.completeExceptionally(failure); }
            });
            result.orTimeout(25, TimeUnit.SECONDS).whenComplete((value, failure) -> {
                if (failure != null) { task.cancel(true); WORKERS.purge(); }
            });
        } catch (RejectedExecutionException busy) {
            result.completeExceptionally(new IllegalStateException("Skin search is busy. Try again shortly."));
        }
        return result;
    }
}
