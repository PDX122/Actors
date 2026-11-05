package pdx.dev.fbactors.uav;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UavHudTest {
    private static final class Recording implements UavHud.Canvas {
        final List<String> texts = new ArrayList<>();
        int rects;
        public void rect(float x, float y, float w, float h, int argb) { rects++; }
        public void text(String s, float x, float y, float size, int argb) { texts.add(s); }
        public float textWidth(String s, float size) { return s.length() * size * 0.6f; }
    }
    @Test void formatsMatchMilitaryConventions() {
        assertEquals("00:14:32", UavHud.hms(872));
        assertEquals("2.41 KM", UavHud.range(2412));
        assertEquals("2.41 KM", UavHud.range(2412.0));
        assertEquals("39\u00B040'00\"N", UavHud.dms(39.6666667, 'N', 'S'));
        assertEquals("104\u00B059'21\"W", UavHud.dms(-104.9891667, 'E', 'W'));
    }
    @Test void renderEmitsSensorAndTargetBlocks() {
        Recording canvas = new Recording();
        UavHud.State state = new UavHud.State();
        state.recSeconds = 872;
        state.azimuthDeg = 11.0;
        state.elevationDeg = -32.5;
        state.slantRangeM = 2412;
        state.rangeLased = true;
        state.latDeg = 39.7392;
        state.lonDeg = -104.9903;
        state.altitudeFt = 20112;
        state.laser = UavHud.Laser.ARMED;
        state.tracking = true;
        UavHud.render(canvas, state, 1920, 1080, 0L);
        assertTrue(canvas.rects > 20);
        assertTrue(canvas.texts.stream().anyMatch(s -> s.equals("REC")));
        assertTrue(canvas.texts.stream().anyMatch(s -> s.equals("00:14:32")));
        assertTrue(canvas.texts.stream().anyMatch(s -> s.startsWith("IR")));
        assertTrue(canvas.texts.stream().anyMatch(s -> s.contains("NFOV")));
        assertTrue(canvas.texts.stream().anyMatch(s -> s.startsWith("AZ")));
        assertTrue(canvas.texts.stream().anyMatch(s -> s.startsWith("RNG L ")));
        assertTrue(canvas.texts.stream().anyMatch(s -> s.startsWith("ALT ")));
        assertTrue(canvas.texts.stream().anyMatch(s -> s.equals("LASER ARM")));
    }
    @Test void laserBlinkAndSafeStateBehave() {
        Recording on = new Recording();
        UavHud.State lasing = new UavHud.State();
        lasing.laser = UavHud.Laser.LASING;
        UavHud.render(on, lasing, 1920, 1080, 0L);
        assertTrue(on.texts.stream().anyMatch(s -> s.equals("LASE DES")));
        Recording off = new Recording();
        UavHud.render(off, lasing, 1920, 1080, 200L);
        assertTrue(off.texts.stream().noneMatch(s -> s.equals("LASE DES")));
        Recording safe = new Recording();
        UavHud.State idle = new UavHud.State();
        idle.recording = false;
        idle.laser = UavHud.Laser.SAFE;
        idle.tracking = false;
        UavHud.render(safe, idle, 1920, 1080, 0L);
        assertTrue(safe.texts.stream().noneMatch(s -> s.equals("REC")));
        assertTrue(safe.texts.stream().noneMatch(s -> s.equals("LASER ARM")));
    }
}
