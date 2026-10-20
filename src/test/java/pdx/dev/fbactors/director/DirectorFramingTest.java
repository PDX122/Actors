package pdx.dev.fbactors.director;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.DirectorConfig;


class DirectorFramingTest {

    private static ActorFrame frame(double x, double y, double z) {
        return new ActorFrame(x, y, z, 0.0f, 0.0f, 0.0f,
            (byte) Pose.STANDING.ordinal(),
            ActorFrame.packFlags(false, false, true, false, false));
    }

    private static ActorTake take(String name, int frames, double x0, double dx) {
        List<ActorFrame> list = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            list.add(frame(x0 + dx * i, 64.0, 0.0));
        }
        return new ActorTake(UUID.randomUUID(), name, "minecraft:overworld", 0,
            list, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    private static DirectorConfig config(ActorTake... takes) {
        List<DirectorConfig.ActorSelection> selections = new ArrayList<>();
        for (ActorTake take : takes) {
            selections.add(new DirectorConfig.ActorSelection(take.id, false, 1.0f));
        }
        return new DirectorConfig(selections, DirectorConfig.DirectorStyle.SPIELBERG,
            false, 0.0f, 40, null, null, null, List.of());
    }

    @Test
    void favorPullsCentroidTowardFavoredTake() {
        ActorTake left = take("Left", 30, 0.0, 0.0);
        ActorTake right = take("Right", 30, 10.0, 0.0);
        DirectorConfig config = config(left, right);

        assertEquals(1.5, DirectorPositioner.pullFavor(left, config, right.id, 2.0), 1e-9);
        assertEquals(3.0, DirectorPositioner.pullFavor(right, config, right.id, 2.0), 1e-9);

        Vec3 plain = DirectorPositioner.centroidOfTakes(List.of(left, right),
            take -> DirectorPositioner.pullFavor(take, config, null, 2.0), 10);
        assertNotNull(plain);
        assertEquals(5.0, plain.x, 1e-9);

        Vec3 favored = DirectorPositioner.centroidOfTakes(List.of(left, right),
            take -> DirectorPositioner.pullFavor(take, config, right.id, 2.0), 10);
        assertNotNull(favored);
        assertEquals(20.0 / 3.0, favored.x, 1e-9);
        assertEquals(plain.y, favored.y, 1e-9);
    }

    @Test
    void movingTakeGetsLeadRoom() {
        ActorTake walker = take("Walker", 30, 0.0, 1.0);
        Vec3 lead = DirectorPositioner.leadOffsetFor(walker, 5);
        assertEquals(2.0, lead.x, 1e-9);
        assertEquals(0.0, lead.y, 1e-9);
        assertEquals(0.0, lead.z, 1e-9);
    }

    @Test
    void stillTakeGetsNoLead() {
        ActorTake stander = take("Stander", 30, 4.0, 0.0);
        assertEquals(Vec3.ZERO, DirectorPositioner.leadOffsetFor(stander, 5));
    }

    @Test
    void thirdsOffsetSitsOffCentre() {
        Vec3 aim = new Vec3(0.0, 65.0, 0.0);
        Vec3 camera = new Vec3(0.0, 65.0, 4.5);
        Vec3 plus = DirectorChoreographer.thirdsOffset(aim, camera,
            CameraKeyframe.ShotType.CLOSE_UP, 1.0f);
        Vec3 minus = DirectorChoreographer.thirdsOffset(aim, camera,
            CameraKeyframe.ShotType.CLOSE_UP, -1.0f);
        assertEquals(4.5 * 0.16, plus.x, 1e-9);
        assertEquals(0.0, plus.y, 1e-9);
        assertEquals(-plus.x, minus.x, 1e-9);
        assertEquals(Vec3.ZERO, DirectorChoreographer.thirdsOffset(aim, camera,
            CameraKeyframe.ShotType.WIDE, 1.0f));
        assertEquals(Vec3.ZERO, DirectorChoreographer.thirdsOffset(aim, camera,
            CameraKeyframe.ShotType.OVER_SHOULDER, 1.0f));
        assertEquals(Vec3.ZERO, DirectorChoreographer.thirdsOffset(aim, camera,
            CameraKeyframe.ShotType.MEDIUM, 0.0f));
    }

    @Test
    void sideFlipsOnlyOnSubjectChange() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(1.0f, DirectorShotSelector.nextSide(null, first, 1.0f), 0.0f);
        assertEquals(1.0f, DirectorShotSelector.nextSide(first, first, 1.0f), 0.0f);
        assertEquals(-1.0f, DirectorShotSelector.nextSide(first, second, 1.0f), 0.0f);
        assertEquals(1.0f, DirectorShotSelector.nextSide(second, first, -1.0f), 0.0f);
    }

