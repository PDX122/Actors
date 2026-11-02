package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CameraTrackTest {
    private static CameraKeyframe key(int tick, double x, float yaw, byte easing) {
        return new CameraKeyframe(tick, x, 64, 0, yaw, 0, 70,
            CameraKeyframe.ShotType.TRACKING, null, easing);
    }

    @Test void samplesFractionalTicksAndWrapsRotation() {
        var track = new CameraTrack();
        track.add(key(0, 0, 179, CameraKeyframe.EASING_LINEAR));
        track.add(key(10, 10, -179, CameraKeyframe.EASING_LINEAR));
        assertEquals(0.5, track.at(0.5).x(), 0.0001);
        assertEquals(-180, track.at(5).yaw(), 0.001);
        assertNull(track.at(Double.NaN));
        assertNull(track.at(Double.POSITIVE_INFINITY));
    }

    @Test void duplicateKeyReplacesPreviousValue() {
        var track = new CameraTrack();
        track.add(key(10, 1, 0, (byte) 0));
        track.add(key(0, 0, 0, (byte) 0));
        track.add(key(10, 2, 0, (byte) 0));
        assertEquals(2, track.size());
        assertEquals(2, track.at(10).x());
    }

    @Test void sortCoalescesCollapsedKeysKeepingLast() {
        var track = new CameraTrack();
        track.keyframes.add(key(0, 1, 0, (byte) 0));
        track.keyframes.add(key(0, 2, 0, (byte) 0));
        track.sort();
        assertEquals(1, track.size());
        assertEquals(2, track.at(0).x());
    }

    @Test void selfReplacementRetainsKeys() {
        var track = new CameraTrack();
        track.add(key(1, 1, 0, (byte) 0));
        track.replaceWith(track);
        assertEquals(1, track.size());
    }

    @Test void distantNeighboursCannotOvershootSegment() {
        var track = new CameraTrack();
        track.add(key(0, -1000, 0, (byte) 2));
        track.add(key(10, 0, 0, (byte) 2));
        track.add(key(20, 1, 0, (byte) 2));
        track.add(key(30, 1000, 0, (byte) 2));
        for (double tick = 10; tick <= 20; tick += 0.125) {
            var sample = track.at(tick);
            assertTrue(sample.x() >= 0 && sample.x() <= 1, "Overshoot at " + tick);
            assertEquals(64, sample.y());
        }
    }
}
