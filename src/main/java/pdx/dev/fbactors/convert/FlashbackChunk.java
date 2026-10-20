package pdx.dev.fbactors.convert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public final class FlashbackChunk {


    public static final int MAGIC = 0xD780E884;


    public record Action(int index, byte[] payload) {
    }


    public record Parsed(List<String> names, List<Action> snapshot) {
    }

    private FlashbackChunk() {
    }


    public static Parsed parse(byte[] chunk) throws IOException {
        Cursor cursor = new Cursor(chunk);
        if (cursor.readInt() != MAGIC) {
            throw new IOException("Not a Flashback chunk (bad magic)");
        }
        int count = cursor.readVarInt();
        if (count < 0 || count > 64) {
            throw new IOException("Implausible action table of " + count);
        }
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(cursor.readString());
        }
        int snapshotSize = cursor.readInt();
        if (snapshotSize < 0 || cursor.pos + snapshotSize > chunk.length) {
            throw new IOException("Implausible snapshot size " + snapshotSize);
        }
        int end = cursor.pos + snapshotSize;
        List<Action> snapshot = new ArrayList<>();
        while (cursor.pos < end) {
            int id = cursor.readVarInt();
            int size = cursor.readInt();
            if (id < 0 || id >= names.size() || size < 0 || cursor.pos + size > end) {
                throw new IOException("Corrupt snapshot action (id " + id + ", size " + size + ")");
            }
            byte[] payload = new byte[size];
            System.arraycopy(chunk, cursor.pos, payload, 0, size);
            cursor.pos += size;
            snapshot.add(new Action(id, payload));
        }
        return new Parsed(List.copyOf(names), List.copyOf(snapshot));
    }

    private static final class Cursor {
        private final byte[] data;
        private int pos;

        private Cursor(byte[] data) {
            this.data = data;
        }

        private int readVarInt() throws IOException {
            int value = 0;
            int shift = 0;
            while (true) {
                if (pos >= data.length || shift > 35) {
                    throw new IOException("Truncated varint");
                }
                byte b = data[pos++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
                shift += 7;
            }
        }

        private int readInt() throws IOException {
            if (pos + 4 > data.length) {
                throw new IOException("Truncated int");
            }
            int value = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
            pos += 4;
            return value;
        }

        private String readString() throws IOException {
            int length = readVarInt();
            if (length < 0 || pos + length > data.length) {
                throw new IOException("Truncated string of length " + length);
            }
            String value = new String(data, pos, length, java.nio.charset.StandardCharsets.UTF_8);
            pos += length;
            return value;
        }
    }
}

