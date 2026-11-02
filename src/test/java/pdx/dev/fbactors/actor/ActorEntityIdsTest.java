package pdx.dev.fbactors.actor;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ActorEntityIdsTest {
    @Test void authoredSpawnsHaveUniqueIdsOutsideReplayViewerAndCloneRanges() {
        var ids = new HashSet<Integer>();
        for (int i = 0; i < 10_000; i++) {
            int id = ActorEntityIds.next();
            assertTrue(id >= -980_000_000 && id < 0);
            assertTrue(ids.add(id));
        }
    }
}
