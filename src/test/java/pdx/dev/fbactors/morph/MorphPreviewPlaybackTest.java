package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MorphPreviewPlaybackTest {
    @Test void autoplayVisitsEachStageAndWraps() {
        var clock = new MorphPreviewPlayback();
        for (int i = 0; i < 36; i++) clock.advance(0.1, 3);
        assertEquals(1, clock.advance(0, 3).stage());
        for (int i = 0; i < 35; i++) clock.advance(0.1, 3);
        assertEquals(2, clock.advance(0, 3).stage());
        for (int i = 0; i < 35; i++) clock.advance(0.1, 3);
        assertEquals(0, clock.advance(0, 3).stage());
    }
    @Test void nativeSequencesHaveTimeToCompleteBeforeCycling() {
        var clock = new MorphPreviewPlayback();
        var labels = java.util.List.of("Native: Emerge", "Walk");
        for (int i = 0; i < 80; i++) clock.advance(0.1, labels);
        assertEquals(0, clock.advance(0, labels).stage());
        for (int i = 0; i < 41; i++) clock.advance(0.1, labels);
        assertEquals(1, clock.advance(0, labels).stage());
    }
    @Test void pauseFreezesAnimationAndPoseCycling() {
        var clock = new MorphPreviewPlayback();
        var before = clock.advance(0.1, 4);
        clock.playing = false;
        assertEquals(before, clock.advance(30, 4));
        clock.playing = true;
        assertTrue(clock.advance(0.05, 4).ticks() > before.ticks());
    }
    @Test void manualSelectionLoopsItsOwnAnimationUntilCyclingIsEnabled() {
        var clock = new MorphPreviewPlayback();
        clock.select(2);
        for (int i = 0; i < 80; i++) clock.advance(0.1, 4);
        var frame = clock.advance(0, 4);
        assertEquals(2, frame.stage());
        assertTrue(frame.localTicks() < MorphPreviewPlayback.STAGE_SECONDS * 20);
        assertTrue(frame.startTick() > 0);
        assertFalse(clock.cycling);
    }
    @Test void frameStallsAndInvalidDeltasCannotSkipTheWholeSequence() {
        var clock = new MorphPreviewPlayback();
        var limited = clock.advance(500, 3);
        assertEquals(2, limited.ticks());
        assertEquals(limited, clock.advance(Double.NaN, 3));
        assertEquals(limited, clock.advance(-1, 3));
        clock.select(999);
        assertEquals(2, clock.advance(0, 3).stage());
        assertEquals(0, clock.advance(0, 0).stage());
    }
    @Test void changingMobsResetsTheSequenceAndItsLocalClock() {
        var clock = new MorphPreviewPlayback();
        clock.select(2);
        clock.advance(0.1, 5);
        clock.reset();
        assertEquals(new MorphPreviewPlayback.Frame(0, 0, 0, 0), clock.advance(0, 5));
    }
}
