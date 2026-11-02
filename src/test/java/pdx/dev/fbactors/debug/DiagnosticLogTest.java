package pdx.dev.fbactors.debug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;

class DiagnosticLogTest {
    @TempDir Path temporary;
    @Test void readsOnlyABoundedTailAndDropsTheFirstPartialLine() throws Exception {
        var log=temporary.resolve("latest.log");
        Files.writeString(log,"old line\n".repeat(100_000)+"failure\nlast line\n");
        String tail=DiagnosticLog.tail(log,1024);
        assertTrue(tail.length()<=1024); assertTrue(tail.endsWith("failure\nlast line\n"));
        assertTrue(tail.startsWith("old line\n"));
    }
    @Test void missingAndSmallLogsHavePredictableBehavior() throws Exception {
        var log=temporary.resolve("latest.log");
        assertThrows(java.io.IOException.class,() -> DiagnosticLog.tail(log,1024));
        Files.writeString(log,"short log");
        assertEquals("short log",DiagnosticLog.tail(log,1024));
        assertEquals("",DiagnosticLog.tail(log,0));
    }
}
