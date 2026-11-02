package pdx.dev.fbactors.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicSave {
    private static final Object COMMIT_LOCK = new Object();

    private AtomicSave() {
    }

    public static boolean writeJson(Path dir, String fileName, WriteBody body) {
        Path temp = null;
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);

            temp = Files.createTempFile(dir, fileName + ".", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp,
                java.nio.charset.StandardCharsets.UTF_8)) {
                body.writeTo(writer);
            }

            synchronized (COMMIT_LOCK) {
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException | RuntimeException ignored) {}
        }
    }

    @FunctionalInterface
    public interface WriteBody {
        void writeTo(Writer writer) throws IOException;
    }
}