    @Test
    void forcedShotTypeWinsWhenPlausible() {

        ActorTake first = take("First", 30, 0.0, 0.0);
        ActorTake second = take("Second", 30, 3.0, 0.0);
        List<ActorTake> takes = List.of(first, second);
        DirectorAnalyzer.Moment combat = new DirectorAnalyzer.Moment(10, first.id,
            DirectorAnalyzer.MomentKind.COMBAT, 1.0f);
        DirectorStyle style = DirectorStyle.of(DirectorConfig.DirectorStyle.SPIELBERG);
        java.util.Random rng = new java.util.Random(1L);

        DirectorConfig forced = new DirectorConfig(
            List.of(new DirectorConfig.ActorSelection(first.id, false, 1.0f),
                new DirectorConfig.ActorSelection(second.id, false, 1.0f)),
            DirectorConfig.DirectorStyle.SPIELBERG, false, 0.0f, 40, null, null,
            CameraKeyframe.ShotType.OVER_SHOULDER, List.of());
        assertEquals(CameraKeyframe.ShotType.OVER_SHOULDER,
            DirectorShotSelector.chooseTakes(takes, combat, style, forced, rng));


        DirectorAnalyzer.Moment death = new DirectorAnalyzer.Moment(10, first.id,
            DirectorAnalyzer.MomentKind.DEATH, 5.0f);
        DirectorConfig forcedWide = new DirectorConfig(
            List.of(new DirectorConfig.ActorSelection(first.id, false, 1.0f),
                new DirectorConfig.ActorSelection(second.id, false, 1.0f)),
            DirectorConfig.DirectorStyle.SPIELBERG, false, 0.0f, 40, null, null,
            CameraKeyframe.ShotType.WIDE, List.of());
        assertEquals(CameraKeyframe.ShotType.CLOSE_UP,
            DirectorShotSelector.chooseTakes(takes, death, style, forcedWide, rng));


        DirectorConfig auto = new DirectorConfig(
            List.of(new DirectorConfig.ActorSelection(first.id, false, 1.0f),
                new DirectorConfig.ActorSelection(second.id, false, 1.0f)),
            DirectorConfig.DirectorStyle.SPIELBERG, false, 0.0f, 40, null, null, null,
            List.of());
        CameraKeyframe.ShotType picked = DirectorShotSelector.chooseTakes(
            takes, combat, style, auto, new java.util.Random(1L));
        assertTrue(picked == CameraKeyframe.ShotType.OVER_SHOULDER
            || picked == CameraKeyframe.ShotType.MEDIUM);
    }

    @Test
    void positionOfTracksOneTake() {
        ActorTake walker = take("Walker", 30, 0.0, 1.0);


        net.minecraft.world.phys.Vec3 at =
            DirectorPositioner.positionOfTake(walker, 5);
        assertNotNull(at);
        assertEquals(5.0, at.x, 1e-9);


        assertNull(DirectorPositioner.positionOfTake(walker, 500));
        assertNull(DirectorPositioner.positionOfTake(walker, -1));
        assertNull(DirectorPositioner.positionOfTake(null, 5));
        assertNull(DirectorShotSelector.findTake(List.of(walker), UUID.randomUUID()));
        assertEquals(walker, DirectorShotSelector.findTake(List.of(walker), walker.id));
    }

    @Test
    void presetsAreValidConfigs() {
        for (DirectorPresets.Preset preset : DirectorPresets.all()) {
            assertNotNull(preset.label());
            assertTrue(preset.holdTicks() >= DirectorConfig.MIN_HOLD_TICKS
                && preset.holdTicks() <= DirectorConfig.MAX_HOLD_TICKS);
            DirectorConfig config = new DirectorConfig(List.of(),
                preset.style(), false, 0.0f, preset.holdTicks(),
                preset.move(), preset.height(), preset.shotType(), List.of());


            assertEquals(preset.move(), config.move());
            assertEquals(preset.shotType(), config.shotType());
            assertEquals(preset.holdTicks(), config.holdTicks());
        }
        assertEquals(6, DirectorPresets.all().length);
    }

