package pdx.dev.fbactors.actor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationAreaTest {
    @Test void signalCanPropagateBeyondItsOriginalInteraction() {
        var area = new SimulationArea();
        area.touch(0, 64, 0);
        assertTrue(area.contains(16, 64, 0));
        assertFalse(area.contains(48, 64, 0));
        area.touch(16, 64, 0);
        assertTrue(area.contains(32, 64, 0));
        area.touch(32, 64, 0);
        assertTrue(area.contains(48, 64, 0));
    }
    @Test void negativeCoordinatesAndVerticalBubbleColumnsStayLocal() {
        var area = new SimulationArea();
        area.touch(-1, -59, -1);
        assertTrue(area.contains(-2, -43, -2));
        assertFalse(area.contains(128, -59, -1));
        assertFalse(area.contains(-1, 100, -1));
    }
    @Test void longRunningCircuitsDoNotExpireAfterFiveSeconds() {
        var area = new SimulationArea();
        area.touch(0, 64, 0);
        for (int i = 0; i < 10000; i++) assertTrue(area.contains(1, 64, 0));
    }
    @Test void regionsRemainBoundedAndActiveCellsAreRetained() {
        var area = new SimulationArea();
        for (int i = 0; i < 10000; i++) area.touch(i * 16, 64, 0);
        assertEquals(512, area.size());
        assertFalse(area.contains(0, 64, 0));
        assertTrue(area.contains(9999 * 16, 64, 0));
    }
}
