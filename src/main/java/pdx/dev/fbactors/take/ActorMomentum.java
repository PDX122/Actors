package pdx.dev.fbactors.take;

public record ActorMomentum(int tick, double x, double y, double z) {
    public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    public ActorMomentum retimed(int tick) { return new ActorMomentum(tick, x, y, z); }
}
