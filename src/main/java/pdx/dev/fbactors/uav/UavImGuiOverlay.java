package pdx.dev.fbactors.uav;

import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;

public final class UavImGuiOverlay {
    private UavImGuiOverlay() {}
    public static void render() {
        UavHud.State s = UavHudState.snapshot();
        if (s == null) return;
        float w = ImGui.getIO().getDisplaySize().x;
        float h = ImGui.getIO().getDisplaySize().y;
        if (!(w > 0 && h > 0)) return;
        ImDrawList draw = ImGui.getBackgroundDrawList();
        if (draw == null) return;
        float cx = w / 2f;
        float cy = h / 2f;
        int white = 0xFFF4F4F4;
        int red = 0xFF303BFF;
        int shadow = 0xB0000000;
        float gap = 16f;
        float len = 52f;
        draw.addLine(cx - gap - len, cy, cx - gap, cy, white, 1.5f);
        draw.addLine(cx + gap, cy, cx + gap + len, cy, white, 1.5f);
        draw.addLine(cx, cy - gap - len, cx, cy - gap, white, 1.5f);
        draw.addLine(cx, cy + gap, cx, cy + gap + len, white, 1.5f);
        draw.addCircle(cx, cy, 7f, white, 24, 1.5f);
        draw.addCircleFilled(cx, cy, 2f, white, 12);
        float viewScale = Math.min(w / 1920f, h / 1080f);
        float half = 110f * viewScale + 40f;
        float arm = 30f;
        draw.addLine(cx - half - arm, cy - half, cx - half, cy - half, white, 2f);
        draw.addLine(cx - half, cy - half - arm, cx - half, cy - half, white, 2f);
        draw.addLine(cx + half, cy - half, cx + half + arm, cy - half, white, 2f);
        draw.addLine(cx + half, cy - half - arm, cx + half, cy - half, white, 2f);
        draw.addLine(cx - half - arm, cy + half, cx - half, cy + half, white, 2f);
        draw.addLine(cx - half, cy + half, cx - half, cy + half + arm, white, 2f);
        draw.addLine(cx + half, cy + half, cx + half + arm, cy + half, white, 2f);
        draw.addLine(cx + half, cy + half, cx + half, cy + half + arm, white, 2f);
        if (s.tracking) {
            float tx = s.trackX * w;
            float ty = s.trackY * h;
            float hh = 24f;
            draw.addRect(tx - hh, ty - hh, tx + hh, ty + hh, white, 0f, 1.5f);
        }
        float margin = 28f;
        float y = margin;
        overlayText(draw, "REC " + UavHud.hms(s.recSeconds), margin, y, red, shadow);
        y += 22f;
        overlayText(draw, s.sensor == UavHud.Sensor.IR ? "IR  " + s.polarity.label : "TV", margin, y, white, shadow);
        y += 22f;
        overlayText(draw, s.fov.label + "  x" + s.zoom, margin, y, white, shadow);
        overlayTextRight(draw, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.ROOT).withZone(java.time.ZoneOffset.UTC).format(s.time), w - margin, margin, white, shadow);
        overlayTextRight(draw, java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss'Z'", java.util.Locale.ROOT).withZone(java.time.ZoneOffset.UTC).format(s.time), w - margin, margin + 22f, white, shadow);
        float by = h - margin - 48f;
        overlayText(draw, String.format(java.util.Locale.ROOT, "AZ %05.1f   EL %+.1f", s.azimuthDeg, s.elevationDeg), margin, by, white, shadow);
        overlayText(draw, "RNG " + (s.rangeLased ? "L " : "") + UavHud.range(s.slantRangeM), margin, by + 22f, white, shadow);
        overlayTextRight(draw, UavHud.dms(s.latDeg, 'N', 'S') + "  " + UavHud.dms(s.lonDeg, 'E', 'W'), w - margin, by, white, shadow);
        overlayTextRight(draw, String.format(java.util.Locale.ROOT, "ALT %d FT", Math.round(s.altitudeFt)), w - margin, by + 22f, white, shadow);
        if (s.laser == UavHud.Laser.ARMED) {
            overlayTextCenter(draw, "LASER ARM", cx, h - margin - 60f, white, shadow);
        } else if (s.laser == UavHud.Laser.LASING && (System.currentTimeMillis() / 125) % 2 == 0) {
            overlayTextCenter(draw, "LASE DES", cx, h - margin - 60f, red, shadow);
        }
    }
    private static void overlayText(ImDrawList draw, String s, float x, float y, int color, int shadow) {
        draw.addText(x + 1, y + 1, shadow, s);
        draw.addText(x, y, color, s);
    }
    private static void overlayTextRight(ImDrawList draw, String s, float xRight, float y, int color, int shadow) {
        float w = ImGui.calcTextSizeX(s);
        overlayText(draw, s, xRight - w, y, color, shadow);
    }
    private static void overlayTextCenter(ImDrawList draw, String s, float xc, float y, int color, int shadow) {
        float w = ImGui.calcTextSizeX(s);
        overlayText(draw, s, xc - w / 2f, y, color, shadow);
    }
}
