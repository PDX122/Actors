package pdx.dev.fbactors.selftest;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import pdx.dev.fbactors.FlashbackActors;

final class Harness {

    private static final int MAX_OPEN_ATTEMPTS = 3;

    private static final long OPEN_COOLDOWN_MILLIS = 15_000L;

    private static final int SETTLE_TICKS = 80;

    private static final long HARNESS_TIMEOUT_MILLIS = 15 * 60_000L;

    private static boolean read;
    private static boolean armed;
    private static String replayPath = "";
    private static String reportPath = "";
    private static boolean requested;
    private static boolean verdictFiled;
    private static int openAttempts;
    private static int readyTicks;
    private static long lastOpenAttempt;
    private static long startTime;
    private static boolean converted;

    private Harness() {
    }

    private static void readEnvOnce() {
        if (read) {
            return;
        }
        read = true;
        startTime = System.currentTimeMillis();
        try {
            String replay = System.getenv("FBACTORS_HARNESS_REPLAY");
            String report = System.getenv("FBACTORS_HARNESS_REPORT");
            replayPath = replay == null ? "" : replay.trim();
            reportPath = report == null ? "" : report.trim();
            armed = !replayPath.isEmpty() || !reportPath.isEmpty();
        } catch (SecurityException e) {
            armed = false;
        }
        if (armed) {
            FlashbackActors.LOGGER.info(
                "Harness armed (replay={}, report={})",
                replayPath.isEmpty() ? "<manual>" : replayPath,
                reportPath.isEmpty() ? "<none>" : reportPath);
        }
    }

    static void clientTick() {
        readEnvOnce();
        if (!armed || verdictFiled) {
            return;
        }
        Minecraft.getInstance().options.pauseOnLostFocus=false;
        if (System.currentTimeMillis() - startTime > HARNESS_TIMEOUT_MILLIS) {
            fileVerdict("ERROR", "harness timeout: no self-test verdict in time");
            return;
        }
        ReplayServer replay;
        try {
            replay = Flashback.getReplayServer();
        } catch (Exception e) {
            return;
        }
        if (replay == null) {
            maybeOpenReplay();
            return;
        }
        if(converted && !requested) replay.replayPaused=false;
        if("1".equals(System.getenv("FBACTORS_VISUAL_FOCUS"))) {
            if(Minecraft.getInstance().player==null || pdx.dev.fbactors.compat.Compat.hasOpenScreen(Minecraft.getInstance())) { readyTicks=0; return; }
            if(++readyTicks<40) return;
            if(!ReleaseExportChecks.tick() || !ReleaseVisualChecks.tick()) return;
            String failure=ReleaseExportChecks.error()==null?ReleaseVisualChecks.error():ReleaseExportChecks.error();
            ReleaseExportChecks.cleanup();
            fileVerdict(failure==null?"PASS":"FAIL",failure==null?"Supplemental enabled-feature visual checks":failure);
            return;
        }
        maybeStartRun(replay);
        if(requested && SelfTest.status().startsWith("Self-test running")) replay.replayPaused=false;
        maybeFileVerdict();
    }

