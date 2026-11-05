package pdx.dev.fbactors.uav;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class UavHud {
    public interface Canvas {
        void rect(float x, float y, float w, float h, int argb);
        void text(String s, float x, float y, float size, int argb);
        float textWidth(String s, float size);
    }
    public enum Sensor { IR("IR"), TV("TV"); public final String label; Sensor(String l){label=l;} }
    public enum Polarity { WHT_HOT("WHT HOT"), BLK_HOT("BLK HOT"); public final String label; Polarity(String l){label=l;} }
    public enum Fov { WIDE("WFOV"), MED("MFOV"), NARROW("NFOV"), VNARROW("VNFOV"); public final String label; Fov(String l){label=l;} }
    public enum Laser { SAFE, ARMED, LASING }
    public static final class State {
        public boolean recording = true;
        public int recSeconds;
        public Sensor sensor = Sensor.IR;
        public Polarity polarity = Polarity.WHT_HOT;
        public Fov fov = Fov.NARROW;
        public int zoom = 32;
        public double azimuthDeg;
        public double elevationDeg;
        public double slantRangeM;
        public boolean rangeLased;
        public double latDeg;
        public double lonDeg;
        public double altitudeFt;
        public Laser laser = Laser.SAFE;
        public boolean tracking;
        public float trackX = 0.5f;
        public float trackY = 0.5f;
        public float bracketHalf = 110f;
        public Instant time = Instant.now();
    }
    private static final int WHITE = 0xFFF4F4F4;
    private static final int RED = 0xFFFF3B30;
    private static final int SHADOW = 0xB0000000;
    private static final float MARGIN = 60f;
    private static final float TEXT = 24f;
    private static final float LINE = 32f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ZULU = DateTimeFormatter.ofPattern("HH:mm:ss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private UavHud() {}
    public static void render(Canvas c, State s, float W, float H, long nowMs) {
        float cx = W / 2f;
        float cy = H / 2f;
        crosshair(c, cx, cy);
        brackets(c, cx, cy, s.bracketHalf);
        if (s.tracking) trackBox(c, s.trackX * W, s.trackY * H);
        float x = MARGIN;
        float y = MARGIN;
        if (s.recording) {
            dot(c, x, y + 4, 16, RED);
            String rec = "REC";
            c.text(rec, x + 28, y, TEXT, WHITE);
            c.text(hms(s.recSeconds), x + 28 + c.textWidth(rec + "  ", TEXT), y, TEXT, WHITE);
        }
        String sensorLine = s.sensor == Sensor.IR ? "IR  " + s.polarity.label : "TV";
        c.text(sensorLine, x, y + LINE, TEXT, WHITE);
        c.text(s.fov.label + "  x" + s.zoom, x, y + LINE * 2, TEXT, WHITE);
        textR(c, DATE.format(s.time), W - MARGIN, y);
        textR(c, ZULU.format(s.time), W - MARGIN, y + LINE);
        float by = H - MARGIN - LINE * 2;
        c.text(String.format(Locale.ROOT, "AZ %05.1f   EL %+.1f", s.azimuthDeg, s.elevationDeg), MARGIN, by, TEXT, WHITE);
        c.text("RNG " + (s.rangeLased ? "L " : "") + range(s.slantRangeM), MARGIN, by + LINE, TEXT, WHITE);
        textR(c, dms(s.latDeg, 'N', 'S') + "  " + dms(s.lonDeg, 'E', 'W'), W - MARGIN, by);
        textR(c, String.format(Locale.ROOT, "ALT %d FT", Math.round(s.altitudeFt)), W - MARGIN, by + LINE);
        if (s.laser == Laser.ARMED) {
            textC(c, "LASER ARM", cx, H - MARGIN - LINE * 2);
        } else if (s.laser == Laser.LASING && (nowMs / 125) % 2 == 0) {
            textC(c, "LASE DES", cx, H - MARGIN - LINE * 2);
        }
    }
    private static void r(Canvas c, float x, float y, float w, float h, int argb) {
        c.rect(x + 1.5f, y + 1.5f, w, h, SHADOW);
        c.rect(x, y, w, h, argb);
    }
    private static void crosshair(Canvas c, float cx, float cy) {
        float gap = 18f;
        float len = 70f;
        float t = 2f;
        r(c, cx - gap - len, cy - t / 2, len, t, WHITE);
        r(c, cx + gap, cy - t / 2, len, t, WHITE);
        r(c, cx - t / 2, cy - gap - len, t, len, WHITE);
        r(c, cx - t / 2, cy + gap, t, len, WHITE);
        r(c, cx - 2, cy - 2, 4, 4, WHITE);
    }
    private static void brackets(Canvas c, float cx, float cy, float half) {
        float arm = 34f;
        float t = 3f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                float px = cx + sx * half;
                float py = cy + sy * half;
                float hx = sx > 0 ? px - arm : px;
                float vy = sy > 0 ? py - arm : py;
                float hy = sy > 0 ? py - t : py;
                float vx = sx > 0 ? px - t : px;
                r(c, hx, hy, arm, t, WHITE);
                r(c, vx, vy, t, arm, WHITE);
            }
        }
    }
    private static void trackBox(Canvas c, float x, float y) {
        float h = 22f;
        float t = 2f;
        r(c, x - h, y - h, h * 2, t, WHITE);
        r(c, x - h, y + h - t, h * 2, t, WHITE);
        r(c, x - h, y - h, t, h * 2, WHITE);
        r(c, x + h - t, y - h, t, h * 2, WHITE);
    }
    private static void dot(Canvas c, float x, float y, float d, int argb) {
        float q = d / 4f;
        c.rect(x + q, y, d - 2 * q, d, argb);
        c.rect(x, y + q, d, d - 2 * q, argb);
        c.rect(x + q / 2, y + q / 2, d - q, d - q, argb);
    }
    private static void textR(Canvas c, String s, float xRight, float y) {
        c.text(s, xRight - c.textWidth(s, TEXT), y, TEXT, WHITE);
    }
    private static void textC(Canvas c, String s, float xCentre, float y) {
        c.text(s, xCentre - c.textWidth(s, TEXT) / 2f, y, TEXT, WHITE);
    }
    static String hms(int sec) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
    }
    static String range(double m) {
        return m >= 1000 ? String.format(Locale.ROOT, "%.2f KM", m / 1000.0) : String.format(Locale.ROOT, "%d M", Math.round(m));
    }
    static String dms(double deg, char pos, char neg) {
        char hemi = deg >= 0 ? pos : neg;
        deg = Math.abs(deg);
        int d = (int) deg;
        double mf = (deg - d) * 60.0;
        int m = (int) mf;
        int sec = (int) Math.round((mf - m) * 60.0);
        if (sec == 60) { sec = 0; m++; }
        if (m == 60) { m = 0; d++; }
        return String.format(Locale.ROOT, "%d\u00B0%02d'%02d\"%c", d, m, sec, hemi);
    }
}
