package pdx.dev.fbactors.ui;

import java.util.UUID;

public final class ActorColours {

    private ActorColours() {
    }

    public static int of(UUID id) {
        long bits = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        int hue = Math.floorMod((int) (bits >>> 13), 360);
        return hsv(hue / 360.0f, 0.58f, 1.0f, 0xFF);
    }

    public static int pack(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF);
    }

    public static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int scale(int colour, float factor) {
        int r = Math.min(255, Math.round((colour & 0xFF) * factor));
        int g = Math.min(255, Math.round((colour >>> 8 & 0xFF) * factor));
        int b = Math.min(255, Math.round((colour >>> 16 & 0xFF) * factor));
        return pack(r, g, b, colour >>> 24 & 0xFF);
    }

    private static int hsv(float h, float s, float v, int a) {
        float sector = h * 6.0f;
        int index = (int) sector;
        float fraction = sector - index;
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * fraction);
        float t = v * (1.0f - s * (1.0f - fraction));
        return switch (index % 6) {
            case 0 -> pack(byteOf(v), byteOf(t), byteOf(p), a);
            case 1 -> pack(byteOf(q), byteOf(v), byteOf(p), a);
            case 2 -> pack(byteOf(p), byteOf(v), byteOf(t), a);
            case 3 -> pack(byteOf(p), byteOf(q), byteOf(v), a);
            case 4 -> pack(byteOf(t), byteOf(p), byteOf(v), a);
            default -> pack(byteOf(v), byteOf(p), byteOf(q), a);
        };
    }

    private static int byteOf(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }
}

