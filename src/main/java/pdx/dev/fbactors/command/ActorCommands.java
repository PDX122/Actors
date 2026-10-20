package pdx.dev.fbactors.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.settings.FbSettings;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.timeline.EmptyTimeline;
import pdx.dev.fbactors.ui.TimelineForm;

import java.util.function.Consumer;


public final class ActorCommands {

    private static final String[] DELAY_SUGGESTIONS = {"3s", "5s", "10s", "30s", "1m"};

    private static final int MAX_DELAY_SECONDS = 24 * 60 * 60;


    @Nullable
    private static volatile String pendingTakeName = null;

    private ActorCommands() {
    }


    public static void register() {
        pdx.dev.fbactors.selftest.SelfTest.install();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> actor = Compat.literal("actor")
                .executes(ctx -> {
                    onServer(ctx.getSource(), (replay) -> {
                        ServerPlayer viewer = FlashbackActors.viewerOf(replay);
                        if (viewer != null) {
                            ActorMode.enterSetup(replay, viewer);
                        }
                    });
                    return 1;
                });

            TakeCommands.register(dispatcher, actor);
            EmoteCommands.register(dispatcher, actor);
            GearCommands.register(dispatcher, actor);
            MorphCommands.register(dispatcher, actor);
            SkinCommands.register(dispatcher, actor);
            TimerCommands.register(dispatcher, actor);
            SelfTestCommands.register(dispatcher, actor);


            actor.then(startCommand());
            actor.then(timelineCommand());
            actor.then(stopCommand());

            dispatcher.register(actor);
            dispatcher.register(startCommand());
            dispatcher.register(timelineCommand());
            dispatcher.register(stopCommand());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> applyPendingTakeName());
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> startCommand() {
        return Compat.literal("start")
            .executes(ctx -> {
                start(ctx.getSource(), ActorMode.countdownDelayOr(
                    FbSettings.countdownSeconds()));
                return 1;
            })
            .then(Compat.argument("delay", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (String suggestion : DELAY_SUGGESTIONS) {
                        builder.suggest(suggestion);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    String raw = ActorArgs.string(ctx, "delay");
                    int seconds = parseSeconds(raw);
                    if (seconds < 0) {
                        durationError(ctx.getSource(), raw, "5", "5s", "5m", "5h");
                        return 0;
                    }
                    start(ctx.getSource(), seconds);
                    return 1;
                })
                .then(Compat.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String raw = ActorArgs.string(ctx, "delay");
                        int seconds = parseSeconds(raw);
                        if (seconds < 0) {
                            durationError(ctx.getSource(), raw, "5", "5s", "5m", "5h");
                            return 0;
                        }
                        String name = ActorArgs.string(ctx, "name").trim();
                        start(ctx.getSource(), seconds, name.isEmpty() ? null : name);
                        return 1;
                    })));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> timelineCommand() {
        return Compat.literal("timeline")
            .executes(ctx -> {
                createTimeline(ctx.getSource(), 60);
                return 1;
            })
            .then(Compat.argument("length", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (String suggestion : DELAY_SUGGESTIONS) {
                        builder.suggest(suggestion);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    String raw = ActorArgs.string(ctx, "length");
                    int seconds = parseSeconds(raw);
                    if (seconds < 0) {
                        durationError(ctx.getSource(), raw, "30", "30s", "1m", "1h");
                        return 0;
                    }
                    createTimeline(ctx.getSource(), seconds);
                    return 1;
                }));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> stopCommand() {
        return Compat.literal("stop")
            .executes(ctx -> {
                onServer(ctx.getSource(), (replay) -> {
                    ServerPlayer viewer = FlashbackActors.viewerOf(replay);
                    if (viewer != null) {
                        FlashbackActors.finishTake(replay, viewer);
                    }
                });
                return 1;
            });
    }


    private static void durationError(FabricClientCommandSource source, String raw,
                                      String... examples) {
        StringBuilder out = new StringBuilder();
        out.append("Could not read a duration from \"").append(raw.trim())
            .append("\". Try ");
        for (int i = 0; i < examples.length; i++) {
            if (i > 0) {
                out.append(i == examples.length - 1 ? " or " : ", ");
            }
            out.append(examples[i]);
        }
        out.append('.');
        source.sendFeedback(Component.literal(out.toString()));
    }

    private static void createTimeline(FabricClientCommandSource source, int seconds) {
        int clamped = EmptyTimeline.clampSeconds(seconds);
        if (clamped != seconds) {
            source.sendFeedback(Component.literal(
                "Clamped to " + clamped + "s (allowed range is "
                    + EmptyTimeline.MIN_SECONDS + "-" + EmptyTimeline.MAX_SECONDS + ")."));
        }
        EmptyTimeline.Result result =
            EmptyTimeline.generateAndOpen("Timeline " + clamped + "s", clamped);
        source.sendFeedback(Component.literal(result.message()));
        TimelineForm.notify(result.message(), result.ok());
    }


    private static void start(FabricClientCommandSource source, int seconds, @Nullable String name) {
        if (name != null && !name.isBlank()) {
            pendingTakeName = name.trim();
        }
        start(source, seconds);
    }


    private static void start(FabricClientCommandSource source, int seconds) {
        if (ActorMode.phase() == ActorMode.Phase.SETUP) {
            pendingTakeName = null;
            source.sendFeedback(Component.literal(
                "Confirm the actor setup menu first, then /start when you are geared up."));
            return;
        }
        if (ActorMode.phase() != ActorMode.Phase.PREP) {
            pendingTakeName = null;
            source.sendFeedback(Component.literal(
                "Not in a take setup. Start an actor take with /actor, or use the rows in the timeline."));
            return;
        }
        onServer(source, (replay) -> ActorMode.beginCountdown(seconds));
        source.sendFeedback(Component.literal(
            pendingTakeName == null
                ? "Recording in " + seconds + "s…"
                : "Recording in " + seconds + "s as \"" + pendingTakeName + "\"…"));
    }


    private static void applyPendingTakeName() {
        String wanted = pendingTakeName;
        if (wanted == null) {
            return;
        }


        if (ActorMode.phase() == ActorMode.Phase.IDLE) {
            pendingTakeName = null;
            return;
        }
        if (ActorMode.phase() != ActorMode.Phase.RECORDING) {
            return;
        }
        ActorTake take = FlashbackActors.RECORDER.current();
        if (take == null) {
            return;
        }
        take.name = wanted;
        pendingTakeName = null;
    }

    private static void onServer(FabricClientCommandSource source, Consumer<ReplayServer> action) {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            source.sendFeedback(Component.literal("Not in a replay."));
            return;
        }
        replay.execute(() -> action.accept(replay));
    }


    public static int parseSeconds(String raw) {
        String text = raw.trim().toLowerCase();
        if (text.isEmpty()) {
            return -1;
        }

        int multiplier = 1;
        char last = text.charAt(text.length() - 1);
        if (last == 's' || last == 'm' || last == 'h') {
            multiplier = switch (last) {
                case 'm' -> 60;
                case 'h' -> 3600;
                default -> 1;
            };
            text = text.substring(0, text.length() - 1);
        }

        try {
            long value = Long.parseLong(text.trim());
            if (value < 0) {
                return -1;
            }
            long seconds = value * multiplier;
            return (int) Math.min(seconds, MAX_DELAY_SECONDS);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
