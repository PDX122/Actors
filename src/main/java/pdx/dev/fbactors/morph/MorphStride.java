package pdx.dev.fbactors.morph;

public final class MorphStride {
    public record Frame(float previousSpeed, float speed, float position) {}
    private boolean seeded;
    private double x, y, z;
    private float speed, position;
    public Frame sample(double nextX, double nextY, double nextZ, boolean airborne) {
        if (!Double.isFinite(nextX) || !Double.isFinite(nextY) || !Double.isFinite(nextZ)) {
            seeded = false;
            speed = position = 0;
            return new Frame(0, 0, 0);
        }
        double dx = nextX - x, dz = nextZ - z, dy = airborne ? nextY - y : 0;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        x = nextX; y = nextY; z = nextZ;
        if (!seeded || distance > 2) {
            seeded = true;
            speed = position = 0;
            return new Frame(0, 0, 0);
        }
        float target = distance < 0.012 ? 0 : (float) Math.min(1, distance * 4);
        float previous = speed;
        speed += (target - speed) * (target > speed ? 0.4f : 0.3f);
        if (speed < 0.008f && target == 0) speed = 0;
        position += speed;
        return new Frame(previous, speed, position);
    }
}
