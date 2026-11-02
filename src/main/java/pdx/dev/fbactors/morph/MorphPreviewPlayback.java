package pdx.dev.fbactors.morph;

public final class MorphPreviewPlayback {
    public record Frame(int stage, float ticks, float localTicks, int startTick) {}
    public static final double STAGE_SECONDS = 3.5;
    public boolean playing = true;
    public boolean cycling = true;
    private double elapsed;
    private double local;
    private int stage;
    public void reset() { elapsed = local = 0; stage = 0; }
    public void select(int index) { stage = Math.max(0, index); local = 0; cycling = false; }
    public Frame advance(double seconds, int count) { return advance(seconds, count, STAGE_SECONDS); }
    public Frame advance(double seconds, java.util.List<String> labels) {
        int index = Math.max(0, Math.min(stage, labels.size() - 1));
        double duration = !labels.isEmpty() && labels.get(index).startsWith("Native: ") ? 12 : STAGE_SECONDS;
        return advance(seconds, labels.size(), duration);
    }
    private Frame advance(double seconds, int count, double duration) {
        if (count <= 0) return new Frame(0, 0, 0, 0);
        stage = Math.min(stage, count - 1);
        if (playing && Double.isFinite(seconds) && seconds > 0) {
            double delta = Math.min(seconds, 0.1);
            elapsed += delta;
            local += delta;
            while (local >= duration) {
                local -= duration;
                if (cycling) stage = (stage + 1) % count;
            }
        }
        return new Frame(stage, (float) (elapsed * 20), (float) (local * 20), (int) Math.floor((elapsed - local) * 20));
    }
}
