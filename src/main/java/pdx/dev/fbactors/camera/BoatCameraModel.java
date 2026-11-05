package pdx.dev.fbactors.camera;

import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.take.ActorVehicleFrame;

public final class BoatCameraModel {
    public record Shot(double x, double y, double z, double yaw, double pitch, double roll, float fov) {
        public boolean valid() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Double.isFinite(yaw) && Double.isFinite(pitch) && Double.isFinite(roll)
                && Float.isFinite(fov) && fov > 0 && fov < 180;
        }
    }
    private BoatCameraModel() {}

    public static Shot battle(double ax, double ay, double az, double bx, double by, double bz,
                              double yaw, double distance, double height, double fov, double aspect) {
        double x = (ax+bx)/2, y = (ay+by)/2+.6, z = (az+bz)/2;
        double radius = Math.sqrt((ax-bx)*(ax-bx)+(ay-by)*(ay-by)+(az-bz)*(az-bz))/2+1.5;
        double vertical = Math.toRadians(Math.max(30, Math.min(110, fov)))/2;
        double halfAngle = Math.min(vertical, Math.atan(Math.tan(vertical)*Math.max(.1, aspect)));
        double fit = radius / Math.sin(halfAngle) * 1.12;
        double back = Math.max(Math.max(.5, distance), Math.sqrt(Math.max(0, fit*fit-height*height)));
        double heading = Math.toRadians(yaw);
        double cx = x+Math.sin(heading)*back, cy = y+height, cz = z-Math.cos(heading)*back;
        return new Shot(cx, cy, cz, yaw, Math.toDegrees(Math.atan2(height, back)), 0, (float)fov);
    }
    public static double angle(double from, double to, double alpha) {
        double delta = ((to - from) % 360 + 540) % 360 - 180;
        return from + delta * alpha;
    }
    public static Shot shot(double x, double y, double z, double yaw, double vx, double vz,
                            double turn, double seconds, int mode, double distance, double height,
                            double side, double ahead, double orbit, double bank, double fov, double speedFov) {

        double heading = mode == 6 && Math.hypot(vx, vz) > .01
            ? Math.atan2(-vx, vz) : Math.toRadians(yaw);
        if (mode == 2) heading += Math.toRadians(seconds * orbit);
        double fx = -Math.sin(heading), fz = Math.cos(heading);
        double sx = Math.cos(heading), sz = Math.sin(heading);
        double backwards = mode == 3 ? -0.65 : mode == 5 ? 0 : Math.max(0.5, distance) * (mode == 4 ? -1 : 1);
        double lateral = mode == 1 ? side : 0;
        double cx = x - fx * backwards + sx * lateral;
        double cy = y + (mode == 5 ? Math.max(1, distance + height) : height);
        double cz = z - fz * backwards + sz * lateral;
        double ax = mode == 3 ? x + fx * 10 : mode == 4 || mode == 5 ? x : x + vx * ahead;
        double az = mode == 3 ? z + fz * 10 : mode == 4 || mode == 5 ? z : z + vz * ahead;
        double dx = ax - cx, dy = y + 0.6 - cy, dz = az - cz;
        double roll = mode == 5 ? 0 : Math.max(-bank, Math.min(bank, -turn * bank / 8));
        return new Shot(cx, cy, cz, mode == 5 ? Math.toDegrees(heading) : Math.toDegrees(Math.atan2(-dx, dz)),
            -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), roll,
            (float)Math.max(30, Math.min(120, fov + Math.hypot(vx, vz) * speedFov)));
    }
    public static Shot blend(Shot a, Shot b, double alpha) {
        return new Shot(a.x+(b.x-a.x)*alpha, a.y+(b.y-a.y)*alpha, a.z+(b.z-a.z)*alpha,
            angle(a.yaw,b.yaw,alpha), a.pitch+(b.pitch-a.pitch)*alpha,
            angle(a.roll,b.roll,alpha), (float)(a.fov+(b.fov-a.fov)*alpha));
    }

    public static double[] blendedVelocity(@Nullable ActorVehicleFrame prev,
                                           ActorVehicleFrame a, ActorVehicleFrame b, double fraction) {
        double vx = b.x() - a.x(), vy = b.y() - a.y(), vz = b.z() - a.z();
        if (linkable(prev, a)) {
            double f = clamp01(fraction);
            double px = a.x() - prev.x(), py = a.y() - prev.y(), pz = a.z() - prev.z();
            return new double[]{px + (vx - px) * f, py + (vy - py) * f, pz + (vz - pz) * f};
        }
        return new double[]{vx, vy, vz};
    }

    public static double blendedTurn(@Nullable ActorVehicleFrame prev,
                                     ActorVehicleFrame a, ActorVehicleFrame b, double fraction) {
        double curr = angle(a.yaw(), b.yaw(), 1) - a.yaw();
        if (linkable(prev, a)) {
            double before = angle(prev.yaw(), a.yaw(), 1) - prev.yaw();
            double f = clamp01(fraction);
            return before + (curr - before) * f;
        }
        return curr;
    }
    private static boolean linkable(@Nullable ActorVehicleFrame prev, ActorVehicleFrame a) {
        return prev != null && prev.uuid().equals(a.uuid()) && prev.tick() + 1 == a.tick();
    }
    private static double clamp01(double v) { return v < 0 ? 0 : Math.min(1, v); }
}
