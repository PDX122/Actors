package pdx.dev.fbactors.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AtomicSaveTest {
    @TempDir Path dir;
    @Test void serializationAndIoFailuresKeepLastGoodSave() throws Exception {
        Files.writeString(dir.resolve("state.json"), "original");
        assertFalse(AtomicSave.writeJson(dir,"state.json",w -> { w.write("partial"); throw new IllegalArgumentException("invalid number"); }));
        assertEquals("original",Files.readString(dir.resolve("state.json")));
        assertFalse(AtomicSave.writeJson(dir,"state.json",w -> { w.write("partial"); throw new IOException("disk failed"); }));
        assertEquals("original",Files.readString(dir.resolve("state.json")));
        try(var files=Files.list(dir)) { assertEquals(1,files.count()); }
    }
    @Test void simultaneousWritersCannotMixTemporaryContents() throws Exception {
        var entered=new CountDownLatch(2); var release=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            java.util.function.Function<String,Boolean> save=value -> AtomicSave.writeJson(dir,"state.json",w -> {
                w.write(value); entered.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new IOException("writer timed out"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            });
            var a=pool.submit(() -> save.apply("first complete save"));
            var b=pool.submit(() -> save.apply("second complete save"));
            assertTrue(entered.await(5,TimeUnit.SECONDS)); release.countDown();
            assertTrue(a.get(5,TimeUnit.SECONDS)); assertTrue(b.get(5,TimeUnit.SECONDS));
            assertTrue(java.util.Set.of("first complete save","second complete save").contains(Files.readString(dir.resolve("state.json"))));
            try(var files=Files.list(dir)) { assertEquals(1,files.count()); }
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
