package pdx.dev.fbactors.actor;

import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.take.ActorTake;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

public final class ActorMode {


    public enum Phase { IDLE, SETUP, PREP, COUNTDOWN, RECORDING }

    private static volatile Phase phase = Phase.IDLE;
    private static int countdownTicks = 0;
    private static int lastShownSecond = -1;


    private static volatile int countdownOverride = -1;


    private static volatile boolean pendingVoicePrompt = false;


    private static volatile boolean pendingConfirm = false;

    private ActorMode() {
    }

    public static Phase phase() {
        return phase;
    }

    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    public static boolean suppressesCamera() {
        return phase != Phase.IDLE;
    }

    public static boolean allowsWorldEdits() {
        return phase == Phase.IDLE || phase == Phase.RECORDING;
    }


    public static void enterSetup(ReplayServer replay, ServerPlayer viewer) {
        phase = Phase.SETUP;
        pendingVoicePrompt = true;
        actionBar(viewer, Component.literal("Actor setup: choose what you want for your actor"));
        FlashbackActors.LOGGER.info("Actor setup at tick {}", replay.getReplayTick());
    }


    public static void openVoicePromptIfPending() {
        if (!pendingVoicePrompt) {
            return;
        }
        pendingVoicePrompt = false;
        try {
            pdx.dev.fbactors.actor.ActorSetupMenu.open();
        } catch (Throwable t) {
            FlashbackActors.LOGGER.warn("Failed to open the actor setup menu", t);
        }
    }


    public static void confirmSetup() {
        if (phase != Phase.SETUP) {
            return;
        }
        pendingConfirm = true;
    }


    public static void cancelSetup() {
        phase = Phase.IDLE;
        pendingConfirm = false;
        pendingVoicePrompt = false;
        countdownOverride = -1;
    }


    public static void setCountdownDelay(int seconds) {
        countdownOverride = Math.max(1, seconds);
    }


    public static int countdownDelayOr(int fallback) {
        int override = countdownOverride;
        countdownOverride = -1;
        return override > 0 ? override : Math.max(1, fallback);
    }

    public static boolean beginCountdown(int seconds) {
        if (phase != Phase.PREP && phase != Phase.COUNTDOWN) {
            return false;
        }
        countdownTicks = Math.max(1, Math.max(1, seconds) * 20);
        lastShownSecond = -1;
        if (phase == Phase.PREP) {
            phase = Phase.COUNTDOWN;
        }
        return true;
    }


    private static void launchActingScene(ReplayServer replay, ServerPlayer viewer) {
        replay.replayPaused = true;
        detachCamera(viewer);
        viewer.setGameMode(GameType.CREATIVE);
        viewer.getAbilities().flying = false;
        viewer.onUpdateAbilities();


        viewer.setInvisible(false);
    }

    private static void detachCamera(ServerPlayer viewer) {
        viewer.setCamera(null);
        if (viewer instanceof ReplayPlayer replayPlayer) {
            replayPlayer.spectatingUuid = null;
            replayPlayer.spectatingUuidTickCount = 0;
            replayPlayer.forceRespectateTickCount = 0;
        }
    }

    public static void tick(ReplayServer replay, ServerPlayer viewer, int replayTick) {

        if (viewer instanceof ReplayPlayer replayPlayer && replayPlayer.spectatingUuid != null) {
            detachCamera(viewer);
        }

        switch (phase) {
            case SETUP -> {
                if (pendingConfirm) {
                    pendingConfirm = false;
                    launchActingScene(replay, viewer);
                    actionBar(viewer, Component.translatable("fbactors.actor.setup_hint"));
                    phase = Phase.PREP;
                }
            }
            case PREP -> {

            }
            case COUNTDOWN -> {
                countdownTicks--;
                int second = (countdownTicks + 19) / 20;
                if (second != lastShownSecond) {
                    lastShownSecond = second;
                    countdown(viewer, second);
                }
                if (countdownTicks <= 0) {
                    phase = Phase.RECORDING;
                    countdown(viewer, 0);

                    viewer.setGameMode(GameType.SURVIVAL);
                    viewer.setInvisible(false);
                    FlashbackActors.RECORDER.start(viewer, replayTick, nextName());
                    replay.replayPaused = false;
                    FlashbackActors.LOGGER.info("Recording actor take from tick {}", replayTick);
                }
            }
            case RECORDING -> FlashbackActors.RECORDER.sample(viewer, replayTick);
            default -> {
            }
        }
    }

    @Nullable
    public static ActorTake finish(ReplayServer replay, ServerPlayer viewer) {
        ActorTake take = FlashbackActors.RECORDER.stop();
        clearTitles(viewer);

        phase = Phase.IDLE;
        countdownTicks = 0;
        lastShownSecond = -1;
        pendingConfirm = false;
        pendingVoicePrompt = false;
        countdownOverride = -1;
        pdx.dev.fbactors.timer.TakeTimer.disarm();

        replay.replayPaused = true;
        viewer.setGameMode(GameType.SPECTATOR);
        if (take == null) {
            actionBar(viewer, Component.translatable("fbactors.actor.cancelled"));
        } else {
            int seconds = take.length() / 20;
            actionBar(viewer, Component.translatable("fbactors.actor.take_saved",
                String.format("%d:%02d", seconds / 60, seconds % 60)));
        }
        return take;
    }

    private static String nextName() {

        return "Actor_" + (FlashbackActors.ACTORS.actors().size() + 1);
    }

    private static void actionBar(ServerPlayer viewer, Component text) {
        viewer.sendSystemMessage(text, true);
    }

    private static void countdown(ServerPlayer viewer, int second) {
        boolean go = second <= 0;
        viewer.connection.send(new ClientboundSetTitlesAnimationPacket(0, go ? 14 : 16, go ? 8 : 4));
        viewer.connection.send(new ClientboundSetTitleTextPacket(
            go ? Component.translatable("fbactors.actor.countdown_go")
                : Component.literal(String.valueOf(second))));
        viewer.connection.send(new ClientboundSetSubtitleTextPacket(
            go ? Component.literal(nextName())
                : Component.translatable("fbactors.actor.countdown_subtitle")));
        viewer.level().playSound(null, viewer.getX(), viewer.getY(), viewer.getZ(),
            go ? SoundEvents.NOTE_BLOCK_PLING : SoundEvents.NOTE_BLOCK_HAT,
            SoundSource.MASTER, 0.7f, go ? 1.5f : 1.0f);
    }

    private static void clearTitles(ServerPlayer viewer) {
        viewer.connection.send(new ClientboundClearTitlesPacket(true));
    }
}

