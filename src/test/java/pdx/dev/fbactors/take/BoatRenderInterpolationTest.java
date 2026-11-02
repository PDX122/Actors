package pdx.dev.fbactors.take;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BoatRenderInterpolationTest {
    private final UUID boat = UUID.randomUUID();
    private ActorVehicleFrame pose(int tick, float before, float now, boolean active) {
        return new ActorVehicleFrame(tick, boat, "minecraft:oak_boat", 0, 63, 0, 0, 0)
            .withMotion(0, 0, 0, before, now, before, now, active, active);
    }

    @Test void fractionalReplayClockAdvancesWithinAFrozenEntityTick() {
        var before = pose(99, 0.6f, 1, true);
        var current = pose(100, 1, 1.4f, true);
        assertEquals(1.1f, BoatRenderInterpolation.rowing(before, current, 0,
            BoatRenderInterpolation.fraction(99.25, 100)), 0.00001);
        assertEquals(1.3f, BoatRenderInterpolation.rowing(before, current, 0,
            BoatRenderInterpolation.fraction(99.75, 100)), 0.00001);
    }

    @Test void adjacentTickBoundaryHasNoPoseJump() {
        var a = pose(99, 0.6f, 1, true);
        var b = pose(100, 0.8f, 1.4f, true);
        var c = pose(101, 1.4f, 1.8f, true);
        assertEquals(BoatRenderInterpolation.rowing(a, b, 0, 1),
            BoatRenderInterpolation.rowing(b, c, 0, 0));
    }

    @Test void latePublicationHoldsTheLastPoseInsteadOfFallingBack() {
        assertEquals(1, BoatRenderInterpolation.fraction(101.3, 100));
        assertEquals(0, BoatRenderInterpolation.fraction(97.3, 100));
        assertEquals(0.5f, BoatRenderInterpolation.fraction(99.5, 100));
    }

    @Test void stoppingDoesNotUnwindManyFullRotations() {
        var before = pose(99, 100.0f, 100.4f, true);
        var stopped = pose(100, 0, 0, false);
        float halfway = BoatRenderInterpolation.rowing(before, stopped, 0, 0.5f);
        assertTrue(Math.abs(halfway - before.left()) < Math.PI / 2);
        float end = BoatRenderInterpolation.rowing(before, stopped, 0, 1);
        assertEquals(0, Math.sin(end), 0.00001);
    }

    @Test void seeksAndVehicleChangesDoNotBlendUnrelatedStrokes() {
        var previous = pose(98, 7, 7.4f, true);
        var current = pose(100, 1, 1.4f, true);
        assertEquals(current.rowing(0, 0.5f), BoatRenderInterpolation.rowing(previous, current, 0, 0.5f));
    }
}
