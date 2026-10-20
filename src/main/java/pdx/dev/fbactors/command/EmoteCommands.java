package pdx.dev.fbactors.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.emote.Emote;
import pdx.dev.fbactors.emote.EmoteLibrary;
import pdx.dev.fbactors.emote.EmotePose;
import pdx.dev.fbactors.emote.EmoteTrack;
import pdx.dev.fbactors.emote.Emotes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public final class EmoteCommands {


    private static final float DEFAULT_BLEND_TICKS = 4.0f;


    private static final int MAX_LISTED = 20;

    private EmoteCommands() {
    }


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                               LiteralArgumentBuilder<FabricClientCommandSource> parent) {
        LiteralArgumentBuilder<FabricClientCommandSource> emote = Compat.literal("emote");
        emote.then(list());
        emote.then(apply());
        emote.then(clear());
        emote.then(at());
        parent.then(emote);
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> list() {
        return Compat.literal("list")
            .executes(ctx -> printEmotes(ctx.getSource(), null))
            .then(Compat.argument("query", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (String id : ids()) {
                        builder.suggest(id);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> printEmotes(ctx.getSource(),
                    ActorArgs.string(ctx, "query"))));
    }


    private static List<String> ids() {
        List<Emote> all = Emotes.library().all();
        List<String> out = new ArrayList<>(all.size());
        for (Emote emote : all) {
            out.add(emote.id());
        }
        return out;
    }

    private static int printEmotes(FabricClientCommandSource source, String query) {
        EmoteLibrary library = Emotes.library();
        List<Emote> matches = ranked(library, query);
        if (matches.isEmpty()) {
            ActorArgs.fail(source, "No emote matches \"" + query.trim() + "\". "
                + "The library holds " + library.size() + " emote(s).");
            return 0;
        }
        ActorArgs.ok(source, matches.size() + " emote(s)"
            + (query == null || query.isBlank() ? ":" : " matching \"" + query.trim() + "\":"));
        for (int i = 0; i < Math.min(matches.size(), MAX_LISTED); i++) {
            ActorArgs.ok(source, "  " + describe(matches.get(i)));
        }
        if (matches.size() > MAX_LISTED) {
            ActorArgs.ok(source, "  ... and " + (matches.size() - MAX_LISTED) + " more."
                + " Narrow it with a query.");
        }
        return matches.size();
    }


    private static String describe(Emote emote) {
        return emote.id() + "  \"" + emote.name() + "\"  " + emote.durationTicks()
            + " ticks (" + String.format(Locale.ROOT, "%.2fs", emote.durationTicks() / 20.0f)
            + ")" + (emote.loop() ? ", loops" : "");
    }


    private static List<Emote> ranked(EmoteLibrary library, String query) {
        List<Emote> all = library.all();
        if (query == null || query.isBlank()) {
            return all;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);

        List<Emote> matches = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (Emote emote : all) {
            int score = score(emote, needle);
            if (score > 0) {
                matches.add(emote);
                scores.add(score);
            }
        }


        for (int i = 1; i < matches.size(); i++) {
            Emote candidate = matches.get(i);
            int candidateScore = scores.get(i);
            int j = i - 1;
            while (j >= 0 && scores.get(j) < candidateScore) {
                matches.set(j + 1, matches.get(j));
                scores.set(j + 1, scores.get(j));
                j--;
            }
            matches.set(j + 1, candidate);
            scores.set(j + 1, candidateScore);
        }
        return matches;
    }


    private static int score(Emote emote, String needle) {
        String id = emote.id().toLowerCase(Locale.ROOT);
        if (id.equals(needle)) {
            return 5;
        }
        if (id.startsWith(needle)) {
            return 4;
        }
        if (id.contains(needle)) {
            return 3;
        }
        String name = emote.name().toLowerCase(Locale.ROOT);
        if (name.startsWith(needle)) {
            return 2;
        }
        if (name.contains(needle)) {
            return 1;
        }
        return 0;
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> apply() {
        return Compat.literal("apply")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("emoteId", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String id : ids()) {
                            builder.suggest(id);
                        }
                        return builder.buildFuture();
                    })
                    .then(Compat.argument("startTick", IntegerArgumentType.integer())
                        .executes(ctx -> place(ctx, null, null))
                        .then(Compat.argument("durationTicks", IntegerArgumentType.integer())
                            .executes(ctx -> place(ctx,
                                ActorArgs.integer(ctx, "durationTicks"), null))
                            .then(Compat.argument("loop", BoolArgumentType.bool())
                                .executes(ctx -> place(ctx,
                                    ActorArgs.integer(ctx, "durationTicks"),
                                    ActorArgs.bool(ctx, "loop"))))))));
    }


    private static int place(CommandContext<FabricClientCommandSource> ctx,
                             Integer wantedDuration, Boolean wantedLoop) {
        FabricClientCommandSource source = ctx.getSource();
        String emoteId = ActorArgs.string(ctx, "emoteId").trim();
        int startTick = ActorArgs.integer(ctx, "startTick");

        Emote emote = Emotes.library().byId(emoteId);
        if (emote == null) {
            List<Emote> near = ranked(Emotes.library(), emoteId);
            ActorArgs.fail(source, "No emote with id \"" + emoteId + "\"."
                + (near.isEmpty()
                    ? " Run /actor emote list to see the " + Emotes.library().size() + " available."
                    : " Did you mean " + near.get(0).id() + " (" + near.get(0).name() + ")?"));
            return 0;
        }
        if (startTick < 0) {
            ActorArgs.fail(source, "startTick is a take tick and cannot be negative: got "
                + startTick + ". Tick 0 is the actor's first recorded tick.");
            return 0;
        }

        int duration = wantedDuration == null ? emote.durationTicks() : wantedDuration;
        boolean loop = wantedLoop == null ? emote.loop() : wantedLoop;
        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);

        return ActorArgs.mutate(source, query, (replay, actor) -> {
            EmoteTrack track = Emotes.trackFor(actor.take.id);
            EmoteTrack.Clip clip = new EmoteTrack.Clip(
                startTick,
                duration,
                emote.id(),
                DEFAULT_BLEND_TICKS,
                DEFAULT_BLEND_TICKS,
                loop,
                EmoteTrack.Clip.NORMAL_SPEED);
            track.add(clip);
            Emotes.save();
            ActorArgs.ok(source, actor.displayName() + ": placed \"" + emote.id() + "\" at take"
                + " tick " + startTick + " for " + clip.durationTicks() + " ticks"
                + (loop ? " (looping)" : "") + ". Track now holds " + track.size() + " clip(s).");
            if (clip.durationTicks() != duration) {
                ActorArgs.ok(source, "  Duration raised from " + duration + " to "
                    + clip.durationTicks() + "; a clip cannot be zero ticks long.");
            }
        });
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> clear() {
        return Compat.literal("clear")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .executes(ctx -> {
                    FabricClientCommandSource source = ctx.getSource();
                    String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                    return ActorArgs.mutate(source, query, (replay, actor) -> {
                        EmoteTrack track = Emotes.existingTrack(actor.take.id);
                        if (track == null || track.isEmpty()) {
                            ActorArgs.ok(source, actor.displayName() + " has no emote clips.");
                            return;
                        }
                        List<String> used = track.referencedEmotes();
                        int removed = track.size();
                        track.setAll(List.of());
                        Emotes.save();
                        ActorArgs.ok(source, "Cleared " + removed + " emote clip(s) from "
                            + actor.displayName() + ", using: "
                            + (used.isEmpty() ? "nothing" : String.join(", ", used)) + ".");
                    });
                }));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> at() {
        return Compat.literal("at")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .executes(ctx -> poseAt(ctx, null))
                .then(Compat.argument("takeTick", IntegerArgumentType.integer())
                    .executes(ctx -> poseAt(ctx, ActorArgs.integer(ctx, "takeTick")))));
    }

    private static int poseAt(CommandContext<FabricClientCommandSource> ctx,
                              Integer wantedTick) {
        FabricClientCommandSource source = ctx.getSource();
        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);

        return ActorArgs.report(source, query, (replay, actor) -> {
            int takeTick = wantedTick == null
                ? actor.takeTickFor(replay.getReplayTick())
                : wantedTick;
            if (takeTick < 0) {
                return "Take tick " + takeTick + " is before this actor's start (take tick 0"
                    + ", replay tick " + actor.take.startTick + ").";
            }

            EmoteTrack track = Emotes.existingTrack(actor.take.id);
            if (track == null || track.isEmpty()) {
                return actor.displayName() + " has no emote clips; nothing is posed at take tick "
                    + takeTick + ".";
            }

            EmoteTrack.Clip clip = track.clipAt(takeTick);
            EmotePose pose = Emotes.previewPose(actor.take.id, takeTick);
            StringBuilder out = new StringBuilder();
            out.append(actor.displayName()).append(" at take tick ").append(takeTick)
                .append(" (replay tick ").append(actor.take.startTick + takeTick).append("): ");
            if (clip == null) {
                out.append("no clip covers this tick.");
            } else {
                out.append("\"").append(clip.emoteId()).append("\" started at ")
                    .append(clip.startTick()).append(", ")
                    .append(clip.durationTicks()).append(" ticks long")
                    .append(clip.loop() ? ", looping" : "")
                    .append(", blends ").append((int) clip.blendInTicks())
                    .append(" in / ").append((int) clip.blendOutTicks()).append(" out.");
            }
            out.append(' ').append(pose.isIdentity()
                ? "Pose is neutral."
                : "Pose: head yaw " + round(pose.headYaw()) + " pitch " + round(pose.headPitch())
                    + ", body yaw " + round(pose.bodyYaw()) + " pitch " + round(pose.bodyPitch())
                    + ", L/R arm pitch " + round(pose.leftArmPitch()) + "/"
                    + round(pose.rightArmPitch()) + ".");
            return out.toString();
        });
    }


    private static String round(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
