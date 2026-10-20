package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;


class ActorTakeTest {

    private static ActorFrame frame(double x, double y, double z) {
        return new ActorFrame(x, y, z, 0.0f, 0.0f, 0.0f,
            (byte) Pose.STANDING.ordinal(),
            ActorFrame.packFlags(false, false, true, false, false));
    }

    private static ActorTake take(int startTick, int frames) {
        List<ActorFrame> list = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            list.add(frame(i, 64.0, 0.0));
        }
        return new ActorTake(UUID.randomUUID(), "Test", "minecraft:overworld", startTick,
            list, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    @Test
    void lengthAndEndTick() {
        ActorTake take = take(100, 40);
        assertEquals(40, take.length());
        assertEquals(139, take.endTick());
        assertEquals(100, take.firstTick());
        assertEquals(139, take.lastTick());
    }

    @Test
    void coversTickBoundaries() {
        ActorTake take = take(100, 40);
        assertFalse(take.coversTick(99));
        assertTrue(take.coversTick(100));
        assertTrue(take.coversTick(139));
        assertFalse(take.coversTick(140));
        assertFalse(new ActorTake(UUID.randomUUID(), "Empty", "minecraft:overworld", 0)
            .coversTick(0));
    }

    @Test
    void trimsNarrowCoverage() {
        ActorTake take = take(100, 40);
        take.trimIn = 5;
        take.trimOut = 7;
        assertEquals(105, take.firstTick());
        assertEquals(132, take.lastTick());
        assertFalse(take.coversTick(104));
        assertTrue(take.coversTick(105));
        assertTrue(take.coversTick(132));
        assertFalse(take.coversTick(133));
        take.resetTrim();
        assertTrue(take.coversTick(100));
        assertTrue(take.coversTick(139));
    }

    @Test
    void frameAtClampsBothEnds() {
        ActorTake take = take(100, 40);
        assertEquals(0.0, take.frameAt(50).x());
        assertEquals(0.0, take.frameAt(100).x());
        assertEquals(20.0, take.frameAt(120).x());
        assertEquals(39.0, take.frameAt(10_000).x());
        assertNull(new ActorTake(UUID.randomUUID(), "Empty", "minecraft:overworld", 0)
            .frameAt(0));
    }

    @Test
    void noHitsNoDeathsMeansAlive() {
        assertEquals(-1, take(0, 10).deathTick());
    }

    @Test
    void hitsDeriveDeathAtTwentyDamage() {
        ActorTake take = take(0, 100);
        take.hits.add(new ActorHit(10, 8.0f, 1.0, 0.0, 1.0f, null));
        assertEquals(-1, take.deathTick());
        take.hits.add(new ActorHit(20, 12.0f, 1.0, 0.0, 1.0f, null));
        assertEquals(20, take.deathTick());
    }

    @Test
    void authoredDeathWinsWhenEarlier() {
        ActorTake take = take(0, 100);
        take.hits.add(new ActorHit(50, 25.0f, 1.0, 0.0, 1.0f, null));
        take.deaths.add(new ActorDeath(30, ActorDeath.Kind.LAVA));
        assertEquals(30, take.deathTick());
        assertNotNull(take.deathAt(30));
        assertNull(take.deathAt(31));
    }

    @Test
    void healthIsTwentyMinusHitsClamped() {
        ActorTake take = take(0, 100);
        assertEquals(20.0f, take.healthAt(0));
        take.hits.add(new ActorHit(10, 7.0f, 1.0, 0.0, 1.0f, null));
        assertEquals(20.0f, take.healthAt(9));
        assertEquals(13.0f, take.healthAt(10));
        take.hits.add(new ActorHit(20, 99.0f, 1.0, 0.0, 1.0f, null));
        assertEquals(0.0f, take.healthAt(100));
    }

    @Test
    void recordedTickAddsOffset() {
        ActorTake take = take(50, 10);
        take.offsetTicks = 30;
        assertEquals(80, take.recordedTick(50));
    }

    @Test
    void hitRecordClampsInputs() {
        ActorHit hit = new ActorHit(-5, Float.NaN, 0.0, 0.0, 5.0f, null);
        assertEquals(0, hit.tick());
        assertEquals(0.0f, hit.damage());
        assertEquals(1.0f, hit.attackStrength());
    }

    @Test
    void copyAsPreservesHiddenRanges() {
        ActorTake take = take(0, 100);
        take.hiddenRanges.add(new ActorVisibility(10, 20));
        take.hiddenRanges.add(new ActorVisibility(50, 60));

        ActorTake copy = take.copyAs(UUID.randomUUID(), "Copy");

        assertEquals(2, copy.hiddenRanges.size());
        assertEquals(new ActorVisibility(10, 20), copy.hiddenRanges.get(0));
        assertEquals(new ActorVisibility(50, 60), copy.hiddenRanges.get(1));

        copy.hiddenRanges.clear();
        assertEquals(2, take.hiddenRanges.size());
    }

    @Test
    void copyAsPreservesSpeedAndCamera() {
        ActorTake take = take(0, 100);
        take.setSpeedMultiplier(2.0f);
        take.cameraTrack.add(new CameraKeyframe(10, 0.0, 64.0, 0.0, 0.0f, 0.0f, 70.0f,
            CameraKeyframe.ShotType.WIDE, null, CameraKeyframe.EASING_SMOOTH));

        ActorTake copy = take.copyAs(UUID.randomUUID(), "Copy");

        assertEquals(2.0f, copy.getSpeedMultiplier());
        assertEquals(1, copy.cameraTrack.size());
        assertEquals(10, copy.cameraTrack.keyframes.get(0).tick());
    }

    @Test
    void speakerColorRoundTripsThroughTakeIo(@TempDir Path dir) throws Exception {
        ActorTake take = take(0, 10);
        take.speakerColorArgb = 0xFFFFAA33;

        Path file = dir.resolve("speaker.take");
        TakeIO.write(file, take);
        ActorTake back = TakeIO.read(file);

        assertEquals(0xFFFFAA33, back.speakerColorArgb);

        ActorTake copy = back.copyAs(UUID.randomUUID(), "Copy");
        assertEquals(0xFFFFAA33, copy.speakerColorArgb);
    }
}

