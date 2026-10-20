package pdx.dev.fbactors.station;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.Pose;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorTake;


class StationSummaryTest {

    private static ActorTake take(int frames) {
        List<ActorFrame> list = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            list.add(new ActorFrame(i, 64.0, 0.0, 0.0f, 0.0f, 0.0f,
                (byte) Pose.STANDING.ordinal(), (byte) 0));
        }
        return new ActorTake(UUID.randomUUID(), "T", "minecraft:overworld", 0,
            list, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    @Test
    void emptyCastReadsEmpty() {
        assertEquals("No actors in this replay",
            StationSummary.statusLine(List.of(), Set.of()));
    }

    @Test
    void countsActorsAndFootage() {
        List<ActorTake> takes = List.of(take(40), take(20));
        assertEquals("2 actors, 3.0s of footage",
            StationSummary.statusLine(takes, Set.of()));
    }

    @Test
    void lockMuteAndMultiSelectSuffixes() {
        ActorTake a = take(20);
        ActorTake b = take(20);
        a.locked = true;
        b.enabled = false;
        String line = StationSummary.statusLine(
            List.of(a, b), Set.of(a.id, b.id));
        assertTrue(line.contains("1 locked"), line);
        assertTrue(line.contains("1 muted"), line);
        assertTrue(line.contains("2 selected"), line);
    }

    @Test
    void singleSelectionStaysQuiet() {
        ActorTake a = take(20);
        String line = StationSummary.statusLine(List.of(a), Set.of(a.id));
        assertEquals("1 actor, 1.0s of footage", line);
    }
}

