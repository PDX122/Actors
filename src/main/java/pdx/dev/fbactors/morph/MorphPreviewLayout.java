package pdx.dev.fbactors.morph;

public final class MorphPreviewLayout {
    private MorphPreviewLayout() {}
    public static int resolution(String id) { return "skin_preview".equals(id) ? 512 : 128; }
    public static int scale(float width,float height,int resolution) { return scale(width,height)*resolution/128; }
    public static int scale(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) return 32;
        return Math.max(1, Math.min(110, (int) (96 / Math.max(height, width * 1.4f))));
    }
}
