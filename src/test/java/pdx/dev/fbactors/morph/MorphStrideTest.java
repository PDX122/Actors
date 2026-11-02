package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MorphStrideTest {
    @Test void startsPreservePreviousSpeedAndContinuousPhase() {
        var stride = new MorphStride();
        assertEquals(0, stride.sample(0, 0, 0, false).speed());
        var first = stride.sample(0.1, 0, 0, false);
        var next = stride.sample(0.2, 0, 0, false);
        assertEquals(0, first.previousSpeed());
        assertEquals(0.16f, first.speed(), 0.0001f);
        assertEquals(first.speed(), next.previousSpeed());
        assertTrue(next.speed() > first.speed() && next.speed() < 0.4f);
        assertEquals(first.position() + next.speed(), next.position(), 0.0001f);
    }
    @Test void stopsEaseDownAndEventuallyHoldThePhaseStill() {
        var stride = new MorphStride();
        stride.sample(0, 0, 0, false);
        var moving = stride.sample(0.2, 0, 0, false);
        var stopping = stride.sample(0.2, 0, 0, false);
        assertTrue(stopping.speed() > 0 && stopping.speed() < moving.speed());
        for (int i = 0; i < 40; i++) stride.sample(0.2, 0, 0, false);
        var stopped = stride.sample(0.2, 0, 0, false);
        assertEquals(0, stopped.speed());
        assertEquals(stopped.position(), stride.sample(0.2, 0, 0, false).position());
    }
    @Test void stationaryPacketJitterDoesNotStartTheLegs() {
        var stride = new MorphStride();
        stride.sample(0, 0, 0, false);
        for (int i = 0; i < 100; i++) assertEquals(0, stride.sample(i % 2 * 0.003, 0, 0, false).position());
    }
    @Test void teleportsResetWithoutOneFrameLunges() {
        var stride = new MorphStride();
        stride.sample(0, 0, 0, false);
        stride.sample(0.1, 0, 0, false);
        var teleported = stride.sample(100, 0, 0, false);
        assertEquals(new MorphStride.Frame(0, 0, 0), teleported);
        assertTrue(stride.sample(100.1, 0, 0, false).speed() < 0.4f);
    }
    @Test void airborneMotionIncludesVerticalDisplacement() {
        var ground = new MorphStride();
        var air = new MorphStride();
        ground.sample(0, 0, 0, false);
        air.sample(0, 0, 0, true);
        assertEquals(0, ground.sample(0, 0.1, 0, false).speed());
        assertTrue(air.sample(0, 0.1, 0, true).speed() > 0);
    }
    @Test void invalidSamplesResetBeforeTheNextValidPosition() {
        var stride = new MorphStride();
        stride.sample(0, 0, 0, false);
        stride.sample(0.2, 0, 0, false);
        assertEquals(new MorphStride.Frame(0, 0, 0), stride.sample(Double.NaN, 0, 0, false));
        assertEquals(0, stride.sample(50, 0, 0, false).speed());
        assertTrue(stride.sample(50.1, 0, 0, false).speed() > 0);
    }
    @Test void turnsAndOtherActorsDoNotReverseOrShareStridePhase() {
        var first = new MorphStride();
        var second = new MorphStride();
        first.sample(0, 0, 0, false);
        second.sample(0, 0, 0, false);
        var forward = first.sample(0.1, 0, 0, false);
        var reverse = first.sample(0, 0, 0, false);
        assertTrue(reverse.position() > forward.position());
        assertEquals(0, second.sample(0, 0, 0, false).position());
    }
}
