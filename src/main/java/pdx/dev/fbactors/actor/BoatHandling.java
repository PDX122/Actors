package pdx.dev.fbactors.actor;

public final class BoatHandling {
    private BoatHandling() {}
    public static double propulsion(double before, double after, double multiplier) {
        return before + (after - before) * clamp(multiplier, 0.1, 3);
    }
    public static double bounce(double incoming, double remaining, double requested, double moved, double restitution) {
        if (Math.abs(requested) < 1.e-6 || Math.abs(requested - moved) < 1.e-5) return remaining;
        return -Math.copySign(Math.min(Math.abs(incoming), Math.abs(requested)), requested) * clamp(restitution, 0, 1);
    }
    public static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
