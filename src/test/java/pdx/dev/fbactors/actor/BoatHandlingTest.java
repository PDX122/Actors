package pdx.dev.fbactors.actor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoatHandlingTest {
    @Test void propulsionDoesNotMultiplyAnExistingGeyserOrCollisionImpulse() {
        assertEquals(2.08, BoatHandling.propulsion(2, 2.04, 2), 1.e-8);
        assertEquals(2, BoatHandling.propulsion(2, 2, 3), 1.e-8);
    }
    @Test void wallReflectsOnlyTheBlockedComponent() {
        assertEquals(-.75, BoatHandling.bounce(1, 0, 1, .2, .75), 1.e-8);
        assertEquals(.8, BoatHandling.bounce(1, .8, 1, 1, .75), 1.e-8);
        assertEquals(.5, BoatHandling.bounce(-1, 0, -1, -.2, .5), 1.e-8);
    }
    @Test void RestitutionCannotAddImpactEnergy() {
        assertEquals(-1, BoatHandling.bounce(1, 0, 2, .2, 4), 1.e-8);
        assertEquals(0, BoatHandling.bounce(1, 0, 1, 0, 0), 1.e-8);
        assertEquals(0, BoatHandling.bounce(1, 0, 0, 0, 1), 1.e-8);
    }
}
