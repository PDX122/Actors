package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PreviewRenderQueueTest {
    @Test void previewWorkWaitsUntilMenusHaveBeenSubmitted() {
        var queue = new PreviewRenderQueue();
        var events = new ArrayList<String>();
        Object model = new Object();
        queue.schedule(model, () -> events.add("old pose"));
        queue.schedule(model, () -> events.add("latest pose"));
        assertTrue(events.isEmpty());
        events.add("menus submitted");
        queue.finishFrame();
        queue.finishFrame();
        assertEquals(List.of("menus submitted", "latest pose"), events);
    }
    @Test void evictionCancelsRenderingAndWaitsForGpuCompletion() {
        var queue = new PreviewRenderQueue();
        var complete = new AtomicBoolean();
        var released = new AtomicBoolean();
        Object model = new Object();
        queue.schedule(model, () -> fail("Evicted model must not render"));
        queue.retire(model, () -> {
            if (!complete.get()) return false;
            assertFalse(released.getAndSet(true));
            return true;
        });
        assertFalse(released.get());
        queue.finishFrame();
        assertFalse(released.get());
        complete.set(true);
        queue.finishFrame();
        queue.finishFrame();
        assertTrue(released.get());
    }
}
