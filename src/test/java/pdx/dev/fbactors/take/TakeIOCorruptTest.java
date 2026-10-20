package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;


class TakeIOCorruptTest {

    private static ActorTake take() {
        List<ActorFrame> frames = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            frames.add(new ActorFrame(i, 64, 0, 0, 0, 0,
                (byte) Pose.STANDING.ordinal(), (byte) 0));
        }
        return new ActorTake(UUID.randomUUID(), "T", "minecraft:overworld", 0,
            frames, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    @Test
    void badMagicIsAnIOException(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("junk.take");
        Files.write(file, new byte[] {1, 2, 3, 4});
        assertThrows(IOException.class, () -> TakeIO.read(file));
    }

    @Test
    void hugeFrameCountRefusedBeforeAllocating(@TempDir Path dir) throws Exception {
        ActorTake valid = take();
        Path probe = dir.resolve("probe.take");
        TakeIO.write(probe, valid);
        int version = TakeIO.peekVersion(probe);
        assertTrue(version > 0, "probe take must report its version");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x46424154);
        out.writeInt(version);
        out.writeLong(valid.id.getMostSignificantBits());
        out.writeLong(valid.id.getLeastSignificantBits());
        out.writeUTF("Corrupt");
        out.writeUTF("");
        out.writeUTF("minecraft:overworld");
        out.writeInt(0);
        out.writeInt(2_000_000);
        out.flush();
        Path file = dir.resolve("huge.take");
        Files.write(file, bytes.toByteArray());
        assertThrows(IOException.class, () -> TakeIO.read(file));
    }

    @Test
    void truncatedFileIsAnIOException(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("cut.take");
        TakeIO.write(file, take());
        byte[] raw = Files.readAllBytes(file);
        assertTrue(raw.length > 64, "take file must be large enough to truncate");
        Files.write(file, java.util.Arrays.copyOf(raw, raw.length / 2));
        assertThrows(IOException.class, () -> TakeIO.read(file));
    }
}

