package pdx.dev.fbactors.actor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoatTextureVisibilityTest {
    @Test void transparentServerHullNeedsFallback() {
        assertTrue(BoatTextureVisibility.fullyTransparent(1, 1, (x, y) -> 0));
        assertTrue(BoatTextureVisibility.fullyTransparent(128, 64, (x, y) -> 0));
    }
    @Test void visibleAndPartlyTransparentPacksArePreserved() {
        assertFalse(BoatTextureVisibility.fullyTransparent(128, 64, (x, y) -> 255));
        assertFalse(BoatTextureVisibility.fullyTransparent(128, 64, (x, y) -> x == 127 && y == 63 ? 1 : 0));
    }
    @Test void emptyImagesAreNotEvidenceOfAnInvisibleHull() {
        assertFalse(BoatTextureVisibility.fullyTransparent(0, 64, (x, y) -> 0));
        assertFalse(BoatTextureVisibility.fullyTransparent(64, 0, (x, y) -> 0));
    }
}
