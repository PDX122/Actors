package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoatCameraModelTest {
    private BoatCameraModel.Shot shot(int mode, double time, double turn) {
        return BoatCameraModel.shot(10, 60, 20, 0, 0, 1, turn, time, mode, 6, 2, 3, 3, 30, 8, 80, 8);
    }
    @Test void chaseFacesForwardAndHoldsTheRequestedOffset() {
        var camera = shot(0, 0, 0);
        assertEquals(10, camera.x(), 1.e-8);
        assertEquals(62, camera.y(), 1.e-8);
        assertEquals(14, camera.z(), 1.e-8);
        assertEquals(0, camera.yaw(), 1.e-8);
        assertEquals(88, camera.fov(), 1.e-8);
    }
    @Test void orbitUsesReplayTimeSoASeekProducesTheSameShot() {
        assertEquals(shot(2, 3, 0), shot(2, 3, 0));
        assertEquals(16, shot(2, 3, 0).x(), 1.e-8);
        assertEquals(20, shot(2, 3, 0).z(), 1.e-8);
    }
    @Test void rotationCrossesTheShortArcInsteadOfSpinningAt180() {
        assertEquals(180, BoatCameraModel.angle(179, -179, .5), 1.e-8);
        assertEquals(-180, BoatCameraModel.angle(-179, 179, .5), 1.e-8);
    }
    @Test void frontChaseStaysAheadAndLooksBackAtTheBoat() {
        var camera = shot(4, 0, 0);
        assertEquals(26, camera.z(), 1.e-8);
        assertEquals(180, Math.abs(camera.yaw()), 1.e-8);
    }
    @Test void overheadLooksStraightDownWithoutBanking() {
        var camera = shot(5, 0, 100);
        assertEquals(10, camera.x(), 1.e-8);
        assertEquals(20, camera.z(), 1.e-8);
        assertEquals(68, camera.y(), 1.e-8);
        assertEquals(90, camera.pitch(), 1.e-8);
        assertEquals(0, camera.roll(), 1.e-8);
    }
    @Test void driftChaseFollowsTravelRatherThanBowAndIsStableWhenStopped() {
        var moving = BoatCameraModel.shot(10, 60, 20, 90, 0, 1, 0, 0, 6, 6, 2, 0, 3, 30, 8, 80, 8);
        assertEquals(10, moving.x(), 1.e-8);
        assertEquals(14, moving.z(), 1.e-8);
        var stopped = BoatCameraModel.shot(10, 60, 20, 90, 0, 0, 0, 0, 6, 6, 2, 0, 3, 30, 8, 80, 8);
        assertEquals(16, stopped.x(), 1.e-8);
        assertEquals(20, stopped.z(), 1.e-8);
        assertTrue(Double.isFinite(stopped.yaw()));
    }
    @Test void bankingIsBoundedAndHoodLooksInFrontOfTheBoat() {
        assertEquals(-8, shot(0, 0, 100).roll(), 1.e-8);
        assertEquals(8, shot(0, 0, -100).roll(), 1.e-8);
        assertTrue(shot(3, 0, 0).z() > 20);
        assertEquals(0, shot(3, 0, 0).yaw(), 1.e-8);
    }
    private static final java.util.UUID BOAT = java.util.UUID.randomUUID();
    private static pdx.dev.fbactors.take.ActorVehicleFrame frame(int tick, double x, double yaw) {
        return new pdx.dev.fbactors.take.ActorVehicleFrame(tick, BOAT, "minecraft:oak_boat", x, 63, 0, (float) yaw, 0);
    }
    @Test void segmentVelocityAndTurnAreContinuousAcrossTheTickBoundary() {
        var prev = frame(99, 0, 0);
        var a = frame(100, 2, 10);
        var b = frame(101, 6, 30);
        var c = frame(102, 12, 70);
        double[] end = BoatCameraModel.blendedVelocity(prev, a, b, 1);
        assertArrayEquals(new double[]{4, 0, 0}, end, 1.e-9);
        assertArrayEquals(new double[]{2, 0, 0}, BoatCameraModel.blendedVelocity(prev, a, b, 0), 1.e-9);
        assertArrayEquals(new double[]{3, 0, 0}, BoatCameraModel.blendedVelocity(prev, a, b, 0.5), 1.e-9);

        assertArrayEquals(end, BoatCameraModel.blendedVelocity(a, b, c, 0), 1.e-9);
        assertEquals(BoatCameraModel.blendedTurn(prev, a, b, 1),
            BoatCameraModel.blendedTurn(a, b, c, 0), 1.e-9);
        assertEquals(10, BoatCameraModel.blendedTurn(prev, a, b, 0), 1.e-9);
        assertEquals(20, BoatCameraModel.blendedTurn(prev, a, b, 1), 1.e-9);
    }
    @Test void turnBlendingCrossesTheShortArcInsteadOfSpinning() {
        var prev = frame(99, 0, 179);
        var a = frame(100, 1, -179);
        var b = frame(101, 2, -177);
        assertEquals(2, BoatCameraModel.blendedTurn(prev, a, b, 0), 1.e-9);
        assertEquals(2, BoatCameraModel.blendedTurn(prev, a, b, 1), 1.e-9);
    }
    @Test void missingOrUnrelatedPreviousSampleFallsBackToCurrentDelta() {
        var a = frame(100, 2, 10);
        var b = frame(101, 6, 30);
        assertArrayEquals(new double[]{4, 0, 0}, BoatCameraModel.blendedVelocity(null, a, b, 0.3), 1.e-9);
        assertEquals(20, BoatCameraModel.blendedTurn(null, a, b, 0.3), 1.e-9);
        var otherBoat = new pdx.dev.fbactors.take.ActorVehicleFrame(
            99, java.util.UUID.randomUUID(), "minecraft:oak_boat", 0, 63, 0, 0, 0);
        assertArrayEquals(new double[]{4, 0, 0}, BoatCameraModel.blendedVelocity(otherBoat, a, b, 0), 1.e-9);
        assertEquals(20, BoatCameraModel.blendedTurn(otherBoat, a, b, 0), 1.e-9);
        var gap = frame(98, 0, 0);
        assertArrayEquals(new double[]{4, 0, 0}, BoatCameraModel.blendedVelocity(gap, a, b, 0), 1.e-9);
        assertEquals(20, BoatCameraModel.blendedTurn(gap, a, b, 0), 1.e-9);
    }
}
