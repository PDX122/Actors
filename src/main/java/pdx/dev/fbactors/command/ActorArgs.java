package pdx.dev.fbactors.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorTake;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;


public final class ActorArgs {


    public static final String ACTOR = "actor";


    private static final String ERROR_PREFIX = "Error: ";


    private static final int MAX_LISTED_NAMES = 8;

    private ActorArgs() {
    }


    public enum Kind {

        FOUND,

        EMPTY,

        UNKNOWN,

        AMBIGUOUS
    }


    public record Match(Kind kind, @Nullable Actor actor, String detail) {

        public boolean found() {
            return this.kind == Kind.FOUND && this.actor != null;
        }
    }


    public static RequiredArgumentBuilder<FabricClientCommandSource, String> actorArgument(String name) {
        return Compat.argument(name, StringArgumentType.string())
            .suggests((ctx, builder) -> {
                for (String candidate : candidateNames()) {


                    builder.suggest(candidate.contains(" ") ? "\"" + candidate + "\"" : candidate);
                }
                return builder.buildFuture();
            });
    }


    public static List<String> candidateNames() {
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        List<String> names = new ArrayList<>(actors.size());
        for (Actor actor : actors) {
            names.add(actor.displayName());
        }
        return names;
    }


    @Nullable
    public static ReplayServer requireReplay(FabricClientCommandSource source) {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            fail(source, "Not in a replay. Open a replay and try again.");
        }
        return replay;
    }


    public static int mutate(FabricClientCommandSource source, String query,
                             BiConsumer<ReplayServer, Actor> action) {
        ReplayServer replay = requireReplay(source);
        if (replay == null) {
            return 0;
        }
        Match match = resolve(query);
        if (!match.found()) {
            fail(source, match.detail());
            return 0;
        }
        Actor actor = match.actor();
        replay.execute(() -> action.accept(replay, actor));
        return 1;
    }


    public static int report(FabricClientCommandSource source, String query,
                             BiFunction<ReplayServer, Actor, String> action) {
        ReplayServer replay = requireReplay(source);
        if (replay == null) {
            return 0;
        }
        Match match = resolve(query);
        if (!match.found()) {
            fail(source, match.detail());
            return 0;
        }
        Actor actor = match.actor();
        String message = action.apply(replay, actor);
        if (message != null && !message.isEmpty()) {
            ok(source, message);
        }
        return 1;
    }


    public static Match resolve(String query) {
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        if (actors.isEmpty()) {
            return new Match(Kind.EMPTY, null,
                "This replay has no actors yet. Record one with /actor, or add a row in the timeline.");
        }

        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return new Match(Kind.UNKNOWN, null,
                "Give an actor name. Valid names: " + nameList(actors));
        }

        List<Actor> candidates = new ArrayList<>(2);
        for (Actor actor : actors) {
            if (actor.displayName().toLowerCase(Locale.ROOT).contains(needle)) {
                candidates.add(actor);
            }
        }

        if (candidates.size() == 1) {
            Actor found = candidates.get(0);
            return new Match(Kind.FOUND, found, describe(found));
        }
        if (candidates.isEmpty()) {
            return new Match(Kind.UNKNOWN, null,
                "No actor matches \"" + query.trim() + "\". Valid names: " + nameList(actors));
        }
        return new Match(Kind.AMBIGUOUS, null,
            "\"" + query.trim() + "\" matches " + candidates.size() + " actors: "
                + nameList(candidates) + ". Type more of the name to pick one.");
    }


    private static String nameList(List<Actor> actors) {
        StringBuilder out = new StringBuilder();
        int shown = Math.min(actors.size(), MAX_LISTED_NAMES);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(actors.get(i).displayName());
        }
        if (shown < actors.size()) {
            out.append(", ... (").append(actors.size() - shown).append(" more)");
        }
        return out.toString();
    }


    public static String describe(Actor actor) {
        ActorTake take = actor.take;
        StringBuilder out = new StringBuilder();
        out.append(actor.displayName())
            .append("  [").append(take.id).append("]  ")
            .append(take.length()).append(" ticks");
        if (take.trimIn > 0 || take.trimOut > 0) {
            out.append(", trimmed -").append(take.trimIn).append(" in / -")
                .append(take.trimOut).append(" out");
        }
        out.append(", offset ").append(take.offsetTicks);
        out.append(take.enabled ? ", enabled" : ", DISABLED");
        if (take.skinName != null && !take.skinName.isBlank()) {
            out.append(", skin ").append(take.skinName);
        }
        return out.toString();
    }


    public static void ok(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message));
    }


    public static void fail(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(ERROR_PREFIX + message));
    }


    public static void clamped(FabricClientCommandSource source, String what, int wanted, int applied) {
        if (wanted == applied) {
            return;
        }
        source.sendFeedback(Component.literal(
            "Clamped " + what + " from " + wanted + " to " + applied + "."));
    }


    public static String string(CommandContext<FabricClientCommandSource> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    public static int integer(CommandContext<FabricClientCommandSource> ctx, String name) {
        return IntegerArgumentType.getInteger(ctx, name);
    }

    public static double decimal(CommandContext<FabricClientCommandSource> ctx, String name) {
        return DoubleArgumentType.getDouble(ctx, name);
    }

    public static boolean bool(CommandContext<FabricClientCommandSource> ctx, String name) {
        return BoolArgumentType.getBool(ctx, name);
    }


    public static void save(ActorTake take) {
        FlashbackActors.save(take);
    }


    public static UUID newTakeId() {
        return UUID.randomUUID();
    }


    public static void suggestAll(SuggestionsBuilder builder, List<String> options) {
        for (String option : options) {
            builder.suggest(option);
        }
    }
}
