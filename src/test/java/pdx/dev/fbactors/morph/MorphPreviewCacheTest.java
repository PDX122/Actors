package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MorphPreviewCacheTest {
    private static final class Preview implements AutoCloseable {
        int closed;
        @Override public void close() { closed++; }
    }

    @Test void onlyRequestedMobRendersAndRepeatedFramesReuseIt() {
        var rendered = new ArrayList<String>();
        try (var cache = new MorphPreviewCache<Preview>(8, 1)) {
            cache.beginFrame();
            var zombie = cache.get("zombie", id -> { rendered.add(id); return new Preview(); });
            cache.beginFrame();
            assertSame(zombie, cache.get("zombie", id -> fail("Cached model rendered twice")));
            assertEquals(List.of("zombie"), rendered);
        }
    }

    @Test void generationBudgetDoesNotQueueUnrequestedModels() {
        try (var cache = new MorphPreviewCache<Preview>(8, 1)) {
            cache.beginFrame();
            assertNotNull(cache.get("cow", id -> new Preview()));
            assertNull(cache.get("pig", id -> fail("Frame budget exceeded")));
            cache.beginFrame();
            assertNotNull(cache.get("pig", id -> new Preview()));
        }
    }

    @Test void leastRecentlyUsedPreviewIsClosedWhenCapacityIsReached() {
        var cow = new Preview();
        var pig = new Preview();
        var sheep = new Preview();
        try (var cache = new MorphPreviewCache<Preview>(2, 3)) {
            cache.beginFrame();
            cache.get("cow", id -> cow);
            cache.get("pig", id -> pig);
            cache.get("cow", id -> fail("Unexpected rerender"));
            cache.get("sheep", id -> sheep);
            assertEquals(0, cow.closed);
            assertEquals(1, pig.closed);
            assertEquals(0, sheep.closed);
        }
        assertEquals(1, cow.closed);
        assertEquals(1, pig.closed);
        assertEquals(1, sheep.closed);
    }

    @Test void failedLoadsAreRetriedInsteadOfPoisoningPreview() {
        var cache = new MorphPreviewCache<Preview>(8, 1);
        cache.beginFrame();
        assertNull(cache.get("warming-up", id -> null));
        // A null load must not occupy a slot: the next frame retries the
        // loader instead of serving the cached null forever (which locked
        // previews on "Model unavailable" after one bad first frame).
        cache.beginFrame();
        var recovered = new Preview();
        assertSame(recovered, cache.get("warming-up", id -> recovered));
        cache.beginFrame();
        assertSame(recovered, cache.get("warming-up", id -> fail("Loaded model rendered twice")));
        cache.close();
        cache.close();
        assertEquals(1, recovered.closed);
    }

    @Test void framingFitsTallWideAndSmallMobsAndRejectsInvalidDimensions() {
        assertEquals(48, MorphPreviewLayout.scale(0.6f, 2));
        assertTrue(MorphPreviewLayout.scale(16, 4) <= 5);
        assertEquals(6, MorphPreviewLayout.scale(0.6f, 16));
        assertEquals(110, MorphPreviewLayout.scale(0.1f, 0.1f));
        assertEquals(32, MorphPreviewLayout.scale(Float.NaN, 2));
        assertEquals(32, MorphPreviewLayout.scale(1, Float.POSITIVE_INFINITY));
        assertEquals(32, MorphPreviewLayout.scale(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MorphPreviewCache<Preview>(0, 1));
    }
}
