package pdx.dev.fbactors.voice;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;


class VoiceAudioTest {

    private static short[] constant(int length, short value) {
        short[] samples = new short[length];
        java.util.Arrays.fill(samples, value);
        return samples;
    }

    private static int peak(short[] samples) {
        int max = 0;
        for (short s : samples) {
            max = Math.max(max, Math.abs((int) s));
        }
        return max;
    }

    @Test
    void limiterLeavesCoolLinesAlone() {
        short[] samples = constant(960, (short) 10000);
        assertSame(samples, VoiceBuffer.limit(samples));
    }

    @Test
    void limiterCapsHotLines() {
        short[] samples = constant(960, Short.MAX_VALUE);
        short[] capped = VoiceBuffer.limit(samples);
        assertTrue(peak(capped) <= 0.89f * Short.MAX_VALUE + 1.0f);
        assertTrue(peak(capped) > 0);
    }

    @Test
    void fadesSilenceBothEnds() {
        short[] samples = constant(960, (short) 20000);
        short[] faded = VoiceBuffer.applyEdgeFades(samples);
        assertEquals(0, faded[0]);
        assertEquals(0, faded[faded.length - 1]);
        assertEquals(20000, faded[faded.length / 2]);
        assertNotSame(samples, faded);
    }

    @Test
    void fadesShrinkForShortLines() {
        short[] samples = constant(10, (short) 20000);
        short[] faded = VoiceBuffer.applyEdgeFades(samples);
        assertEquals(10, faded.length);
        assertEquals(0, faded[0]);
        assertEquals(0, faded[9]);
    }

    @Test
    void resampleIsAThirdAndSteady() {
        short[] samples = constant(4800, (short) 9000);
        short[] down = VoiceBuffer.resample16k(samples);
        assertEquals(1600, down.length);
        for (short s : down) {
            assertEquals(9000, s);
        }
    }

    @Test
    void speechNeedsNoThresholdWhenActivationOff() {
        VoiceBuffer.configure(false, -30);
        try {
            assertTrue(VoiceBuffer.isSpeech(new short[960]));
            assertTrue(VoiceBuffer.isSpeech(constant(960, (short) 100)));
        } finally {
            VoiceBuffer.configure(true, -45);
        }
    }

    @Test
    void speechHonoursThresholdWhenActivationOn() {

        VoiceBuffer.configure(true, -30);
        try {
            assertFalse(VoiceBuffer.isSpeech(new short[960]));
            assertFalse(VoiceBuffer.isSpeech(constant(960, (short) 500)));
            assertTrue(VoiceBuffer.isSpeech(constant(960, (short) 2000)));
        } finally {
            VoiceBuffer.configure(true, -45);
        }
    }

    @Test
    void mostSensitiveThresholdHearsRoomTone() {

        VoiceBuffer.configure(true, -60);
        try {
            assertTrue(VoiceBuffer.isSpeech(constant(960, (short) 100)));
            assertFalse(VoiceBuffer.isSpeech(new short[960]));
        } finally {
            VoiceBuffer.configure(true, -45);
        }
    }

    @Test
    void wavHeaderRoundTrips() {
        short[] samples = constant(1600, (short) 1234);
        byte[] wav = Transcriber.wavBytes(samples);
        assertEquals(44 + 3200, wav.length);
        assertEquals('R', wav[0]);
        assertEquals('I', wav[1]);
        assertEquals('F', wav[2]);
        assertEquals('F', wav[3]);
        int dataSize = (wav[40] & 0xFF) | ((wav[41] & 0xFF) << 8)
            | ((wav[42] & 0xFF) << 16) | ((wav[43] & 0xFF) << 24);
        assertEquals(3200, dataSize);
        int rate = (wav[24] & 0xFF) | ((wav[25] & 0xFF) << 8)
            | ((wav[26] & 0xFF) << 16) | ((wav[27] & 0xFF) << 24);
        assertEquals(16000, rate);
    }

