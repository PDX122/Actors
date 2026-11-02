package pdx.dev.fbactors.morph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewIdentityTest {
    @Test void previewIdsAreAssignedAndDistinctAcrossConcurrentPickers() {
        var ids=java.util.stream.IntStream.range(0,1000).parallel().map(i -> PreviewIdentity.next()).toArray();
        assertEquals(1000,java.util.Arrays.stream(ids).distinct().count());
        for (int id:ids) assertTrue(id<0,"Preview IDs must be nonzero and outside the world entity range");
    }
}