    private static void maybeOpenReplay() {
        if (replayPath.isEmpty() || openAttempts >= MAX_OPEN_ATTEMPTS) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastOpenAttempt < OPEN_COOLDOWN_MILLIS) {
            return;
        }
        Minecraft client;
        try {
            client = Minecraft.getInstance();
        } catch (Exception e) {
            return;
        }
        if (client == null || client.level != null || !client.isGameLoadFinished()) {
            return;
        }
        Path path;
        try {
            path = Path.of(replayPath);
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn("Harness: not a path: {}", replayPath);
            openAttempts = MAX_OPEN_ATTEMPTS;
            return;
        }
        if (!Files.isRegularFile(path)) {
            FlashbackActors.LOGGER.warn("Harness: no replay at {}", replayPath);
            openAttempts = MAX_OPEN_ATTEMPTS;
            return;
        }
        lastOpenAttempt = now;
        openAttempts++;
        try {
            String input=System.getenv("FBACTORS_HARNESS_CONVERT");
            if(!converted && input!=null && !input.isBlank()) {
                converted=true;
                com.google.gson.JsonObject metadata;
                try(var zip=new java.util.zip.ZipFile(path.toFile());
                    var reader=new java.io.InputStreamReader(zip.getInputStream(zip.getEntry("metadata.json")),java.nio.charset.StandardCharsets.UTF_8)) {
                    metadata=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                }
                var result=pdx.dev.fbactors.convert.McprToFlashback.convert(Path.of(input),null,
                    metadata.get("version_string").getAsString(),metadata.get("data_version").getAsInt(),
                    metadata.get("protocol_version").getAsInt(),path.getParent().resolve("converted"),path);
                if(!result.ok()) { fileVerdict("FAIL",result.message()); return; }
                path=result.path(); replayPath=path.toString();
                Files.writeString(Path.of(reportPath).getParent().resolve("conversion.txt"),"Created "+path+"\n");
            }
            Flashback.openReplayWorld(path);
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn("Harness: could not open {} (attempt {}/{})",
                replayPath, openAttempts, MAX_OPEN_ATTEMPTS, e);
        }
    }

    private static void maybeStartRun(ReplayServer replay) {
        if (requested) {
            return;
        }
        int tick;
        try {
            tick = replay.getReplayTick();
        } catch (Exception e) {
            return;
        }
        if(Minecraft.getInstance().player==null || pdx.dev.fbactors.compat.Compat.hasOpenScreen(Minecraft.getInstance())) { readyTicks=0; return; }
        if(converted && !Minecraft.getInstance().level.hasChunkAt(Minecraft.getInstance().player.blockPosition())) { readyTicks=0; return; }
        if (++readyTicks < 40) {
            return;
        }
        replay.replayPaused = false;
        requested = true;
        String answer = SelfTest.requestStart();
        FlashbackActors.LOGGER.info("Harness started the self-test: {}", answer);
    }

    private static void maybeFileVerdict() {
        if (!requested) {
            return;
        }
        String status;
        try {
            status = SelfTest.status();
        } catch (Exception e) {
            return;
        }
        if (status == null || status.startsWith("Self-test queued")
            || status.startsWith("Self-test running")
            || status.equals("Not run yet.")) {
            return;
        }
        if (status.startsWith("Self-test PASS")) {
            if (!ReleaseIntegrationChecks.tick()) return;
            if (!ReleaseExportChecks.tick()) return;
            if (!ReleaseVisualChecks.tick()) return;
            String visualError = ReleaseVisualChecks.error();
            if (ReleaseExportChecks.error() != null) visualError = ReleaseExportChecks.error();
            if (ReleaseIntegrationChecks.error() != null) visualError = ReleaseIntegrationChecks.error();
            ReleaseExportChecks.cleanup();
            fileVerdict(visualError == null ? "PASS" : "FAIL", visualError == null ? status : visualError);
        } else if (status.startsWith("Self-test FAIL")) {
            fileVerdict("FAIL", status);
        } else {
            fileVerdict("ERROR", status);
        }
    }

    private static void fileVerdict(String verdict, String summary) {
        verdictFiled = true;
        FlashbackActors.LOGGER.info("Harness verdict: {} ({})", verdict, summary);
        if (reportPath.isEmpty()) {
            return;
        }
        String body = "VERDICT=" + verdict + "\n"
            + "SUMMARY=" + summary.replace('\n', ' ') + "\n"
            + "TIMESTAMP=" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            + "\n";
        try {
            Path file = Path.of(reportPath);
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, body,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException | RuntimeException e) {
            FlashbackActors.LOGGER.error("Harness could not write its verdict file", e);
        }
    }
}