    @Test
    void transcriptDropsTimestamps() {
        String raw = "[00:00:00.000 --> 00:00:02.000]  hello   there\n[00:00:02.000 --> 00:00:03.000]  general kenobi\n";
        assertEquals("hello there general kenobi", Transcriber.cleanTranscript(raw));
    }

    @Test
    void transcriptEmptyStaysNull() {
        assertNull(Transcriber.cleanTranscript(""));
        assertNull(Transcriber.cleanTranscript("[00:00:00.000 --> 00:00:01.000]\n"));
    }

    @Test
    void captionKeyRoundTripsTake() {
        UUID id = UUID.randomUUID();
        Transcriber.Caption caption = new Transcriber.Caption(id, 41, "hello");
        assertEquals(id, caption.takeId());
        assertEquals(41, caption.lineTick());
        assertEquals("hello", caption.text());
    }

    private static Transcriber.LineSnapshot snapshot(int tick, int ticksLong) {
        return new Transcriber.LineSnapshot(tick, new short[ticksLong * 960]);
    }

    @Test
    void streamLaysLinesOnTheirTicks() {
        java.util.List<Transcriber.LineSnapshot> lines = java.util.List.of(
            snapshot(0, 1), snapshot(2, 1));
        short[] stream = Transcriber.buildStream16k(lines);
        assertEquals(3 * 320, stream.length);
        for (int i = 320; i < 640; i++) {
            assertEquals(0, stream[i]);
        }
    }

    @Test
    void streamKeepsSpeech() {
        short[] loud = new short[960];
        java.util.Arrays.fill(loud, (short) 12000);
        java.util.List<Transcriber.LineSnapshot> lines =
            java.util.List.of(new Transcriber.LineSnapshot(1, loud));
        short[] stream = Transcriber.buildStream16k(lines);
        assertEquals(2 * 320, stream.length);
        int peak = 0;
        for (int i = 320; i < 640; i++) {
            peak = Math.max(peak, Math.abs((int) stream[i]));
        }
        assertTrue(peak > 10000);
        for (int i = 0; i < 320; i++) {
            assertEquals(0, stream[i]);
        }
    }

    @Test
    void srtParsesSegments() {
        String srt = "1\n00:00:00,000 --> 00:00:01,000\nhello there\n\n"
            + "2\n00:00:01,500 --> 00:00:02,000\ngeneral kenobi\n";
        java.util.List<Transcriber.Segment> segments = Transcriber.parseSrt(srt);
        assertEquals(2, segments.size());
        assertEquals(0.0f, segments.get(0).startSec(), 0.001f);
        assertEquals(1.0f, segments.get(0).endSec(), 0.001f);
        assertEquals("hello there", segments.get(0).text());
        assertEquals(1.5f, segments.get(1).startSec(), 0.001f);
        assertEquals("general kenobi", segments.get(1).text());
    }

    @Test
    void mapAssignsOverlappingSegments() {
        java.util.List<Transcriber.LineSnapshot> lines = java.util.List.of(
            snapshot(0, 2), snapshot(10, 2));
        java.util.List<Transcriber.Segment> segments = java.util.List.of(
            new Transcriber.Segment(0.0f, 0.04f, "hello"),
            new Transcriber.Segment(0.2f, 0.24f, "edge"));
        java.util.Map<Integer, String> captions = Transcriber.mapCaptions(lines, segments);
        assertEquals("hello", captions.get(0));
        assertEquals("edge", captions.get(10));
    }

    @Test
    void mapClearsUnmatchedLines() {
        java.util.List<Transcriber.LineSnapshot> lines = java.util.List.of(snapshot(50, 2));
        java.util.List<Transcriber.Segment> segments = java.util.List.of(
            new Transcriber.Segment(0.0f, 1.0f, "elsewhere"));
        java.util.Map<Integer, String> captions = Transcriber.mapCaptions(lines, segments);
        assertNull(captions.get(50));
    }
}

