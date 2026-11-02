package pdx.dev.fbactors.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class JsonFilesTest {
    @TempDir Path dir;
    record State(String name) {}
    @Test void validJsonLoadsAndInvalidJsonIsReportedWithoutModifyingFile() throws Exception {
        var file=dir.resolve("state.json"); Files.writeString(file,"{\"name\":\"cat\"}");
        assertEquals("cat",JsonFiles.read(file,State.class,128).name());
        String broken="{\"name\":"; Files.writeString(file,broken);
        assertThrows(IOException.class,() -> JsonFiles.read(file,State.class,128));
        assertEquals(broken,Files.readString(file));
    }
    @Test void oversizedFilesAreRejectedBeforeParsing() throws Exception {
        var file=dir.resolve("big.json"); Files.writeString(file," ".repeat(256));
        assertThrows(IOException.class,() -> JsonFiles.read(file,State.class,128));
    }
}
