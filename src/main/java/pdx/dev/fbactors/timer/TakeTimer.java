package pdx.dev.fbactors.timer;

import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.settings.FbSettings;


public final class TakeTimer {


    private static int startReplayTick = -1;


    private static String lastShownText = null;

    private static boolean installed = false;

    private TakeTimer() {
    }


    public static void disarm() {
        startReplayTick = -1;
        lastShownText = null;
    }


    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(server));
    }


    public static int durationSeconds() {
        return FbSettings.takeTimerSeconds();
    }


    public static void durationSeconds(int seconds) {
        FbSettings.settings().takeTimerSeconds = Math.max(0, seconds);
        FbSettings.settings().save();
    }


    private static void tick(MinecraftServer server) {
        if (!(server instanceof ReplayServer replay)) {
            return;
        }

        if (ActorMode.phase() != ActorMode.Phase.RECORDING || replay.replayPaused) {
            startReplayTick = -1;
            return;
        }

        int budget = durationSeconds();
        if (startReplayTick < 0) {
            startReplayTick = replay.getReplayTick();
            lastShownText = null;
        }

        int elapsed = Math.max(0, replay.getReplayTick() - startReplayTick);

        ServerPlayer viewer = FlashbackActors.viewerOf(replay);
        if (viewer == null) {
            return;
        }

        if (budget <= 0) {


            String text = String.format("%d:%02d", elapsed / 1200, (elapsed / 20) % 60);
            if (!text.equals(lastShownText)) {
                lastShownText = text;
                viewer.sendSystemMessage(
                    Component.translatable("fbactors.timer.elapsed", text), true);
            }
            return;
        }

        int remaining = budget * 20 - elapsed;
        if (remaining <= 0) {
            startReplayTick = -1;
            lastShownText = null;
            viewer.sendSystemMessage(Component.translatable("fbactors.timer.expired"), true);
            FlashbackActors.finishTake(replay, viewer);
            FlashbackActors.LOGGER.info("Take timer expired at tick {}",
                replay.getReplayTick());
        }
    }
}

