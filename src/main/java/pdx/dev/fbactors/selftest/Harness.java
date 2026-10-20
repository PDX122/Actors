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
    private static long lastOpenAttempt;
    private static long startTime;

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
        maybeStartRun(replay);
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
        if (client == null || client.level != null) {
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
        if (tick < SETTLE_TICKS) {
            return;
        }
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
            fileVerdict("PASS", status);
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