    @Test
    void shotTypeRoundTripsThroughTakeIo(@TempDir Path dir) throws Exception {
        ActorTake actorTake = take("Star", 10, 0.0, 0.0);
        actorTake.directorConfig = new DirectorConfig(
            List.of(new DirectorConfig.ActorSelection(actorTake.id, true, 1.0f)),
            DirectorConfig.DirectorStyle.FINCHER, false, 0.0f, 80,
            pdx.dev.fbactors.take.CameraMove.STATIC, null,
            CameraKeyframe.ShotType.OVER_SHOULDER, List.of());
        Path file = dir.resolve("director.take");
        pdx.dev.fbactors.take.TakeIO.write(file, actorTake);
        ActorTake back = pdx.dev.fbactors.take.TakeIO.read(file);
        assertNotNull(back.directorConfig);
        assertEquals(CameraKeyframe.ShotType.OVER_SHOULDER, back.directorConfig.shotType());
        assertEquals(pdx.dev.fbactors.take.CameraMove.STATIC, back.directorConfig.move());
        assertEquals(80, back.directorConfig.holdTicks());
        assertEquals(DirectorConfig.DirectorStyle.FINCHER, back.directorConfig.style());
        assertTrue(back.directorConfig.shots().isEmpty());
    }

    @Test
    void shotListRoundTripsThroughTakeIo(@TempDir Path dir) throws Exception {
        ActorTake actorTake = take("Star", 10, 0.0, 0.0);
        UUID subject = UUID.randomUUID();
        actorTake.directorConfig = new DirectorConfig(
            List.of(new DirectorConfig.ActorSelection(actorTake.id, true, 1.0f)),
            DirectorConfig.DirectorStyle.SPIELBERG, false, 0.0f, 40, null, null, null,
            List.of(
                new pdx.dev.fbactors.take.DirectorShot(subject,
                    pdx.dev.fbactors.take.CameraMove.ORBIT,
                    CameraKeyframe.ShotType.MEDIUM, null, 100, 50),
                new pdx.dev.fbactors.take.DirectorShot(subject, null, null, null, 60, 200)));
        Path file = dir.resolve("shots.take");
        pdx.dev.fbactors.take.TakeIO.write(file, actorTake);
        ActorTake back = pdx.dev.fbactors.take.TakeIO.read(file);
        assertNotNull(back.directorConfig);
        assertEquals(2, back.directorConfig.shots().size());
        pdx.dev.fbactors.take.DirectorShot first = back.directorConfig.shots().get(0);
        assertEquals(subject, first.subjectId());
        assertEquals(pdx.dev.fbactors.take.CameraMove.ORBIT, first.move());
        assertEquals(CameraKeyframe.ShotType.MEDIUM, first.shotType());
        assertNull(first.height());
        assertEquals(100, first.holdTicks());
        assertEquals(50, first.startTick());
        pdx.dev.fbactors.take.DirectorShot second = back.directorConfig.shots().get(1);
        assertNull(second.move());
        assertEquals(200, second.startTick());
    }

    @Test
    void directorShotClamps() {
        pdx.dev.fbactors.take.DirectorShot shot = new pdx.dev.fbactors.take.DirectorShot(
            UUID.randomUUID(), null, null, null, 1, -5);
        assertEquals(DirectorConfig.MIN_HOLD_TICKS, shot.holdTicks());
        assertEquals(0, shot.startTick());
        assertThrows(IllegalArgumentException.class, () ->
            new pdx.dev.fbactors.take.DirectorShot(null, null, null, null, 60, 0));
    }

    @Test
    void monitorWallCapsAtFive() {
        DirectorMonitors wall = new DirectorMonitors();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            ids.add(UUID.randomUUID());
        }
        for (int i = 0; i < 5; i++) {
            assertTrue(wall.tryMonitor(ids.get(i)));
        }
        assertEquals(5, wall.size());
        assertFalse(wall.tryMonitor(ids.get(5)), "wall must hold five feeds");
        assertTrue(wall.monitored(ids.get(0)));
        wall.unmonitor(ids.get(0));
        assertFalse(wall.monitored(ids.get(0)));
        assertTrue(wall.tryMonitor(ids.get(5)));
        assertTrue(wall.tryMonitor(ids.get(5)), "re-adding is idempotent");
        assertFalse(wall.tryMonitor(ids.get(6)));
        assertFalse(wall.tryMonitor(null));
        assertEquals(List.of(ids.get(1), ids.get(2), ids.get(3), ids.get(4), ids.get(5)),
            wall.ids());
    }
}

