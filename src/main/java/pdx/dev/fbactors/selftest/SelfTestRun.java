package pdx.dev.fbactors.selftest;

import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.emote.Emotes;
import pdx.dev.fbactors.emote.EmoteTrack;
import pdx.dev.fbactors.gear.Gear;
import pdx.dev.fbactors.gear.GearOverride;
import pdx.dev.fbactors.gear.GearSlot;
import pdx.dev.fbactors.morph.Morph;
import pdx.dev.fbactors.morph.MorphDefinition;
import pdx.dev.fbactors.morph.MorphRenderState;
import pdx.dev.fbactors.morph.MorphSelection;
import pdx.dev.fbactors.settings.FbSettings;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.TakeStore;

final class SelfTestRun {

    private static final double MAX_GAP = 0.5;

    private final int frames;

    private final UUID replayId;
    private final int startReplayTick;
    private final boolean morphed;
    private final List<Case> cases = new ArrayList<>();

    private int serverTicks;
    private boolean done;
    @Nullable
    private String abortReason;

    private SelfTestRun(UUID replayId, int startReplayTick, boolean morphed, int frames) {
        this.replayId = replayId;
        this.startReplayTick = startReplayTick;
        this.morphed = morphed;
        this.frames = frames;
    }

    @Nullable
    static SelfTestRun begin(ReplayServer replay, ServerPlayer viewer) {
        UUID replayId = SelfTest.replayIdOf(replay);
        if (replayId == null) {
            return null;
        }
        String dimension = viewer.level() instanceof ServerLevel sl
            ? Compat.dimensionId(sl) : "minecraft:overworld";
        double baseX = viewer.getX();
        double baseY = viewer.getY();
        double baseZ = viewer.getZ();

        int start = replay.getReplayTick() + 2;
        boolean morphed = FbSettings.morphEnabled()
            && Morph.registry().byId("zombie") != null
            && Morph.registry().byId("chicken") != null
            && Morph.registry().byId("bat") != null;

        int available = replay.getTotalReplayTicks() - start - SelfTest.TAIL_TICKS - 2;
        if (available < 30) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Self-test needs 30 ticks; replay is at {} of {}",start,replay.getTotalReplayTicks());
            return null;
        }
        int length = Math.min(SelfTest.FRAMES, available);
        SelfTestRun run = new SelfTestRun(replayId, start, morphed, length);
        var level=(ServerLevel)viewer.level();
        var stand=clearSpace(level,baseX,baseY,baseZ);
        var walk=clearSpace(level,baseX+5,baseY,baseZ);
        var fly=clearSpace(level,baseX-5,baseY,baseZ+4);
        if(stand==null || walk==null || fly==null) return null;
        run.addCase("stand", "zombie", standFrames(stand.x,stand.y,stand.z, length), dimension, start);
        run.addCase("walk", "chicken", walkFrames(walk.x,walk.y,walk.z, length), dimension, start);
        run.addCase("fly-pose", "bat", walkFrames(fly.x,fly.y,fly.z, length), dimension,
            start);
        if (run.cases.isEmpty()) {
            return null;
        }
        return run;
    }

    private static net.minecraft.world.phys.Vec3 clearSpace(ServerLevel level,double x,double y,double z) {
        y=Math.max(y,level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,(int)Math.floor(x),(int)Math.floor(z))+1);
        for(int dy=0;dy<=32;dy+=4) for(int radius=0;radius<=24;radius+=4) for(int dx=-radius;dx<=radius;dx+=4) for(int dz=-radius;dz<=radius;dz+=4) {
            var box=new net.minecraft.world.phys.AABB(x+dx-2.5,y+dy,z+dz-.5,x+dx+2.5,y+dy+2,z+dz+.5);
            if(level.noCollision(box)) return new net.minecraft.world.phys.Vec3(x+dx,y+dy,z+dz);
        }
        return null;
    }

    private void addCase(String name, String morphId, List<ActorFrame> frames, String dimension,
                         int start) {
        ActorTake take = new ActorTake(UUID.randomUUID(), "SelfTest " + name, dimension, start,
            frames, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
        Actor actor = FlashbackActors.ACTORS.add(take);
        if (morphed) {
            Morph.store().set(take.id, MorphSelection.of(take.id, morphId));
        }

        if ("stand".equals(name)) {
            Gear.store().set(take.id,
                GearOverride.of(GearSlot.HEAD, "minecraft:diamond_helmet", 1));
        }
        Emotes.trackFor(take.id).add(new EmoteTrack.Clip(start, this.frames, "wave",
            5.0f, 5.0f, false, EmoteTrack.Clip.NORMAL_SPEED));
        Case testCase = new Case(name, morphId, actor);
        if (FlashbackActors.ACTORS.add(take) != actor
            || FlashbackActors.ACTORS.actors().stream().filter(a -> a.take.id.equals(take.id)).count() != 1) {
            testCase.note("loading the same take twice created a duplicate actor");
        }
        cases.add(testCase);
    }

    private static List<ActorFrame> standFrames(double x, double y, double z, int length) {
        List<ActorFrame> frames = new ArrayList<>(length);
        byte flags = ActorFrame.packFlags(false, false, true, false, false);
        for (int i = 0; i < length; i++) {
            frames.add(new ActorFrame(x, y, z, 0.0f, 0.0f, 0.0f,
                (byte) Pose.STANDING.ordinal(), flags));
        }
        return frames;
    }

    private static List<ActorFrame> walkFrames(double x, double y, double z, int length) {
        List<ActorFrame> frames = new ArrayList<>(length);
        byte flags = ActorFrame.packFlags(false, false, true, false, false);
        for (int i = 0; i < length; i++) {

            frames.add(new ActorFrame(x + Math.sin(i * .1) * 1.8, y, z, 90.0f, 0.0f, 90.0f,
                (byte) Pose.STANDING.ordinal(), flags));
        }
        return frames;
    }

    boolean replayMatches(ReplayServer replay) {
        UUID id = SelfTest.replayIdOf(replay);
        return id != null && id.equals(replayId);
    }

    boolean finished() {
        return done;
    }

    void abort(String reason) {
        if (!done) {
            abortReason = reason;
            cleanup();
            done = true;
        }
    }

    String status() {
        return "Self-test running: tick " + serverTicks + "/" + SelfTest.MAX_TICKS + ".";
    }

    void tick(ReplayServer replay) {
        if (done) {
            return;
        }
        serverTicks++;
        if (serverTicks > SelfTest.MAX_TICKS) {
            abort("timed out");
            return;
        }
        int replayTick = replay.getReplayTick();
        boolean allPastTail = true;
        for (Case c : cases) {
            int takeTick = replayTick - c.actor.take.offsetTicks;
            if (takeTick <= c.actor.take.lastTick() + SelfTest.TAIL_TICKS) {
                allPastTail = false;
            }
            sample(c, takeTick);
        }
        if (allPastTail) {
            finish();
        }
    }

    private void sample(Case c, int takeTick) {
        c.sampled++;
        Actor actor = c.actor;
        if (actor.clone() == null) {

            if (takeTick >= actor.take.firstTick() && takeTick <= actor.take.lastTick()) {
                c.note("missing clone at take tick " + takeTick);
            }
            return;
        }
        if (c.clone == null || c.clone != actor.clone()) {
            if (c.clone != null) {
                c.note("respawned at take tick " + takeTick);
                c.respawns++;
            }
            c.clone = actor.clone();
        }
        if (takeTick < actor.take.firstTick() || takeTick > actor.take.lastTick()) {
            return;
        }
        ActorFrame frame = actor.take.frameAt(takeTick);
        if (frame == null) {
            c.note("no frame at covered take tick " + takeTick);
            return;
        }
        double dx = c.clone.getX() - frame.x();
        double dy = c.clone.getY() - frame.y();
        double dz = c.clone.getZ() - frame.z();
        double gap = Math.sqrt(dx * dx + dy * dy + dz * dz);
        c.maxGap = Math.max(c.maxGap, gap);
        if (gap > MAX_GAP && c.firstGapTick < 0) {
            c.firstGapTick = takeTick;
            c.note("body " + fmt(gap) + "m from frame at take tick " + takeTick);
        }
        Pose expected = morphed ? morphPose(c.morphId) : Pose.STANDING;
        if (c.clone.getPose() != expected) {
            c.poseMisses++;
            if (c.poseMisses == 1) {
                c.note("pose " + c.clone.getPose() + ", expected " + expected
                    + " at take tick " + takeTick);
            }
        }
        if (morphed) {
            var info = MorphRenderState.get(c.clone.getUUID());
            if (info == null || !c.morphId.equals(info.morphId())) {
                c.entryMisses++;
                if (c.entryMisses == 1) {
                    c.note("render entry missing at take tick " + takeTick);
                }
            }
            String name = c.clone.getCustomName() == null
                ? null : c.clone.getCustomName().getString();
            MorphDefinition def = Morph.registry().byId(c.morphId);
            if (def != null && !def.displayName().equals(name)) {
                c.nameMisses++;
                if (c.nameMisses == 1) {
                    c.note("name '" + name + "', expected '" + def.displayName()
                        + "' at take tick " + takeTick);
                }
            }
            if (def != null && def.quadruped() && !equipmentCleared(c)) {
                c.gearMisses++;
                if (c.gearMisses == 1) {
                    c.note("quadruped still holding equipment at take tick " + takeTick);
                }
            }
        }

        if ("stand".equals(c.name)) {
            var head = c.clone.getItemBySlot(EquipmentSlot.HEAD);
            if (head.isEmpty() || !"minecraft:diamond_helmet".equals(Compat.itemId(head))) {
                c.helmetMisses++;
                if (c.helmetMisses == 1) {
                    c.note("gear helmet missing at take tick " + takeTick);
                }
            }
        }

        {
            int rel = takeTick - actor.take.startTick;
            if (rel >= 10 && rel < frames - 10
                && Emotes.previewPose(actor.take.id, takeTick).isIdentity()) {
                c.emoteMisses++;
                if (c.emoteMisses == 1) {
                    c.note("emote track not resolving at take tick " + takeTick);
                }
            }
        }
        if (!actor.sim().usesRecordedBaseline(takeTick)) {
            c.diverged++;
            if (c.diverged == 1) {
                c.note("left the recorded path at take tick " + takeTick);
            }
        }
        if (c.clone.deathTime > 0 || c.clone.getHealth() <= 0.0f) {
            c.note("dead or dying at take tick " + takeTick);
        }
    }

    private static Pose morphPose(String morphId) {
        MorphDefinition def = Morph.registry().byId(morphId);
        return def == null ? Pose.STANDING : def.stance().pose();
    }

    private static boolean equipmentCleared(Case c) {
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (!c.clone.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void finish() {
        cleanup();
        done = true;
    }

    private void cleanup() {
        for (Case c : cases) {
            FlashbackActors.ACTORS.remove(c.actor);
            try {
                TakeStore.delete(replayId, c.actor.take.id);
            } catch (Exception ignored) {
            }
            Morph.store().clear(c.actor.take.id);
            Gear.store().clear(c.actor.take.id);
            Emotes.forgetTake(c.actor.take.id);
        }
        Morph.save();
        Gear.save();
        Emotes.save();
    }

    String summary() {
        int failures = 0;
        for (Case c : cases) {
            failures += c.problems.size();
        }
        if (abortReason != null) {
            return "Self-test " + abortReason + " (" + failures + " problem(s) logged).";
        }
        return failures == 0
            ? "Self-test PASS (" + cases.size() + " actors, " + serverTicks + " ticks)."
            : "Self-test FAIL (" + failures + " problem(s)).";
    }

    String report() {
        StringBuilder out = new StringBuilder();
        out.append("fbactors self-test: ").append(summary()).append('\n');
        out.append("replay=").append(replayId)
            .append(" morphs=").append(morphed ? "on" : "off").append('\n');
        for (Case c : cases) {
            out.append("- ").append(c.actor.displayName())
                .append(": sampled=").append(c.sampled)
                .append(" maxGap=").append(fmt(c.maxGap)).append("m")
                .append(" poseMisses=").append(c.poseMisses)
                .append(" entryMisses=").append(c.entryMisses)
                .append(" nameMisses=").append(c.nameMisses)
                .append(" gearMisses=").append(c.gearMisses)
                .append(" helmetMisses=").append(c.helmetMisses)
                .append(" emoteMisses=").append(c.emoteMisses)
                .append(" respawns=").append(c.respawns)
                .append(" divergedTicks=").append(c.diverged).append('\n');
            for (String problem : c.problems) {
                out.append("    ! ").append(problem).append('\n');
            }
        }
        if (abortReason != null) {
            out.append("aborted: ").append(abortReason).append('\n');
        }
        return out.toString();
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class Case {
        final String name;
        final String morphId;
        final Actor actor;
        @Nullable
        net.minecraft.world.entity.LivingEntity clone;
        int sampled;
        double maxGap;
        int firstGapTick = -1;
        int poseMisses;
        int entryMisses;
        int nameMisses;
        int gearMisses;
        int helmetMisses;
        int emoteMisses;
        int respawns;
        int diverged;
        final List<String> problems = new ArrayList<>();
        final Map<String, Boolean> seenProblems = new LinkedHashMap<>();

        Case(String name, String morphId, Actor actor) {
            this.name = name;
            this.morphId = morphId;
            this.actor = actor;
        }

        void note(String problem) {
            if (seenProblems.putIfAbsent(problem, Boolean.TRUE) == null) {
                problems.add(problem);
            }
        }
    }
}
