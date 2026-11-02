package pdx.dev.fbactors.filters;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EffectsTimelineTest {
    private EffectClip clip(int start, int end, String filter) {
        EffectClip clip = new EffectClip();
        clip.start = start;
        clip.end = end;
        clip.look.add(filter);
        return clip;
    }

    @Test void deletingAnActorEffectTrackPreservesOtherTargetsAndCanBeRestoredFromSnapshot() {
        var track = new EffectsTimeline();
        var actor = UUID.randomUUID();
        var other = UUID.randomUUID();
        var first = clip(0, 100, "aurora");
        first.actor = actor;
        var second = clip(100, 200, "ember");
        second.actor = actor;
        second.enabled = false;
        var unrelated = clip(0, 200, "carbon");
        unrelated.actor = other;
        var scene = clip(0, 200, "daybreak");
        track.clips.addAll(List.of(first, second, unrelated, scene));
        var before = track.copy();
        assertTrue(track.removeTrack(actor));
        assertEquals(List.of(unrelated, scene), track.clips);
        assertFalse(track.removeTrack(actor));
        var restored = before.copy();
        assertEquals(4, restored.clips.size());
        assertNotNull(restored.find(first.id));
        assertNotNull(restored.find(second.id));
        assertFalse(restored.find(second.id).enabled);
        assertEquals(List.of("aurora"), restored.find(first.id).look.filters);
        assertEquals(2, track.clips.size());
    }

    @Test void deletingMiscEffectsHonorsItsLockAndPreservesActorEffects() {
        var track = new EffectsTimeline();
        var scene = clip(0, 100, "aurora");
        var actor = clip(0, 100, "ember");
        actor.actor = UUID.randomUUID();
        track.clips.addAll(List.of(scene, actor));
        track.locked = true;
        assertFalse(track.removeTrack(null));
        assertEquals(2, track.clips.size());
        track.locked = false;
        assertTrue(track.removeTrack(null));
        assertEquals(List.of(actor), track.clips);
        assertFalse(track.removeTrack(null));
        assertTrue(track.enabled);
    }

    @Test void clipBoundsAreHalfOpenAndFadesUseSmoothstep() {
        EffectClip clip = clip(20, 120, "aurora");
        assertEquals(0, clip.weight(19));
        assertEquals(0, clip.weight(20));
        assertEquals(0.5f, clip.weight(25), 0.0001f);
        assertEquals(1, clip.weight(30));
        assertEquals(0.5f, clip.weight(115), 0.0001f);
        assertEquals(0, clip.weight(120));
        assertEquals(0, clip.weight(Float.NaN));
        clip.fadeIn = clip.fadeOut = 0;
        assertEquals(1, clip.weight(20));
    }

    @Test void evaluationFadesWithoutMutatingClipOrReplayGrade() {
        var track = new EffectsTimeline();
        var clip = clip(20, 120, "ember");
        clip.look.intensity = 0.8f;
        track.clips.add(clip);
        var base = new LookSettings();
        assertSame(base, track.evaluate(0, base));
        var evaluated = track.evaluate(25, base);
        assertEquals(0.4f, evaluated.intensity, 0.0001f);
        assertEquals(0.8f, clip.look.intensity);
        evaluated.filters.clear();
        assertEquals(List.of("ember"), clip.look.filters);
        assertFalse(base.enabled);
    }

    @Test void overlapsChooseLatestStartAndMuteRestoresEarlierClip() {
        var track = new EffectsTimeline();
        var first = clip(0, 100, "aurora");
        var second = clip(30, 60, "ember");
        track.clips.add(first);
        track.clips.add(second);
        assertSame(second, track.active(40));
        second.enabled = false;
        assertSame(first, track.active(40));
        second.enabled = true;
        second.look.enabled = false;
        assertSame(first, track.active(40));
        track.enabled = false;
        assertNull(track.active(40));
    }

    @Test void additionsFindOpenSpaceWithoutTouchingAdjacentClips() {
        var track = new EffectsTimeline();
        track.clips.add(clip(200, 300, "ember"));
        track.clips.add(clip(0, 100, "aurora"));
        assertEquals(100, track.nextStart(20, 100));
        assertEquals(300, track.nextStart(150, 100));
        assertEquals(300, track.nextStart(300, 100));
        assertEquals(100, track.nextStart(100, 100));
    }

    @Test void normalizationRepairsCorruptClipsAndDuplicateIds() {
        var track = new EffectsTimeline();
        var a = clip(-30, -10, "aurora");
        a.fadeIn = 999;
        a.fadeOut = -5;
        a.name = null;
        var b = a.copy();
        b.start = 10;
        b.end = 20;
        b.look = null;
        track.clips = new ArrayList<>();
        track.clips.add(null);
        track.clips.add(a);
        track.clips.add(b);
        track.normalize();
        assertEquals(2, track.clips.size());
        assertEquals(0, a.start);
        assertEquals(1, a.end);
        assertEquals(1, a.fadeIn);
        assertEquals(0, a.fadeOut);
        assertEquals("Effect", a.name);
        assertNotEquals(a.id, b.id);
        assertNotNull(b.look);
    }

    @Test void copiesOwnAllNestedClipAndLookState() {
        var track = new EffectsTimeline();
        var original = clip(0, 100, "prism");
        track.clips.add(original);
        var copy = track.copy();
        copy.clips.getFirst().look.filters.clear();
        copy.clips.getFirst().start = 20;
        copy.clips.clear();
        assertEquals(1, track.clips.size());
        assertEquals(0, original.start);
        assertEquals(List.of("prism"), original.look.filters);
    }

    @Test void trackJsonRoundTripsAndOldLooksStaySeparate() {
        var track = new EffectsTimeline();
        track.locked = true;
        var clip = clip(100, 200, "halation");
        track.clips.add(clip);
        var gson = new com.google.gson.Gson();
        var restored = gson.fromJson(gson.toJson(track), EffectsTimeline.class);
        restored.normalize();
        assertEquals(clip.id, restored.clips.getFirst().id);
        assertEquals(List.of("halation"), restored.active(150).look.filters);
        assertTrue(restored.locked);
        var empty = gson.fromJson("{\"clips\":null}", EffectsTimeline.class);
        empty.normalize();
        assertTrue(empty.clips.isEmpty());
    }

    @Test void actorLooksNeverBecomeSceneLooksOrOtherActorsLooks() {
        var track = new EffectsTimeline();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var actor = clip(0, 100, "carbon");
        actor.actor = a;
        actor.fadeIn = 0;
        track.clips.add(actor);
        var base = new LookSettings();
        assertSame(base, track.evaluate(50, base));
        assertNull(track.active(50));
        assertNull(track.actorLook(50, b));
        assertNull(track.actorLook(50, null));
        assertEquals(List.of("carbon"), track.actorLook(50, a).filters);
        assertEquals(a, track.copy().clips.getFirst().actor);
    }

    @Test void miscAndActorLanesHaveIndependentSpaceAndMute() {
        var track = new EffectsTimeline();
        UUID actor = UUID.randomUUID();
        var subject = clip(0, 100, "carbon");
        subject.actor = actor;
        track.clips.add(subject);
        assertEquals(0, track.nextStart(0, 100));
        assertEquals(100, track.nextStart(0, 100, actor));
        var scene = clip(0, 100, "ember");
        track.clips.add(scene);
        assertSame(scene, track.active(50));
        assertSame(subject, track.active(50, actor));
        track.enabled = false;
        assertNull(track.active(50));
        assertSame(subject, track.active(50, actor));
    }

    @Test void targetedClipSurvivesJsonAndLegacyClipDefaultsToMisc() {
        var gson = new com.google.gson.Gson();
        var clip = clip(0, 100, "aurora");
        clip.actor = UUID.randomUUID();
        var copy = gson.fromJson(gson.toJson(clip), EffectClip.class);
        assertEquals(clip.actor, copy.actor);
        var legacy = gson.fromJson("{\"start\":20,\"end\":50}", EffectClip.class);
        legacy.normalize();
        assertNull(legacy.actor);
    }

    @Test void legacyReplayGradeBecomesOneVisibleMiscClip() {
        var track = new EffectsTimeline();
        var existing = clip(20, 50, "ember");
        track.clips.add(existing);
        var old = new LookSettings();
        old.add("aurora");
        old.exposure = 0.5f;
        assertTrue(track.importReplayGrade(old, 200));
        assertEquals(2, track.clips.size());
        assertEquals(List.of("aurora"), track.active(10).look.filters);
        assertEquals(0.5f, track.active(10).look.exposure);
        assertSame(existing, track.active(30));
        assertNull(track.active(200));
        assertFalse(track.importReplayGrade(old, 200));
        assertEquals(2, track.clips.size());
        assertTrue(track.copy().replayGradeImported);
    }

    @Test void resourceLimitsAndUnknownSelectionsAreSafe() {
        var track = new EffectsTimeline();
        for (int i = 0; i < 300; i++) track.clips.add(clip(i, i + 1, "aurora"));
        track.normalize();
        assertEquals(256, track.clips.size());
        assertNull(track.find(UUID.randomUUID()));
        assertNull(track.active(Float.POSITIVE_INFINITY));
    }
}
