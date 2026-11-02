package pdx.dev.fbactors.actor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackContinuityTest {
    @Test void runningCircuitIsNotPinnedToItsSavedPoweredState() {
        var clock = new PlaybackContinuity();
        assertTrue(clock.restoreAt(0));
        for (int tick = 1; tick < 200; tick++) {
            assertFalse(clock.restoreAt(tick));
            assertFalse(clock.restoreAt(tick));
        }
    }
    @Test void rewindAndSeekRestoreSceneStartingConditions() {
        var clock = new PlaybackContinuity();
        assertTrue(clock.restoreAt(100));
        assertFalse(clock.restoreAt(101));
        assertTrue(clock.restoreAt(20));
        assertTrue(clock.restoreAt(200));
        assertFalse(clock.restoreAt(201));
    }
    @Test void resettingAfterSettingsChangeRestoresOnNextPlayback() {
        var clock = new PlaybackContinuity();
        assertTrue(clock.restoreAt(20));
        assertFalse(clock.restoreAt(20));
        clock.reset();
        assertTrue(clock.restoreAt(20));
    }
}
