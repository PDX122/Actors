package pdx.dev.fbactors.take;

public final class BoatRenderInterpolation {
    private BoatRenderInterpolation() {}

    public static float fraction(double renderTick, int publishedTick) {

        return (float) Math.max(0, Math.min(1, renderTick - (publishedTick - 1.0)));
    }

    public static float rowing(ActorVehicleFrame previous, ActorVehicleFrame current, int side, float fraction) {
        if (side != 0 && side != 1) return Float.NaN;
        if (previous == null || !previous.uuid().equals(current.uuid()) || previous.tick() + 1 != current.tick())
            return current.rowing(side, fraction);
        float from = previous.rowing(side, 1);
        float to = current.rowing(side, 1);
        if (!Float.isFinite(from) || !Float.isFinite(to)) return current.rowing(side, fraction);
        float delta = to - from;
        boolean wasActive = side == 0 ? previous.leftPaddle() : previous.rightPaddle();
        boolean active = side == 0 ? current.leftPaddle() : current.rightPaddle();

        if (wasActive != active) delta = (float) Math.IEEEremainder(delta, Math.PI * 2);
        return from + delta * Math.max(0, Math.min(1, fraction));
    }
}
