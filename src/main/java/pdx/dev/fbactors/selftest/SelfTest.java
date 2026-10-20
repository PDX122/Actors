package pdx.dev.fbactors.selftest;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.settings.FbSettings;


public final class SelfTest {


    static final int FRAMES = 200;


    static final int TAIL_TICKS = 20;


    static final int MAX_TICKS = 1200;


    private static final Set<UUID> AUTO_RAN = new HashSet<>();


    @Nullable
    private static SelfTestRun active;


    private static boolean pendingStart;


    private static String lastStatus = "Not run yet.";

    private SelfTest() {
    }


    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(SelfTest::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> Harness.clientTick());
    }


    public static String requestStart() {
        if (active != null) {
            return "Self-test already running.";
        }
        pendingStart = true;
        return "Self-test queued; it starts on the next tick.";
    }


    public static String requestStop() {
        if (active == null) {
            pendingStart = false;
            return "No self-test running.";
        }
        active.abort("stopped by user");
        return "Self-test stopped.";
    }


    public static String status() {
        if (active != null) {
            return active.status();
        }
        return lastStatus;
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        if (!(server instanceof ReplayServer replay)) {
            return;
        }
        if (pendingStart) {
            pendingStart = false;
            startNow(replay);
        }
        if (active != null) {
            if (!active.replayMatches(replay)) {
                active.abort("replay closed mid-run");
                finishActive(replay);
                return;
            }
            active.tick(replay);
            if (active.finished()) {
                finishActive(replay);
            }
            return;
        }
        maybeAutoRun(replay);
    }


    private static void startNow(ReplayServer replay) {
        if (active != null) {
            return;
        }
        if (ActorMode.phase() != ActorMode.Phase.IDLE) {
            lastStatus = "Self-test refused: finish recording first.";
            return;
        }
        if (Flashback.isExporting()) {
            lastStatus = "Self-test refused: an export is running.";
            return;
        }
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer viewer : replay.getReplayViewers()) {
            viewers.add(viewer);
        }
        if (viewers.isEmpty()) {
            lastStatus = "Self-test refused: no viewer in this replay.";
            return;
        }
        SelfTestRun run = SelfTestRun.begin(replay, viewers.get(0));
        if (run == null) {
            lastStatus = "Self-test could not start here.";
            return;
        }
        active = run;
        lastStatus = "Self-test running...";
    }

    private static void maybeAutoRun(ReplayServer replay) {
        if (!FbSettings.selfTestOnLoad()) {
            return;
        }
        UUID id = replayIdOf(replay);
        if (id == null || AUTO_RAN.contains(id)) {
            return;
        }
        if (replay.getReplayTick() < 60) {
            return;
        }
        AUTO_RAN.add(id);
        startNow(replay);
    }

    @Nullable
    static UUID replayIdOf(ReplayServer replay) {
        try {
            return replay.getMetadata().replayIdentifier;
        } catch (Exception e) {
            return null;
        }
    }

    private static void finishActive(ReplayServer replay) {
        SelfTestRun run = active;
        active = null;
        if (run == null) {
            return;
        }
        String report = run.report();
        lastStatus = run.summary();
        tell(replay, report);
        appendReportFile(replay, report);
    }


    static void tell(ReplayServer replay, String report) {
        for (String line : report.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            Component text = Component.literal(line);
            for (ServerPlayer viewer : replay.getReplayViewers()) {
                viewer.sendSystemMessage(text, false);
            }
        }
    }


    static void appendReportFile(ReplayServer replay, String report) {
        Path file = Flashback.getDataDirectory().resolve("fbactors")
            .resolve("selftest-report.txt");
        String header = "=== self-test "
            + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            + " replay=" + replayIdOf(replay) + " ===\n";
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, header + report + "\n",
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            FlashbackActors.LOGGER.error("Failed to write the self-test report", e);
        }
    }
}

