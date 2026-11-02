package pdx.dev.fbactors.debug;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DiagnosticLog {
    private DiagnosticLog() {}
    public static String tail(Path path,int maximumBytes) throws IOException {
        if(maximumBytes<=0) return "";
        try(var file=FileChannel.open(path,StandardOpenOption.READ)) {
            long start=Math.max(0,file.size()-maximumBytes);
            file.position(start);
            var buffer=ByteBuffer.allocate(maximumBytes);
            while(buffer.hasRemaining() && file.read(buffer)>0) {}
            buffer.flip();
            String text=StandardCharsets.UTF_8.decode(buffer).toString();
            if(start>0) { int newline=text.indexOf('\n'); if(newline>=0) text=text.substring(newline+1); }
            return text;
        }
    }
}
