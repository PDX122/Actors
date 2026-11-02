package pdx.dev.fbactors.record;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldActionCaptureTest {
    @Test void followsNeighborUpdatesBeyondThePerformersReach() {
        var capture = new WorldActionCapture();
        capture.note(0,64,0,100);
        for (int x=1; x<=40; x++) {
            assertTrue(capture.contains(x,64,0,100+x));
            capture.note(x,64,0,100+x);
        }
        assertFalse(capture.contains(100,64,100,141));
    }
    @Test void dropsExpiredAndScrubbedHistory() {
        var capture = new WorldActionCapture();
        capture.note(2,64,3,100);
        assertTrue(capture.contains(3,64,3,104));
        assertFalse(capture.contains(2,64,3,201));
        capture.note(2,64,3,300);
        assertFalse(capture.contains(2,64,3,200));
    }
    @Test void capsLongRunningCircuitsAndClearsBetweenTakes() {
        var capture = new WorldActionCapture();
        for (int x=0;x<600;x++) capture.note(x,0,0,0);
        assertFalse(capture.contains(0,0,0,0));
        assertTrue(capture.contains(599,0,0,0));
        capture.clear();
        assertFalse(capture.contains(599,0,0,0));
    }
}
