package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;


class TimelineOpsTest {

    @Test
    void razorConvertsPlayheadToTakeRelative() {
        assertEquals(50, TimelineOps.razorRelative(1000, 0, 1050, 100));
        assertEquals(40, TimelineOps.razorRelative(1000, 10, 1050, 100));
        assertTrue(TimelineOps.canRazor(1000, 0, 1050, 100));
    }

    @Test
    void razorRejectsEdgesAndOutside() {
        assertEquals(-1, TimelineOps.razorRelative(1000, 0, 1000, 100));
        assertEquals(-1, TimelineOps.razorRelative(1000, 0, 1100, 100));
        assertEquals(-1, TimelineOps.razorRelative(1000, 0, 999, 100));
        assertEquals(-1, TimelineOps.razorRelative(1000, 0, 1200, 100));
        assertFalse(TimelineOps.canRazor(1000, 0, 1000, 100));
    }

    @Test
    void snapPicksNearestCandidateInRange() {
        int[] candidates = {1000, 1200};
        assertEquals(1000, TimelineOps.snapStart(1003, candidates, 2.0f, 6.0f));
        assertEquals(1010, TimelineOps.snapStart(1010, candidates, 2.0f, 6.0f));
        assertEquals(1003, TimelineOps.snapStart(1003, null, 2.0f, 6.0f));
        assertEquals(1003, TimelineOps.snapStart(1003, candidates, 0.0f, 6.0f));
        assertEquals(1003, TimelineOps.snapStart(1003, candidates, Float.NaN, 6.0f));
        assertEquals(1003, TimelineOps.snapStart(1003, candidates, 2.0f, -1.0f));
    }

    @Test
    void rippleIgnoresInvertedRange() {
        assertEquals(0, TimelineOps.rippleShift(1300, 1220, 1200));
        assertEquals(-21, TimelineOps.rippleShift(1300, 1200, 1220));
        assertEquals(-21, TimelineOps.rippleShift(Integer.MAX_VALUE, 1200, 1220));
    }

    @Test
    void razorHandlesExtremeTicks() {
        assertEquals(-1, TimelineOps.razorRelative(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 100));
        assertTrue(TimelineOps.razorRelative(0, 0, 50, Integer.MAX_VALUE) >= 0);
    }

    @Test
    void pasteKeysClampsOverflow() {
        CameraTrack track = new CameraTrack();
        track.add(new CameraKeyframe(Integer.MAX_VALUE - 5, 0, 64, 0, 0, 0, 70,
            CameraKeyframe.ShotType.WIDE, null, CameraKeyframe.EASING_SMOOTH));
        List<CameraKeyframe> pasted = TimelineOps.pasteKeys(
            TimelineOps.copyKeys(track, 0, Integer.MAX_VALUE), Integer.MAX_VALUE);
        assertEquals(1, pasted.size());
        assertEquals(Integer.MAX_VALUE, pasted.get(0).tick());
    }

    @Test
    void rippleShiftsOnlyClipsAfterGap() {
        assertEquals(-21, TimelineOps.rippleShift(1300, 1200, 1220));
        assertEquals(0, TimelineOps.rippleShift(1100, 1200, 1220));
        assertEquals(0, TimelineOps.rippleShift(1210, 1200, 1220));
    }

    @Test
    void keyCopyPasteRetimesGaps() {
        CameraTrack track = new CameraTrack();
        track.add(new CameraKeyframe(10, 0, 64, 0, 0, 0, 70,
            CameraKeyframe.ShotType.WIDE, null, CameraKeyframe.EASING_SMOOTH));
        track.add(new CameraKeyframe(20, 5, 64, 0, 0, 0, 70,
            CameraKeyframe.ShotType.MEDIUM, null, CameraKeyframe.EASING_SMOOTH));
        track.add(new CameraKeyframe(100, 9, 64, 0, 0, 0, 70,
            CameraKeyframe.ShotType.MEDIUM, null, CameraKeyframe.EASING_SMOOTH));

        List<CameraKeyframe> copied = TimelineOps.copyKeys(track, 0, 30);
        assertEquals(2, copied.size());

        List<CameraKeyframe> pasted = TimelineOps.pasteKeys(copied, 200);
        assertEquals(2, pasted.size());
        assertEquals(200, pasted.get(0).tick());
        assertEquals(210, pasted.get(1).tick());
        assertTrue(TimelineOps.pasteKeys(List.of(), 50).isEmpty());
    }

    @Test
    void audibleFollowsEnabledAndSolo() {
        assertFalse(TimelineOps.isAudible(false, false, false));
        assertTrue(TimelineOps.isAudible(true, false, false));
        assertFalse(TimelineOps.isAudible(true, true, false));
        assertTrue(TimelineOps.isAudible(true, true, true));
    }

    @Test
    void lockGuardsEdits() {
        assertTrue(TimelineOps.canEdit(false));
        assertFalse(TimelineOps.canEdit(true));
    }
}

