package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;


class KnockbackReplayTest {

    private static ActorFrame frame(double x) {
        return new ActorFrame(x, 64.0, 0.0, 0.0f, 0.0f, 0.0f,
            (byte) Pose.STANDING.ordinal(),
            ActorFrame.packFlags(false, false, true, false, false));
    }


    private static ActorTake take(int startTick, int frames) {
        List<ActorFrame> list = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            list.add(frame(i));
        }
        return new ActorTake(UUID.randomUUID(), "Test", "minecraft:overworld", startTick,
            list, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    private static ActorHit punch(int takeTick) {
        return new ActorHit(takeTick, 4.0f, 1.0, 0.0, 1.0f, null,
            new Vec3(0.5, 0.1, 0.0), ActorHit.CRIT_NONE);
    }

    private static ActorHit critPunch(int takeTick) {
        return new ActorHit(takeTick, 6.0f, 1.0, 0.0, 1.0f, null,
            new Vec3(0.5, 0.1, 0.0),
            (byte) (ActorHit.CRIT_HIT | ActorHit.CRIT_MAGIC));
    }

    @Test
    void impulseHitFiresOneTickAfterLanding() {


        ActorTake take = take(1000, 200);
        take.recordHit(punch(1050));
        assertNull(take.hitAt(1049));
        assertNotNull(take.hitAt(1050));
        assertNotNull(take.hitAt(1050).impulse());
        assertNull(take.hitAt(1051));
    }

    @Test
    void splitKeepsHitOnSecondHalf() {
        ActorTake take = take(1000, 100);
        take.recordHit(punch(1050));
        take.deaths.add(new ActorDeath(1080, ActorDeath.Kind.LAVA));

        ActorTake[] halves = ClipEditor.splitAt(take, 50);
        ActorTake second = halves[1];


        assertNotNull(second.hitAt(1050), "split dropped the hit");
        assertNotNull(second.hitAt(1050).impulse());
        assertEquals(4.0f, second.hitAt(1050).damage());
        assertNotNull(second.deathAt(1080), "split dropped the death");

        assertNull(halves[0].hitAt(1051));
    }

    @Test
    void deleteRangeDropsHitsInsideAndKeepsTheRestAligned() {
        ActorTake take = take(1000, 100);
        take.recordHit(punch(1030));
        take.recordHit(punch(1070));

        ActorTake cut = ClipEditor.deleteRange(take, 20, 40);

        assertNull(cut.hitAt(1010), "a hit inside the deleted range survived, misplaced");
        assertNull(cut.hitAt(1030), "a hit inside the deleted range survived");
        assertNotNull(cut.hitAt(1050), "a hit after the deleted range was lost");
        assertNotNull(cut.hitAt(1050).impulse());
    }

    @Test
    void trimToKeepsHitsAligned() {
        ActorTake take = take(1000, 100);
        take.recordHit(punch(1050));

        ActorTake trimmed = ClipEditor.trimTo(take, 20, 60);

        assertEquals(1000 + 20, trimmed.startTick);
        assertNotNull(trimmed.hitAt(1050), "trim dropped an enclosed hit");
        assertNotNull(trimmed.hitAt(1050).impulse());
    }

    @Test
    void shotsFireWhenTheirFrameShows() {


        ActorTake take = take(1000, 200);
        take.shots.add(new ActorShot(50, 0.0, 0.0, 1.0, 3.0f, "minecraft:arrow", null));

        assertTrue(take.shotsAt(1050).size() == 1, "arrow never fires at its frame");
        assertTrue(take.shotsAt(1049).isEmpty());
        assertTrue(take.shotsAt(1051).isEmpty());
    }

    @Test
    void legacyHitWithoutImpulseStillFiled() {
        ActorTake take = take(1000, 200);
        take.recordHit(new ActorHit(1050, 4.0f, 1.0, 0.0, 1.0f, null));
        assertNotNull(take.hitAt(1050));
        assertNull(take.hitAt(1050).impulse());
    }

    @Test
    void critFlagsSurviveSplit() {
        ActorTake take = take(1000, 100);
        take.recordHit(critPunch(1050));

        ActorTake[] halves = ClipEditor.splitAt(take, 50);

        ActorHit kept = halves[1].hitAt(1050);
        assertNotNull(kept, "split dropped the crit hit");
        assertTrue(kept.isCritical());
        assertTrue(kept.isMagicCritical());
        assertNotNull(kept.impulse());
    }

    @Test
    void critFlagsSanitized() {
        ActorHit hit = new ActorHit(5, 1.0f, 1.0, 0.0, 1.0f, null, null, (byte) 7);
        assertEquals((byte) (ActorHit.CRIT_HIT | ActorHit.CRIT_MAGIC), hit.crit());
        assertTrue(hit.isCritical());
        assertTrue(hit.isMagicCritical());
    }

    @Test
    void hitsRoundTripThroughTakeIo(@TempDir Path dir) throws Exception {
        ActorTake take = take(1000, 10);
        take.recordHit(punch(1002));
        take.recordHit(critPunch(1005));
        take.recordHit(new ActorHit(1007, 2.0f, 0.0, 1.0, 0.5f, null));

        Path file = dir.resolve("crit.take");
        TakeIO.write(file, take);
        ActorTake back = TakeIO.read(file);

        assertEquals(3, back.hits.size());
        assertEquals(ActorHit.CRIT_NONE, back.hitAt(1002).crit());
        assertNotNull(back.hitAt(1002).impulse());
        ActorHit crit = back.hitAt(1005);
        assertNotNull(crit);
        assertTrue(crit.isCritical());
        assertTrue(crit.isMagicCritical());
        assertEquals(6.0f, crit.damage());
        assertNotNull(crit.impulse());
        assertEquals(0.5f, back.hitAt(1007).attackStrength());
    }

    @Test
    void splitFiresJunctionHitOnce() {
        ActorTake take = take(1000, 100);
        take.recordHit(punch(1050));

        ActorTake[] halves = ClipEditor.splitAt(take, 50);


        assertNotNull(halves[1].hitAt(1050), "split dropped the junction hit");
        assertNull(halves[0].hitAt(1050), "junction hit fires in both halves");

        assertEquals(51, halves[0].frames.size());
        assertEquals(50, halves[1].frames.size());
    }

    @Test
    void speedAndHidesRoundTripThroughTakeIo(@TempDir Path dir) throws Exception {
        ActorTake take = take(1000, 10);
        take.setSpeedMultiplier(2.0f);
        take.hiddenRanges.add(new ActorVisibility(2, 5));

        Path file = dir.resolve("speed.take");
        TakeIO.write(file, take);
        ActorTake back = TakeIO.read(file);

        assertEquals(2.0f, back.getSpeedMultiplier());
        assertEquals(1, back.hiddenRanges.size());
        assertEquals(new ActorVisibility(2, 5), back.hiddenRanges.get(0));
    }

    @Test
    void clipEditsPreserveHidesCameraAndConfig(@TempDir Path dir) throws Exception {
        ActorTake take = take(1000, 100);
        take.hiddenRanges.add(new ActorVisibility(10, 90));
        take.cameraTrack.add(new CameraKeyframe(30, 0.0, 64.0, 0.0, 0.0f, 0.0f, 70.0f,
            CameraKeyframe.ShotType.MEDIUM, null, CameraKeyframe.EASING_SMOOTH));

        ActorTake trimmed = ClipEditor.trimTo(take, 20, 60);

        assertFalse(trimmed.hiddenRanges.isEmpty(), "trim dropped the hide range");
        assertEquals(1, trimmed.cameraTrack.size(), "trim dropped the camera track");
        assertEquals(10, trimmed.cameraTrack.keyframes.get(0).tick(),
            "trim did not retime the camera keyframe");
    }
}

