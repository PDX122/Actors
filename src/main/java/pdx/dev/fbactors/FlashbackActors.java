package pdx.dev.fbactors;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorManager;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.command.ActorCommands;
import pdx.dev.fbactors.record.TakeRecorder;
import pdx.dev.fbactors.take.ActorEvent;
import pdx.dev.fbactors.take.ActorHit;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pdx.dev.fbactors.actor.ActorClonePlayer;
import pdx.dev.fbactors.actor.InputSim;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.TakeStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.compat.Compat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

public class FlashbackActors implements ClientModInitializer {

    public static final String MOD_ID = "fbactors";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Handout build tag, bumped per rebuilt jar so logs identify the exact
     * binary under test. This is NOT the mod version (stays 0.11.0); it only
     * appears in the startup log line below.
     */
    public static final String BUILD_TAG = "26.3-r5";

    public static final ActorManager ACTORS = new ActorManager();
    public static final TakeRecorder RECORDER = new TakeRecorder();

    private static UUID loadedReplayId = null;

    private static boolean reachedEnd = false;

    private static final int END_WARNING_TICKS = 5 * 20;

    private static int lastEndWarningSecond = -1;

    @Override
    public void onInitializeClient() {
        ActorCommands.register();


        pdx.dev.fbactors.emote.Emotes.install();
        pdx.dev.fbactors.gear.Gear.install();
        pdx.dev.fbactors.morph.Morph.install();
        pdx.dev.fbactors.skin.Skins.install();


        pdx.dev.fbactors.timer.TakeTimer.install();
        pdx.dev.fbactors.input.Hotkey.install();
        pdx.dev.fbactors.voice.VoiceMeterOverlay.install();
        pdx.dev.fbactors.subtitle.SubtitleOverlay.install();

        ServerTickEvents.START_SERVER_TICK.register(FlashbackActors::onServerTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {


            ActorMode.openVoicePromptIfPending();


            int tick = currentReplayTick();
            if (tick >= 0) {
                pdx.dev.fbactors.voice.VoicePlayer.drain(tick);
            }
        });


        PlayerBlockBreakEvents.BEFORE.register(
            (world, player, pos, state, blockEntity) -> ActorMode.allowsWorldEdits());
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
            ActorMode.allowsWorldEdits() ? InteractionResult.PASS : InteractionResult.FAIL);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !pdx.dev.fbactors.settings.FbSettings.combatEnabled()) {
                return InteractionResult.PASS;
            }


            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                onAttack(entity.getUUID(), serverPlayer);
            }
            return InteractionResult.PASS;
        });

        LOGGER.info("Flashback Actors ready [build {}]", BUILD_TAG);
        if (pdx.dev.fbactors.debug.DragDiag.ENABLED) {
            LOGGER.info("[FBACTORS-DIAG] drag-drop tracing build: drag lifecycle + guard trips will be logged");
        }
        pdx.dev.fbactors.util.Watermark.log();
    }

    private static void maybeAutoImportBundle(UUID replayId) {
        java.nio.file.Path origin;
        try {
            origin = pdx.dev.fbactors.take.TakeBundle.findReplaySource(replayId);
        } catch (Throwable t) {
            return;
        }
        java.nio.file.Path bundle = pdx.dev.fbactors.take.TakeBundle.findBundleFor(origin);
        if (bundle == null) {
            return;
        }
        try {
            pdx.dev.fbactors.take.TakeBundle.Bundle parsed =
                pdx.dev.fbactors.take.TakeBundle.readBundle(bundle);
            if (parsed.takes().isEmpty()) {
                return;
            }
            pdx.dev.fbactors.take.TakeBundle.Summary summary =
                pdx.dev.fbactors.take.TakeBundle.importInto(parsed, replayId);
            LOGGER.info("Auto-imported {} actor(s) from {}",
                summary.takes(), bundle.getFileName());
        } catch (Exception e) {
            LOGGER.warn("Found bundle {} but could not import it", bundle.getFileName(), e);
        }
    }

    private static int lastReplayTick = -1;

    private static int currentReplayTick() {
        return lastReplayTick;
    }


    public static int replayTick() {
        return lastReplayTick;
    }

    private static void onServerTick(MinecraftServer server) {
        if (!(server instanceof ReplayServer replay)) {
            return;
        }
        lastReplayTick = replay.getReplayTick();
        pdx.dev.fbactors.tips.ActorTips.install();

        UUID replayId = replay.getMetadata().replayIdentifier;
        if (!Objects.equals(replayId, loadedReplayId)) {
            ACTORS.clear();
            for (ActorTake take : TakeStore.loadAll(replayId)) {
                ACTORS.add(take);
            }


            if (ACTORS.actors().isEmpty()) {
                maybeAutoImportBundle(replayId);
            }
            loadedReplayId = replayId;
            reachedEnd = false;
            lastEndWarningSecond = -1;
            LOGGER.info("Loaded {} actor(s) for replay {}", ACTORS.actors().size(), replayId);
        }

        int tick = replay.getReplayTick();

        if (ActorMode.isActive()) {
            ServerPlayer viewer = viewerOf(replay);
            if (viewer != null) {
                ActorMode.tick(replay, viewer, tick);
            }
        }

        ACTORS.applyTick(replay, tick);
        warnAboutReplayEnd(replay, tick);
        maybePauseAtEnd(replay, tick);
    }

    private static void warnAboutReplayEnd(ReplayServer replay, int tick) {

        boolean performing = ActorMode.phase() == ActorMode.Phase.RECORDING
            || ActorMode.phase() == ActorMode.Phase.SETUP;
        int total = replay.getTotalReplayTicks();
        int remaining = total - tick;

        if (!performing || total <= 0 || replay.replayPaused || Flashback.isExporting()
            || remaining <= 0 || remaining > END_WARNING_TICKS) {
            clearEndWarning();
            return;
        }

        int second = (remaining + 19) / 20;
        if (second == lastEndWarningSecond) {
            return;
        }
        lastEndWarningSecond = second;

        ServerPlayer viewer = viewerOf(replay);
        if (viewer == null) {
            return;
        }

        viewer.connection.send(new ClientboundSetTitlesAnimationPacket(0, 4, 4));
        viewer.connection.send(new ClientboundSetTitleTextPacket(Component.literal(String.valueOf(second))));
        viewer.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal(second == 1 ? "last second of footage" : "seconds of footage left")));
        viewer.level().playSound(null, viewer.getX(), viewer.getY(), viewer.getZ(),
            SoundEvents.NOTE_BLOCK_HAT, SoundSource.MASTER, 0.7f, 1.2f);
    }

    private static void clearEndWarning() {
        if (lastEndWarningSecond < 0) {
            return;
        }
        lastEndWarningSecond = -1;
        ReplayServer replay = Flashback.getReplayServer();
        ServerPlayer viewer = replay == null ? null : viewerOf(replay);
        if (viewer != null) {
            viewer.connection.send(new ClientboundClearTitlesPacket(true));
        }
    }

    private static void maybePauseAtEnd(ReplayServer replay, int tick) {
        int total = replay.getTotalReplayTicks();
        if (total <= 0 || replay.replayPaused || ActorMode.isActive()
            || Flashback.isExporting()) {
            reachedEnd = false;
            return;
        }
        if (tick < total || reachedEnd) {
            return;
        }
        reachedEnd = true;

        replay.replayPaused = true;
        LOGGER.info("Replay finished at tick {}, pausing", tick);
    }


    @Nullable
    public static UUID loadedReplayIdOrNull() {
        return loadedReplayId;
    }

    public static void save(ActorTake take) {
        if (loadedReplayId != null) {
            TakeStore.save(loadedReplayId, take);
        }
    }

    public static Actor duplicateActor(Actor source) {
        String name = source.take.name == null ? "Actor" : source.take.name;
        ActorTake copy = source.take.copyAs(UUID.randomUUID(), shortened(name + " copy"));
        Actor actor = ACTORS.add(copy);
        save(copy);
        LOGGER.info("Duplicated actor '{}' as '{}'", name, copy.name);
        return actor;
    }

    private static String shortened(String name) {
        return name.length() <= 32 ? name : name.substring(0, 32);
    }

    public static void removeActor(Actor actor) {
        ACTORS.remove(actor);
        if (loadedReplayId != null) {
            TakeStore.delete(loadedReplayId, actor.take.id);
        }
        LOGGER.info("Removed actor '{}'", actor.take.name);
    }


    private static final ThreadLocal<Boolean> STRIKING = ThreadLocal.withInitial(() -> false);


    public static void onAttack(UUID targetId, ServerPlayer viewer) {
        if (!pdx.dev.fbactors.settings.FbSettings.combatEnabled()) {
            return;
        }
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null || !(viewer.level() instanceof ServerLevel level)) {
            return;
        }
        int tick = replay.getReplayTick();

        replay.execute(() -> {
            if (ActorMode.phase() == ActorMode.Phase.RECORDING) {
                RECORDER.recordAttack(targetId, tick);
            }
            Actor target = ACTORS.byUuid(targetId);
            if (target != null) {
                noteViewerStruckActor(target, level, tick, viewer);
            }
        });
    }


    private static void noteViewerStruckActor(Actor actor, ServerLevel level, int replayTick,
            ServerPlayer viewer) {
        ActorClonePlayer clone = actor.clone();
        if (clone == null || clone.level() != level) {
            return;
        }
        if (STRIKING.get()) {
            return;
        }
        STRIKING.set(Boolean.TRUE);
        try {
            actor.sim().markStruck(actor.takeTickFor(replayTick),
                viewer.getAttackStrengthScale(0.5f), viewer, clone.getDeltaMovement());
        } finally {
            STRIKING.set(Boolean.FALSE);
        }
    }

    public static void finishTake(ReplayServer replay, ServerPlayer viewer) {
        ActorTake take = ActorMode.finish(replay, viewer);
        if (take == null) {
            return;
        }
        if (loadedReplayId != null) {
            TakeStore.save(loadedReplayId, take);
        }
        ACTORS.add(take);
        TakeStore.clearDirty(take.id);
        replay.goToReplayTick(take.startTick);
        LOGGER.info("Saved actor take '{}' ({} ticks from {}, {} voice line(s))",
            take.name, take.length(), take.startTick, take.voices.size());
    }

    @Nullable
    public static ServerPlayer viewerOf(ReplayServer replay) {
        for (ReplayPlayer viewer : replay.getReplayViewers()) {
            return viewer;
        }
        return null;
    }


    public static java.nio.file.Path fbSettingsPath() {
        return com.moulberry.flashback.Flashback.getDataDirectory()
            .resolve("fbactors").resolve("settings.json");
    }
}

