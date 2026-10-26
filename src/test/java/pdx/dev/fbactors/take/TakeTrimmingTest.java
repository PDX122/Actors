package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TakeTrimmingTest {
    private static ActorTake take() {
        var take = new ActorTake(UUID.randomUUID(), "Trim", "minecraft:overworld", 100);
        for (int i = 0; i < 10; i++) take.frames.add(new ActorFrame(i, 64, 0, 0, 0, 0, (byte) 0, (byte) 0));
        take.offsetTicks = 20;
        return take;
    }

    @Test void startTrimCannotCrossEndTrim() {
        var take = take();
        take.trimOut = 4;
        assertEquals(5, TakeTrimming.startAt(take, 200));
        assertEquals(2, TakeTrimming.startAt(take, 122));
        assertEquals(0, TakeTrimming.startAt(take, 0));
    }

    @Test void endTrimCannotCrossStartTrim() {
        var take = take();
        take.trimIn = 6;
        assertEquals(3, TakeTrimming.endAt(take, 0));
        assertEquals(1, TakeTrimming.endAt(take, 128));
        assertEquals(0, TakeTrimming.endAt(take, 200));
    }

    @Test void extremePlayheadsDoNotOverflow() {
        var take = take();
        assertEquals(9, TakeTrimming.startAt(take, Integer.MAX_VALUE));
        assertEquals(0, TakeTrimming.startAt(take, Integer.MIN_VALUE));
        assertEquals(0, TakeTrimming.endAt(take, Integer.MAX_VALUE));
        assertEquals(9, TakeTrimming.endAt(take, Integer.MIN_VALUE));
    }
}
