package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;


class MorphSelectionTest {

    private static UUID take() {
        return UUID.randomUUID();
    }

    @Test
    void defaultsAreVisiblePlayerSizedWithNameShown() {
        MorphSelection selection = MorphSelection.of(take(), "zombie");
        assertTrue(selection.isMorphed());
        assertEquals(MorphSelection.DEFAULT_SCALE, selection.scale());
        assertEquals(MorphSelection.DEFAULT_ALPHA, selection.alpha());
        assertFalse(selection.hideName());
        assertFalse(selection.isInvisible());
    }

    @Test
    void scaleClampsToDocumentedRange() {
        assertEquals(MorphSelection.MAX_SCALE,
            MorphSelection.of(take(), "zombie").withScale(100.0f).scale());
        assertEquals(MorphSelection.MIN_SCALE,
            MorphSelection.of(take(), "zombie").withScale(0.0f).scale());
        assertEquals(MorphSelection.MIN_SCALE,
            MorphSelection.of(take(), "zombie").withScale(-3.0f).scale());
    }

    @Test
    void nonFiniteScalesFallBackToDefaults() {
        assertEquals(MorphSelection.DEFAULT_SCALE,
            new MorphSelection(take(), "zombie", Float.NaN, 1.0f, false).scale());
        assertEquals(MorphSelection.DEFAULT_ALPHA,
            new MorphSelection(take(), "zombie", 1.0f, Float.NaN, false).alpha());
        assertEquals(MorphSelection.DEFAULT_SCALE,
            new MorphSelection(take(), "zombie", Float.POSITIVE_INFINITY, 1.0f, false).scale());
    }

    @Test
    void alphaClampsToUnitRange() {
        assertEquals(1.0f, MorphSelection.of(take(), "zombie").withAlpha(7.0f).alpha());
        assertEquals(0.0f, MorphSelection.of(take(), "zombie").withAlpha(-1.0f).alpha());
        assertTrue(MorphSelection.of(take(), "zombie").withAlpha(0.0f).isInvisible());
    }

    @Test
    void blankMorphIdMeansNotMorphed() {
        assertFalse(new MorphSelection(take(), "", 1.0f, 1.0f, false).isMorphed());
        assertFalse(new MorphSelection(take(), "   ", 1.0f, 1.0f, false).isMorphed());
        assertFalse(new MorphSelection(take(), null, 1.0f, 1.0f, false).isMorphed());
        assertFalse(MorphSelection.none(take()).isMorphed());
    }

    @Test
    void withMorphKeepsTuning() {
        MorphSelection tuned = new MorphSelection(take(), "slime", 0.5f, 0.8f, true);
        MorphSelection next = tuned.withMorph("wither");
        assertEquals("wither", next.morphId());
        assertEquals(0.5f, next.scale());
        assertEquals(0.8f, next.alpha());
        assertTrue(next.hideName());
    }

    @Test
    void nullTakeIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> MorphSelection.of(null, "zombie"));
    }
}

