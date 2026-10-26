package pdx.dev.fbactors.take;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public final class CameraTrack {


    public final List<CameraKeyframe> keyframes = new ArrayList<>();

    public CameraTrack() {
    }

    public boolean isEmpty() {
        return this.keyframes.isEmpty();
    }

    public int size() {
        return this.keyframes.size();
    }

    public void clear() {
        this.keyframes.clear();
    }


    public void add(@Nullable CameraKeyframe keyframe) {
        if (keyframe == null) {
            return;
        }
        int index = this.keyframes.size();
        while (index > 0 && this.keyframes.get(index - 1).tick() > keyframe.tick()) {
            index--;
        }
        if (index > 0 && this.keyframes.get(index - 1).tick() == keyframe.tick()) {
            this.keyframes.set(index - 1, keyframe);
        } else {
            this.keyframes.add(index, keyframe);
        }
    }


    public void sort() {
        this.keyframes.sort(java.util.Comparator.comparingInt(CameraKeyframe::tick));
        for (int i = this.keyframes.size() - 2; i >= 0; i--) {
            if (this.keyframes.get(i).tick() == this.keyframes.get(i + 1).tick()) {
                this.keyframes.remove(i);
            }
        }
    }


    public CameraTrack copy() {
        CameraTrack copy = new CameraTrack();
        copy.keyframes.addAll(this.keyframes);
        return copy;
    }


    public void replaceWith(@Nullable CameraTrack other) {
        if (other == this) return;
        this.keyframes.clear();
        if (other != null) {
            this.keyframes.addAll(other.keyframes);
        }
    }


    public int firstTick() {
        return this.keyframes.isEmpty() ? -1 : this.keyframes.get(0).tick();
    }


    public int lastTick() {
        return this.keyframes.isEmpty() ? -1 : this.keyframes.get(this.keyframes.size() - 1).tick();
    }


    public boolean covers(int replayTick) {
        return !this.keyframes.isEmpty()
            && replayTick >= firstTick()
            && replayTick <= lastTick();
    }


    @Nullable
    public CameraKeyframe at(int replayTick) {
        return at((double) replayTick);
    }

    @Nullable
    public CameraKeyframe at(double replayTick) {
        if (!Double.isFinite(replayTick)) return null;
        int count = this.keyframes.size();
        if (count == 0) {
            return null;
        }
        CameraKeyframe first = this.keyframes.get(0);
        if (replayTick <= first.tick()) {
            return first;
        }
        CameraKeyframe last = this.keyframes.get(count - 1);
        if (replayTick >= last.tick()) {
            return last;
        }


        int low = 0;
        int high = count - 1;
        while (high - low > 1) {
            int mid = (low + high) >>> 1;
            if (this.keyframes.get(mid).tick() <= replayTick) {
                low = mid;
            } else {
                high = mid;
            }
        }

        CameraKeyframe a = this.keyframes.get(low);
        CameraKeyframe b = this.keyframes.get(high);
        int span = b.tick() - a.tick();
        float t = span <= 0 ? 1.0f : (float) ((replayTick - a.tick()) / span);
        float eased = ease(b.easing(), t);
        CameraKeyframe blended = CameraKeyframe.lerp(a, b, eased);

        if (b.easing() == CameraKeyframe.EASING_CATMULL) {
            CameraKeyframe p0 = low > 0 ? this.keyframes.get(low - 1) : a;
            CameraKeyframe p3 = high < count - 1 ? this.keyframes.get(high + 1) : b;
            double[] position = boundedCurve(p0, a, b, p3, t);
            blended = new CameraKeyframe(blended.tick(), position[0], position[1], position[2],
                blended.yaw(), blended.pitch(), blended.fov(), blended.shotType(),
                blended.targetActorId(), blended.easing());
        }
        return blended;
    }


    public static float ease(byte easing, float t) {
        float k = Mth.clamp(t, 0.0f, 1.0f);
        if (easing == CameraKeyframe.EASING_LINEAR) {
            return k;
        }
        return k * k * (3.0f - 2.0f * k);
    }


    private static double[] boundedCurve(CameraKeyframe p0, CameraKeyframe p1,
                                       CameraKeyframe p2, CameraKeyframe p3, float t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double[] out = new double[3];
        double[] p0v = {p0.x(), p0.y(), p0.z()};
        double[] p1v = {p1.x(), p1.y(), p1.z()};
        double[] p2v = {p2.x(), p2.y(), p2.z()};
        double[] p3v = {p3.x(), p3.y(), p3.z()};
        double span = p2.tick() - p1.tick();
        for (int i = 0; i < 3; i++) {
            double slope = (p2v[i] - p1v[i]) / span;
            double leftSpan = p1.tick() - p0.tick();
            double rightSpan = p3.tick() - p2.tick();
            double left = leftSpan > 0
                ? tangent((p1v[i] - p0v[i]) / leftSpan, slope, leftSpan, span) : slope;
            double right = rightSpan > 0
                ? tangent(slope, (p3v[i] - p2v[i]) / rightSpan, span, rightSpan) : slope;
            double value = (2 * t3 - 3 * t2 + 1) * p1v[i]
                + (t3 - 2 * t2 + t) * span * left
                + (-2 * t3 + 3 * t2) * p2v[i]
                + (t3 - t2) * span * right;
            out[i] = Mth.clamp(value, Math.min(p1v[i], p2v[i]), Math.max(p1v[i], p2v[i]));
        }
        return out;
    }

    private static double tangent(double left, double right, double leftSpan, double rightSpan) {
        if (left == 0 || right == 0 || Math.signum(left) != Math.signum(right)) return 0;
        double w1 = 2 * rightSpan + leftSpan;
        double w2 = rightSpan + 2 * leftSpan;
        return (w1 + w2) / (w1 / left + w2 / right);
    }
}

