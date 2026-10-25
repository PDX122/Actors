package pdx.dev.fbactors.filters;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LooksDragPreviewTest {
    @Test void clampKeepsPreviewFullyInsideViewport() {
        // Mouse away from edges: box follows the cursor-side offset untouched.
        assertEquals(100f, LooksMenu.DragPreview.clampPos(100f, 0f, 800f, 194f));
        // Near the far edge: clamped so the box end meets the viewport end.
        assertEquals(606f, LooksMenu.DragPreview.clampPos(700f, 0f, 800f, 194f));
        assertEquals(450f, LooksMenu.DragPreview.clampPos(9999f, 0f, 600f, 150f));
        // Near the near edge: clamped to the viewport origin.
        assertEquals(0f, LooksMenu.DragPreview.clampPos(-50f, 0f, 800f, 194f));
        // Offset viewports (windowed / multi-monitor) clamp to their own origin.
        assertEquals(100f, LooksMenu.DragPreview.clampPos(100f, 64f, 800f, 194f));
        assertEquals(64f, LooksMenu.DragPreview.clampPos(0f, 64f, 800f, 194f));
        assertEquals(64f + 800f - 194f, LooksMenu.DragPreview.clampPos(9999f, 64f, 800f, 194f));
    }

    @Test void degenerateViewportHasNoValidPositionSoCallersMustSkip() {
        // Exact fit still draws, pinned at the viewport origin.
        assertTrue(LooksMenu.DragPreview.fits(194f, 150f, 194f, 150f));
        assertTrue(LooksMenu.DragPreview.fits(800f, 600f, 194f, 150f));
        assertFalse(LooksMenu.DragPreview.fits(100f, 600f, 194f, 150f));
        assertFalse(LooksMenu.DragPreview.fits(800f, 100f, 194f, 150f));
        assertFalse(LooksMenu.DragPreview.fits(Float.NaN, 600f, 194f, 150f));
        assertFalse(LooksMenu.DragPreview.fits(800f, Float.NaN, 194f, 150f));
        // Without a fits() guard the clamped position would sit off-viewport.
        assertTrue(LooksMenu.DragPreview.clampPos(50f, 0f, 100f, 194f) < 0f);
    }

    @Test void labelsAreShortAndNeverNull() {
        assertEquals("", LooksMenu.DragPreview.label(null));
        assertEquals("Aurora", LooksMenu.DragPreview.label("Aurora"));
        assertEquals("123456789012345678901234", LooksMenu.DragPreview.label("123456789012345678901234"));
        String label = LooksMenu.DragPreview.label("A Very Long Look Name Indeed");
        assertTrue(label.length() <= LooksMenu.DragPreview.MAX_LABEL, label);
        assertTrue(label.endsWith("..."), label);
    }
}
