package pdx.dev.fbactors.take;

import java.util.UUID;

public record ActorVehicleFrame(int tick, UUID uuid, String type,
                                double x, double y, double z, float yaw, float pitch,
                                double velocityX, double velocityY, double velocityZ,
                                float leftPrevious, float left, float rightPrevious, float right,
                                boolean leftPaddle, boolean rightPaddle) {
    public ActorVehicleFrame(int tick, UUID uuid, String type,
                             double x, double y, double z, float yaw, float pitch) {
        this(tick, uuid, type, x, y, z, yaw, pitch, Double.NaN, Double.NaN, Double.NaN,
            Float.NaN, Float.NaN, Float.NaN, Float.NaN, false, false);
    }
    public ActorVehicleFrame withMotion(double vx, double vy, double vz,
                                         float lp, float l, float rp, float r, boolean activeLeft, boolean activeRight) {
        return new ActorVehicleFrame(tick, uuid, type, x, y, z, yaw, pitch,
            vx, vy, vz, lp, l, rp, r, activeLeft, activeRight);
    }
    public boolean hasMomentum() {
        return Double.isFinite(velocityX) && Double.isFinite(velocityY) && Double.isFinite(velocityZ);
    }

    public float rowing(int side, float partial) {
        if (side != 0 && side != 1) return Float.NaN;
        float previous = side == 0 ? leftPrevious : rightPrevious;
        float current = side == 0 ? left : right;
        if (!Float.isFinite(previous) || !Float.isFinite(current)) return Float.NaN;
        if (!(side == 0 ? leftPaddle : rightPaddle)) return 0;
        return previous + (current - previous) * Math.max(0, Math.min(1, partial));
    }
    public ActorVehicleFrame retimed(int tick) {
        return new ActorVehicleFrame(tick, uuid, type, x, y, z, yaw, pitch,
            velocityX, velocityY, velocityZ, leftPrevious, left, rightPrevious, right, leftPaddle, rightPaddle);
    }
    public ActorVehicleFrame translated(double dx, double dy, double dz) {
        return new ActorVehicleFrame(tick, uuid, type, x + dx, y + dy, z + dz, yaw, pitch,
            velocityX, velocityY, velocityZ, leftPrevious, left, rightPrevious, right, leftPaddle, rightPaddle);
    }
}
