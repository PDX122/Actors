package pdx.dev.fbactors.convert;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class McprReaderTest {

    private static void writeRecord(DataOutputStream out, int timestamp, byte[] payload)
            throws Exception {
        out.writeInt(timestamp);
        out.writeInt(payload.length);
        out.write(payload);
    }

    private static Path mcpr(Path dir, String meta, List<byte[]> tmcpr) throws Exception {
        Path file = dir.resolve("test.mcpr");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("metaData.json"));
            zip.write(meta.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("recording.tmcpr"));
            ByteArrayOutputStream flat = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(flat)) {
                int ts = 0;
                for (byte[] payload : tmcpr) {
                    writeRecord(out, ts, payload);
                    ts += 50;
                }
            }
            zip.write(flat.toByteArray());
            zip.closeEntry();
        }
        return file;
    }

    @Test
    void groupsPacketsIntoFiftyMsTicks(@TempDir Path dir) throws Exception {
        String meta = "{\"duration\":150,\"mcversion\":\"26.2\",\"customServerName\":\"W\"}";
        Path file = mcpr(dir, meta,
            List.of(new byte[]{1, 2, 3}, new byte[]{4}, new byte[]{5, 6}));
        McprReader.Recording recording = McprReader.read(file);
        assertEquals("26.2", recording.mcVersion());
        assertEquals(3, recording.totalTicks());
        assertEquals(1, recording.ticks().get(0).size());
        assertArrayEquals(new byte[]{1, 2, 3}, recording.ticks().get(0).get(0));
        assertArrayEquals(new byte[]{5, 6}, recording.ticks().get(2).get(0));
    }

    @Test
    void missingStreamIsAnErrorNotAnEmptyTake(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("empty.mcpr");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("metaData.json"));
            zip.write("{\"duration\":0}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThrows(Exception.class, () -> McprReader.read(file));
    }

    @Test
    void truncatedRecordIsAnError(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("trunc.mcpr");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("metaData.json"));
            zip.write("{\"duration\":50}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("recording.tmcpr"));
            zip.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 5, 1, 2});
            zip.closeEntry();
        }
        assertThrows(Exception.class, () -> McprReader.read(file));
    }
}

