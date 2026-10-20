package pdx.dev.fbactors.emote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public record Emote(
    String id,
    String name,
    boolean loop,
    int durationTicks,
    List<EmoteKeyframe> keyframes
) {


    public Emote {
        List<EmoteKeyframe> sorted = new ArrayList<>(
            keyframes == null ? List.of() : keyframes);
        sorted.sort(Comparator.comparingInt(EmoteKeyframe::tick));
        keyframes = List.copyOf(sorted);

        if (keyframes.isEmpty()) {
            throw new IllegalArgumentException(
                "Emote '" + id + "' has no keyframes; an emote with no keys has no pose");
        }
        if (keyframes.get(0).tick() != 0) {
            throw new IllegalArgumentException(
                "Emote '" + id + "' must have a keyframe at tick 0, but its first is at tick "
                    + keyframes.get(0).tick() + "; pose sampling is only defined from tick 0");
        }
        if (durationTicks <= 0) {


            throw new IllegalArgumentException(
                "Emote '" + id + "' must have a positive duration, not " + durationTicks);
        }
    }


    public int endTick() {
        return this.keyframes.get(this.keyframes.size() - 1).tick();
    }


    public int keyframeCount() {
        return this.keyframes.size();
    }


    public boolean covers(int tick) {
        if (this.loop) {
            return tick >= 0;
        }
        return tick >= 0 && tick <= endTick();
    }


    public EmotePose poseAt(int tick) {
        List<EmoteKeyframe> keys = this.keyframes;
        int local = this.loop ? EmoteMath.wrapTick(tick, this.durationTicks) : clampTick(tick);


        int lo = 0;
        int hi = keys.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (keys.get(mid).tick() <= local) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }

        EmoteKeyframe from = keys.get(lo);
        EmoteKeyframe to = (lo + 1 < keys.size()) ? keys.get(lo + 1) : null;
        if (to == null || to.tick() == from.tick()) {


            return EmotePose.of(from);
        }

        float span = to.tick() - from.tick();
        float raw = (local - from.tick()) / span;
        String mode = from.interpolation();

        if (EmoteKeyframe.CATMULL.equals(mode)) {


            EmoteKeyframe prev = lo > 0 ? keys.get(lo - 1) : from;
            EmoteKeyframe next = (lo + 2 < keys.size()) ? keys.get(lo + 2) : to;
            return catmull(prev, from, to, next, raw);
        }

        return EmotePose.of(from).blend(EmotePose.of(to), EmoteMath.ease(mode, raw));
    }


    private int clampTick(int tick) {
        int end = endTick();
        if (tick < 0) {
            return 0;
        }
        return tick > end ? end : tick;
    }


    private static EmotePose catmull(EmoteKeyframe p0, EmoteKeyframe p1, EmoteKeyframe p2,
            EmoteKeyframe p3, float t) {


        net.minecraft.world.entity.Pose later = EmoteStance.parse(p2.stance());
        net.minecraft.world.entity.Pose stance = later != null ? later : EmoteStance.parse(p1.stance());
        return new EmotePose(
            EmoteMath.catmullRom((float) p0.xOffset(), (float) p1.xOffset(),
                (float) p2.xOffset(), (float) p3.xOffset(), t),
            EmoteMath.catmullRom((float) p0.yOffset(), (float) p1.yOffset(),
                (float) p2.yOffset(), (float) p3.yOffset(), t),
            EmoteMath.catmullRom((float) p0.zOffset(), (float) p1.zOffset(),
                (float) p2.zOffset(), (float) p3.zOffset(), t),

            EmoteMath.catmullRom(p0.bodyYaw(), p1.bodyYaw(), p2.bodyYaw(), p3.bodyYaw(), t),
            EmoteMath.catmullRom(p0.bodyPitch(), p1.bodyPitch(), p2.bodyPitch(), p3.bodyPitch(), t),
            EmoteMath.catmullRom(p0.headYaw(), p1.headYaw(), p2.headYaw(), p3.headYaw(), t),
            EmoteMath.catmullRom(p0.headPitch(), p1.headPitch(), p2.headPitch(), p3.headPitch(), t),

            EmoteMath.catmullRom(p0.leftArmPitch(), p1.leftArmPitch(),
                p2.leftArmPitch(), p3.leftArmPitch(), t),
            EmoteMath.catmullRom(p0.leftArmYaw(), p1.leftArmYaw(),
                p2.leftArmYaw(), p3.leftArmYaw(), t),
            EmoteMath.catmullRom(p0.leftArmRoll(), p1.leftArmRoll(),
                p2.leftArmRoll(), p3.leftArmRoll(), t),

            EmoteMath.catmullRom(p0.rightArmPitch(), p1.rightArmPitch(),
                p2.rightArmPitch(), p3.rightArmPitch(), t),
            EmoteMath.catmullRom(p0.rightArmYaw(), p1.rightArmYaw(),
                p2.rightArmYaw(), p3.rightArmYaw(), t),
            EmoteMath.catmullRom(p0.rightArmRoll(), p1.rightArmRoll(),
                p2.rightArmRoll(), p3.rightArmRoll(), t),

            EmoteMath.catmullRom(p0.leftLegPitch(), p1.leftLegPitch(),
                p2.leftLegPitch(), p3.leftLegPitch(), t),
            EmoteMath.catmullRom(p0.leftLegYaw(), p1.leftLegYaw(),
                p2.leftLegYaw(), p3.leftLegYaw(), t),
            EmoteMath.catmullRom(p0.leftLegRoll(), p1.leftLegRoll(),
                p2.leftLegRoll(), p3.leftLegRoll(), t),

            EmoteMath.catmullRom(p0.rightLegPitch(), p1.rightLegPitch(),
                p2.rightLegPitch(), p3.rightLegPitch(), t),
            EmoteMath.catmullRom(p0.rightLegYaw(), p1.rightLegYaw(),
                p2.rightLegYaw(), p3.rightLegYaw(), t),
            EmoteMath.catmullRom(p0.rightLegRoll(), p1.rightLegRoll(),
                p2.rightLegRoll(), p3.rightLegRoll(), t),
            stance);
    }
}


