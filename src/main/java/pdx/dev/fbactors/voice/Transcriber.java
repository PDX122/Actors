package pdx.dev.fbactors.voice;

import pdx.dev.fbactors.settings.FbSettings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public final class Transcriber {


    private static final String ENGINE_TAG = "b5454";

    private static final String ENGINE_BASE =
        "https://github.com/ggml-org/whisper.cpp/releases/download/" + ENGINE_TAG + "/";

    private static final String MODEL_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin";

    private static final String MODEL_FILE = "ggml-tiny.en.bin";

    private static final long TAKE_TIMEOUT_SECONDS = 1200L;

    private static final int MAX_CAPTION = 280;

    public enum State {
        MISSING,
        DOWNLOADING,
        READY,
        WORKING,
        DONE,
        ERROR
    }


    public record Caption(UUID takeId, int lineTick, String text) {
    }


    public record LineSnapshot(int tick, short[] samples) {
    }

    private static volatile State state = State.MISSING;
    private static volatile String status = "";
    private static volatile double progress = 0.0;
    private static volatile boolean busy;

    private static final ConcurrentLinkedQueue<Caption> RESULTS = new ConcurrentLinkedQueue<>();

    private Transcriber() {
    }

    public static State state() {
        return state;
    }

    public static String status() {
        return status;
    }


    public static double progress() {
        return progress;
    }


    public static List<Caption> drain() {
        List<Caption> out = new ArrayList<>();
        Caption next;
        while ((next = RESULTS.poll()) != null) {
            out.add(next);
        }
        return out;
    }


    public static Path modelsDir() {
        return com.moulberry.flashback.Flashback.getDataDirectory()
            .resolve("fbactors").resolve("models").resolve("whisper");
    }


    public static synchronized void refresh() {
        if (busy) {
            return;
        }
        boolean installed = resolveBinary() != null && Files.isRegularFile(modelPath());
        if (installed && state == State.MISSING) {
            state = State.READY;
            status = "";
        } else if (!installed && state == State.READY) {
            state = State.MISSING;
            status = "";
        }
    }


    public static synchronized void install() {
        if (busy) {
            return;
        }
        busy = true;
        state = State.DOWNLOADING;
        status = "Downloading transcriber...";
        progress = 0.0;
        Thread thread = new Thread(Transcriber::installJob, "fbactors-whisper-install");
        thread.setDaemon(true);
        thread.start();
    }


    public static synchronized void transcribe(UUID takeId, List<LineSnapshot> lines) {
        if (busy) {
            return;
        }
        List<LineSnapshot> work = new ArrayList<>();
        for (LineSnapshot line : lines) {
            if (line != null && line.samples() != null && line.samples().length >= 4800) {
                work.add(new LineSnapshot(line.tick(), line.samples().clone()));
            }
        }
        if (work.isEmpty()) {
            state = State.DONE;
            status = "Nothing to transcribe.";
            return;
        }
        Path binary = resolveBinary();
        Path model = modelPath();
        if (binary == null || !Files.isRegularFile(model)) {
            state = State.MISSING;
            status = "Transcriber is not installed.";
            return;
        }
        busy = true;
        state = State.WORKING;
        status = "Transcribing 0/" + work.size() + "...";
        progress = 0.0;
        Thread thread = new Thread(() -> transcribeJob(takeId, work, binary, model),
            "fbactors-whisper-run");
        thread.setDaemon(true);
        thread.start();
    }


    public static Path resolveBinary() {
        String custom = FbSettings.settings().whisperBinary;
        if (custom != null && !custom.isBlank()) {
            Path path = Path.of(custom.strip());
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        Path dir = modelsDir();
        if (Files.isDirectory(dir)) {
            Path found = probeDir(dir);
            if (found != null) {
                return found;
            }
        }
        for (String name : new String[] {"whisper-cli", "whisper"}) {
            Path found = probePath(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static Path modelPath() {
        return modelsDir().resolve(MODEL_FILE);
    }

    private static Path probeDir(Path dir) {
        boolean windows = isWindows();
        List<Path> candidates = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(dir, 4)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.startsWith("whisper")) {
                    continue;
                }
                if (windows && !(name.endsWith(".exe") || name.indexOf('.') < 0)) {
                    continue;
                }
                if (!windows && name.indexOf('.') >= 0) {
                    continue;
                }
                candidates.add(path);
            }
        } catch (IOException ignored) {
        }


        for (Path candidate : candidates) {
            if (stem(candidate).equals("whisper-cli")) {
                return candidate;
            }
        }
        for (Path candidate : candidates) {
            if (stem(candidate).equals("whisper")) {
                return candidate;
            }
        }
        for (Path candidate : candidates) {
            String stem = stem(candidate);
            if (!stem.contains("bench") && !stem.contains("server") && !stem.contains("quantize")) {
                return candidate;
            }
        }
        return null;
    }

    private static String stem(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static Path probePath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        boolean windows = isWindows();
        for (String dir : pathEnv.split(java.util.regex.Pattern.quote(
                java.io.File.pathSeparator))) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, windows ? name + ".exe" : name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    private static boolean isArm() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.contains("aarch64") || arch.contains("arm64");
    }

    private static void installJob() {
        try {
            Path dir = modelsDir();
            Files.createDirectories(dir);
            if (resolveBinary() == null) {
                String asset = engineAsset();
                if (asset == null) {
                    fail("No prebuilt engine for this platform. Set a custom whisper path instead.");
                    return;
                }
                status = "Downloading engine...";
                Path archive = dir.resolve(asset.substring(asset.lastIndexOf('/') + 1));
                download(asset, archive, 0.0, 0.15);
                status = "Unpacking engine...";
                if (archive.toString().endsWith(".zip")) {
                    unzip(archive, dir);
                } else {
                    untarGz(archive, dir);
                }
                Files.deleteIfExists(archive);
                Path binary = probeDir(dir);
                if (binary == null) {
                    fail("Engine download unpacked with no binary inside.");
                    return;
                }
                if (!isWindows()) {
                    binary.toFile().setExecutable(true);
                }
            }
            if (!Files.isRegularFile(modelPath())) {
                status = "Downloading speech model (75 MB)...";
                download(MODEL_URL, modelPath(), 0.15, 1.0);
            }
            if (resolveBinary() != null && Files.isRegularFile(modelPath())) {
                state = State.READY;
                status = "Transcriber ready.";
                progress = 1.0;
            } else {
                fail("Install finished but files are missing.");
            }
        } catch (Exception e) {
            fail("Install failed: " + shortMessage(e));
        } finally {
            busy = false;
        }
    }


    private static String engineAsset() {
        if (isWindows()) {
            return isArm() ? ENGINE_BASE + "whisper-bin-win-cpu-arm64.zip"
                : ENGINE_BASE + "whisper-bin-x64.zip";
        }
        if (isMac()) {
            return null;
        }
        return isArm() ? ENGINE_BASE + "whisper-bin-ubuntu-arm64.tar.gz"
            : ENGINE_BASE + "whisper-bin-ubuntu-x64.tar.gz";
    }

    private static void transcribeJob(UUID takeId, List<LineSnapshot> lines, Path binary,
                                      Path model) {
        pdx.dev.fbactors.FlashbackActors.LOGGER.info(
            "Transcribing take {} ({} lines) with {} and {}", takeId, lines.size(), binary,
            model.getFileName());
        try {
            Path tmp = modelsDir().resolve("tmp");
            Files.createDirectories(tmp);
            status = "Transcribing take...";
            progress = 0.0;
            short[] stream = buildStream16k(lines);
            if (stream.length < 1600) {
                state = State.DONE;
                status = "Nothing to transcribe.";
                progress = 1.0;
                return;
            }
            List<Segment> segments = runTake(binary, model, tmp, stream);
            java.util.Map<Integer, String> captions = mapCaptions(lines, segments);
            int captioned = 0;
            for (LineSnapshot line : lines) {
                String text = captions.get(line.tick());
                if (text != null && !text.isBlank()) {
                    captioned++;
                }
                RESULTS.add(new Caption(takeId, line.tick(), text));
            }
            state = State.DONE;
            if (captioned == 0) {
                status = "No speech recognised.";
            } else {
                status = "Transcribed " + captioned + " of " + lines.size() + " lines.";
            }
            pdx.dev.fbactors.FlashbackActors.LOGGER.info(
                "Transcribed take {}: {} of {} lines captioned", takeId, captioned, lines.size());
            progress = 1.0;
        } catch (EngineException e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Whisper engine failed: {}",
                e.getMessage());
            fail("Engine failed. See the game log.");
        } catch (Exception e) {
            fail("Transcription failed: " + shortMessage(e));
        } finally {
            busy = false;
        }
    }


    public record Segment(float startSec, float endSec, String text) {
    }


    static short[] buildStream16k(List<LineSnapshot> lines) {
        List<short[]> downs = new ArrayList<>(lines.size());
        List<Integer> offsets = new ArrayList<>(lines.size());
        int total = 0;
        for (LineSnapshot line : lines) {
            short[] down = VoiceBuffer.resample16k(line.samples());
            int offset = Math.max(0, line.tick()) * 320;
            downs.add(down);
            offsets.add(offset);
            total = Math.max(total, offset + down.length);
        }
        short[] stream = new short[total];
        for (int i = 0; i < downs.size(); i++) {
            System.arraycopy(downs.get(i), 0, stream, offsets.get(i), downs.get(i).length);
        }
        return stream;
    }

    private static List<Segment> runTake(Path binary, Path model, Path tmp, short[] stream)
            throws IOException, InterruptedException {
        Path wav = tmp.resolve("take-" + System.nanoTime() + ".wav");
        Path outBase = tmp.resolve("take-" + System.nanoTime() + "-out");
        try {
            Files.write(wav, wavBytes(stream));
            List<String> command = new ArrayList<>();
            command.add(binary.toString());
            command.add("-m");
            command.add(model.toString());
            command.add("-f");
            command.add(wav.toString());
            command.add("-of");
            command.add(outBase.toString());
            command.add("--output-srt");
            command.add("-l");
            command.add("en");
            command.add("-t");
            command.add("4");
            command.add("--no-prints");
            Process process = new ProcessBuilder(command)
                .directory(binary.getParent().toFile())
                .redirectErrorStream(true)
                .start();
            String engineLog = readAll(process.getInputStream());
            boolean finished =
                process.waitFor(TAKE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new EngineException("timed out after " + TAKE_TIMEOUT_SECONDS + "s");
            }
            if (process.exitValue() != 0) {
                throw new EngineException("exit " + process.exitValue() + ": "
                    + tail(engineLog.strip(), 300));
            }
            Path srt = Path.of(outBase + ".srt");
            if (!Files.isRegularFile(srt)) {
                throw new EngineException("no transcript file written");
            }
            return parseSrt(Files.readString(srt, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(wav);
            Files.deleteIfExists(Path.of(outBase + ".srt"));
        }
    }


    static List<Segment> parseSrt(String raw) {
        List<Segment> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String block : raw.split("\r?\n\\s*\r?\n")) {
            String[] rows = block.split("\r?\n");
            Float start = null;
            Float end = null;
            StringBuilder text = new StringBuilder();
            for (String row : rows) {
                String trimmed = row.strip();
                if (trimmed.isEmpty() || trimmed.matches("\\d+")) {
                    continue;
                }
                float[] times = parseSrtTimes(trimmed);
                if (times != null) {
                    start = times[0];
                    end = times[1];
                    continue;
                }
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append(trimmed);
            }
            if (start != null && end != null && end > start) {
                String collapsed = text.toString().replaceAll("\\s+", " ").strip();
                if (!collapsed.isEmpty()) {
                    out.add(new Segment(start, end, collapsed));
                }
            }
        }
        return out;
    }

    private static float[] parseSrtTimes(String row) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "(\\d+):(\\d\\d):(\\d\\d)[,.](\\d+)\\s+-->\\s+(\\d+):(\\d\\d):(\\d\\d)[,.](\\d+)")
            .matcher(row);
        if (!matcher.find()) {
            return null;
        }
        return new float[] {toSeconds(matcher, 1), toSeconds(matcher, 5)};
    }

    private static float toSeconds(java.util.regex.Matcher matcher, int group) {
        float hours = Float.parseFloat(matcher.group(group));
        float minutes = Float.parseFloat(matcher.group(group + 1));
        float seconds = Float.parseFloat(matcher.group(group + 2));
        float millis = Float.parseFloat("0." + matcher.group(group + 3));
        return hours * 3600.0f + minutes * 60.0f + seconds + millis;
    }


    static java.util.Map<Integer, String> mapCaptions(List<LineSnapshot> lines,
                                                      List<Segment> segments) {
        java.util.Map<Integer, String> out = new java.util.HashMap<>();
        for (LineSnapshot line : lines) {
            int lineStart = Math.max(0, line.tick());
            int lineEnd = lineStart + Math.max(1,
                (int) Math.ceil(line.samples().length / 960.0));
            StringBuilder text = new StringBuilder();
            for (Segment segment : segments) {
                int segStart = (int) Math.floor(segment.startSec() * 50.0f);
                int segEnd = (int) Math.ceil(segment.endSec() * 50.0f);
                if (segStart < lineEnd && segEnd > lineStart) {
                    if (!text.isEmpty()) {
                        text.append(' ');
                    }
                    text.append(segment.text());
                }
            }
            String joined = text.toString().replaceAll("\\s+", " ").strip();
            out.put(line.tick(), joined.length() > MAX_CAPTION
                ? joined.substring(0, MAX_CAPTION) : (joined.isEmpty() ? null : joined));
        }
        return out;
    }


    static byte[] wavBytes(short[] samples) {
        int data = samples.length * 2;
        byte[] out = new byte[44 + data];
        writeAscii(out, 0, "RIFF");
        writeInt(out, 4, 36 + data);
        writeAscii(out, 8, "WAVE");
        writeAscii(out, 12, "fmt ");
        writeInt(out, 16, 16);
        writeShort(out, 20, (short) 1);
        writeShort(out, 22, (short) 1);
        writeInt(out, 24, 16000);
        writeInt(out, 28, 16000 * 2);
        writeShort(out, 32, (short) 2);
        writeShort(out, 34, (short) 16);
        writeAscii(out, 36, "data");
        writeInt(out, 40, data);
        for (int i = 0; i < samples.length; i++) {
            writeShort(out, 44 + i * 2, samples[i]);
        }
        return out;
    }

    private static void writeAscii(byte[] out, int at, String text) {
        for (int i = 0; i < text.length(); i++) {
            out[at + i] = (byte) text.charAt(i);
        }
    }

    private static void writeInt(byte[] out, int at, int value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
        out[at + 2] = (byte) (value >> 16);
        out[at + 3] = (byte) (value >> 24);
    }

    private static void writeShort(byte[] out, int at, short value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
    }


    static String cleanTranscript(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (String line : raw.split("\n")) {
            String stripped = line.replaceAll("\\[.*?\\]", " ").strip();
            if (stripped.isEmpty() || stripped.contains("-->")) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(stripped);
        }
        String collapsed = text.toString().replaceAll("\\s+", " ").strip();
        if (collapsed.isEmpty()) {
            return null;
        }
        return collapsed.length() <= MAX_CAPTION ? collapsed : collapsed.substring(0, MAX_CAPTION);
    }

    private static void download(String url, Path destination, double from, double to)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(30))
            .GET()
            .build();
        HttpResponse<InputStream> response =
            client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("download answered " + response.statusCode());
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        Path partial = destination.resolveSibling(destination.getFileName() + ".part");
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(partial)) {
            byte[] buffer = new byte[65536];
            long done = 0L;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                done += read;
                if (total > 0) {
                    progress = from + (to - from) * (done / (double) total);
                }
                status = downloadStatus(destination, done, total);
            }
        }
        Files.move(partial, destination,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static String downloadStatus(Path destination, long done, long total) {
        String name = destination.getFileName().toString();
        if (total > 0) {
            return "Downloading " + name + " " + done / 1048576 + "/"
                + total / 1048576 + " MB...";
        }
        return "Downloading " + name + " " + done / 1048576 + " MB...";
    }

    private static void unzip(Path archive, Path destination) throws IOException {
        try (InputStream fileIn = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(fileIn)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("archive entry escapes its folder");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }


    private static void untarGz(Path archive, Path destination) throws IOException {
        try (InputStream fileIn = Files.newInputStream(archive);
             GZIPInputStream gzip = new GZIPInputStream(fileIn)) {
            byte[] header = new byte[512];
            String pendingLongName = null;
            while (readFully(gzip, header)) {
                if (isZeroBlock(header)) {
                    break;
                }
                String name = readString(header, 0, 100);
                long size = readOctal(header, 124, 12);
                char type = (char) header[156];
                String prefix = readString(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
                if (type == 'L') {
                    byte[] nameBytes = new byte[(int) size];
                    readFully(gzip, nameBytes);
                    skipPadding(gzip, size);
                    pendingLongName = new String(nameBytes, StandardCharsets.UTF_8).strip();
                    continue;
                }
                if (pendingLongName != null) {
                    name = pendingLongName;
                    pendingLongName = null;
                }
                Path target = destination.resolve(name).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("archive entry escapes its folder");
                }
                if (type == '5') {
                    Files.createDirectories(target);
                    continue;
                }
                if (type == 'x' || type == 'g') {
                    skipFully(gzip, size);
                    skipPadding(gzip, size);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    copyExactly(gzip, out, size);
                }
                skipPadding(gzip, size);
            }
        }
    }

    private static boolean readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                return offset > 0;
            }
            offset += read;
        }
        return true;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readString(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long readOctal(byte[] header, int offset, int length) {
        String text = readString(header, offset, length).strip();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text, 8);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long padding = (512L - (size % 512L)) % 512L;
        skipFully(in, padding);
    }

    private static void skipFully(InputStream in, long count) throws IOException {
        long left = count;
        byte[] buffer = new byte[8192];
        while (left > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (read < 0) {
                throw new IOException("archive ends early");
            }
            left -= read;
        }
    }

    private static void copyExactly(InputStream in, OutputStream out, long count)
            throws IOException {
        long left = count;
        byte[] buffer = new byte[8192];
        while (left > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (read < 0) {
                throw new IOException("archive ends early");
            }
            out.write(buffer, 0, read);
            left -= read;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
            if (out.size() > 65536) {
                break;
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String tail(String text, int length) {
        if (text == null) {
            return "";
        }
        return text.length() <= length ? text : text.substring(text.length() - length);
    }

    private static String shortMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() <= 120 ? message : message.substring(0, 120);
    }


    private static final class EngineException extends RuntimeException {
        EngineException(String message) {
            super(message);
        }
    }

    private static void fail(String message) {
        state = State.ERROR;
        status = message;
    }
}

