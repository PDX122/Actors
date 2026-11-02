package pdx.dev.fbactors.emote;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.entity.Pose;
import org.junit.jupiter.api.Test;


class EmoteStanceTest {

    private static EmoteKeyframe key(double yOffset, float bodyPitch, float headPitch,
                                     String stance) {
        return new EmoteKeyframe(0, 0.0, yOffset, 0.0,
            0.0f, bodyPitch, 0.0f, headPitch,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            EmoteKeyframe.SMOOTH, stance);
    }

    @Test
    void explicitStanceWinsOverInference() {

        EmotePose pose = EmotePose.of(key(-0.12, 14.0f, 6.0f, "sitting"));
        assertEquals(Pose.SITTING, pose.stance());
        assertEquals(Pose.SITTING, EmoteApplier.stanceFor(pose));
    }

    @Test
    void inferenceUnchangedWithoutAName() {
        assertEquals(Pose.SWIMMING,
            EmoteApplier.stanceFor(EmotePose.of(key(-0.62, 78.0f, 12.0f, null))));
        assertEquals(Pose.CROUCHING,
            EmoteApplier.stanceFor(EmotePose.of(key(-0.6, 20.0f, 0.0f, null))));
        assertNull(EmoteApplier.stanceFor(EmotePose.of(key(0.0, 0.0f, 0.0f, null))));
    }

    @Test
    void stanceAloneIsNotIdentity() {
        EmotePose pose = EmotePose.of(key(0.0, 0.0f, 0.0f, "sitting"));
        assertFalse(pose.isIdentity());
        assertFalse(key(0.0, 0.0f, 0.0f, "sitting").isNeutral());
        assertTrue(EmotePose.identity().isIdentity());
    }

    @Test
    void blendTakesTheLaterStance() {
        EmotePose sitting = EmotePose.of(key(0.0, 0.0f, 0.0f, "sitting"));
        EmotePose plain = EmotePose.of(key(0.0, 0.0f, 0.0f, null));
        assertEquals(Pose.SITTING, plain.blend(sitting, 0.5f).stance());
        assertEquals(Pose.SITTING, sitting.blend(plain, 0.5f).stance());
        assertNull(plain.blend(plain, 0.5f).stance());
    }

    @Test
    void unknownStanceIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> key(0.0, 0.0f, 0.0f, "diving"));
        assertThrows(IllegalArgumentException.class, () -> EmoteStance.parse("perching"));
        assertNull(EmoteStance.parse(null));
        assertNull(EmoteStance.parse("  "));
    }

    @Test
    void stanceRoundTripsThroughJson() {
        EmoteKeyframe key = key(-0.12, 14.0f, 6.0f, "SITTING");
        assertEquals("sitting", key.stance());
        Emote emote = new Emote("sit-test", "Sit test", false, 10,
            java.util.List.of(key, key.atTick(5)));
        Emote read = EmoteIO.fromJson(EmoteIO.toJson(emote), "sit-test.json");
        assertEquals("sitting", read.keyframes().get(1).stance());
        assertEquals(Pose.SITTING, EmotePose.of(read.keyframes().get(1)).stance());
    }

    @Test
    void absentStanceReadsAsInfer() {
        EmoteKeyframe key = key(0.0, 0.0f, 0.0f, null);
        Emote emote = new Emote("plain-test", "Plain", false, 10,
            java.util.List.of(key, key.atTick(5)));
        Emote read = EmoteIO.fromJson(EmoteIO.toJson(emote), "plain-test.json");
        assertNull(read.keyframes().get(0).stance());
    }

    @Test
    void clipCodecRoundTrips() {
        EmoteTrack.Clip clip = new EmoteTrack.Clip(100, 200, "sit", 4.0f, 6.0f, true, 1.5f);
        EmoteTrack.Clip read = EmoteIO.decodeClip(EmoteIO.encodeClip(clip));
        assertNotNull(read);
        assertEquals(clip, read);
    }

    @Test
    void clipCodecDropsBadEntries() {
        com.google.gson.JsonObject missing = new com.google.gson.JsonObject();
        missing.addProperty("start", 10);
        assertNull(EmoteIO.decodeClip(missing));
        com.google.gson.JsonObject blank = new com.google.gson.JsonObject();
        blank.addProperty("emote", "  ");
        assertNull(EmoteIO.decodeClip(blank));
    }

    @Test
    void shippedSitEmoteSits() {
        Emote sit = EmoteLibrary.builtIns().stream()
            .filter(emote -> emote.id().equals("sit"))
            .findFirst()
            .orElse(null);
        assertNotNull(sit);
        assertEquals(Pose.SITTING, sit.poseAt(0).stance());
        assertEquals(Pose.SITTING, sit.poseAt(20).stance());
        assertEquals(Pose.SITTING, EmoteApplier.stanceFor(sit.poseAt(20)));
    }
}

