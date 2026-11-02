package pdx.dev.fbactors.timeline;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;


class TimelineSelectionTest {

    @Test
    void toggleAddsAndRemoves() {
        TimelineSelection selection = new TimelineSelection();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        selection.toggle(a);
        selection.toggle(b);
        assertEquals(2, selection.size());
        assertTrue(selection.contains(a));
        selection.toggle(a);
        assertEquals(1, selection.size());
        assertFalse(selection.contains(a));
    }

    @Test
    void selectClearsOthers() {
        TimelineSelection selection = new TimelineSelection();
        selection.toggle(UUID.randomUUID());
        selection.toggle(UUID.randomUUID());
        UUID only = UUID.randomUUID();
        selection.select(only);
        assertEquals(1, selection.size());
        assertTrue(selection.contains(only));
        selection.clear();
        assertTrue(selection.isEmpty());
    }
}

