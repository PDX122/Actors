package pdx.dev.fbactors.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RetryingJsonSavesTest {
    @TempDir Path dir;
    record State(String name) {}
    @Test void failedWritesStayWithOriginalReplayAndRecoverAfterSwitch() throws Exception {
        var a=UUID.randomUUID(); var b=UUID.randomUUID();
        Files.writeString(dir.resolve(a.toString()),"blocks directory creation");
        var saves=new RetryingJsonSaves<State>("state.json");
        saves.put(a,new State("first replay"));
        saves.flush(id -> dir.resolve(id.toString()));
        assertEquals("first replay",saves.pending(a).name());
        saves.put(b,new State("second replay"));
        saves.flush(id -> dir.resolve(id.toString()));
        assertEquals("second replay",JsonFiles.read(dir.resolve(b.toString()).resolve("state.json"),State.class,1024).name());
        assertNull(saves.pending(b)); assertNotNull(saves.pending(a));
        Files.delete(dir.resolve(a.toString()));
        saves.flush(id -> dir.resolve(id.toString()));
        assertTrue(saves.isEmpty());
        assertEquals("first replay",JsonFiles.read(dir.resolve(a.toString()).resolve("state.json"),State.class,1024).name());
    }
}
