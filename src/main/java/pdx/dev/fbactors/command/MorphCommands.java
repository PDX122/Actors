package pdx.dev.fbactors.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.morph.Morph;
import pdx.dev.fbactors.morph.MorphCompatibility;
import pdx.dev.fbactors.morph.MorphDefinition;
import pdx.dev.fbactors.morph.MorphRegistry;
import pdx.dev.fbactors.morph.MorphSearch;
import pdx.dev.fbactors.morph.MorphSelection;

import pdx.dev.fbactors.settings.FbSettings;

import java.util.List;


public final class MorphCommands {


    private static final int MAX_LISTED = 20;

    private MorphCommands() {
    }


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                               LiteralArgumentBuilder<FabricClientCommandSource> parent) {
        LiteralArgumentBuilder<FabricClientCommandSource> morph = Compat.literal("morph");
        morph.then(list());
        morph.then(set());
        morph.then(clear());
        parent.then(morph);
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> list() {
        return Compat.literal("list")
            .executes(ctx -> printMorphs(ctx.getSource(), null))
            .then(Compat.argument("query", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (String id : Morph.registry().ids()) {
                        builder.suggest(id);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> printMorphs(ctx.getSource(),
                    ActorArgs.string(ctx, "query"))));
    }

    private static int printMorphs(FabricClientCommandSource source, String query) {
        MorphRegistry registry = Morph.registry();


        List<MorphDefinition> matches =
            MorphSearch.search(registry, query, null, 0, MAX_LISTED);
        int total = MorphSearch.resultCount(registry, query, null);

        if (matches.isEmpty()) {
            ActorArgs.fail(source, "No morph matches \"" + query.trim() + "\". "
                + "The catalogue holds " + registry.size() + " morph(s).");
            return 0;
        }
        ActorArgs.ok(source, total + " morph(s)"
            + (query == null || query.isBlank() ? ":" : " matching \"" + query.trim() + "\":"));
        for (MorphDefinition morph : matches) {
            ActorArgs.ok(source, "  " + describe(morph));
        }
        if (total > matches.size()) {
            ActorArgs.ok(source, "  ... and " + (total - matches.size()) + " more."
                + " Narrow it with a query.");
        }
        return total;
    }


    private static String describe(MorphDefinition morph) {
        return morph.id() + "  \"" + morph.displayName() + "\"  "
            + MorphSearch.categoryOf(morph) + "  scale " + morph.scale()
            + (morph.quadruped() ? ", quadruped" : "")
            + (morph.elytraCapable() ? ", elytra" : "")
            + (MorphCompatibility.canApply(morph, true) ? "" : ", NEEDS RENDERER SUPPORT");
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> set() {
        return Compat.literal("set")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("morphId", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String id : Morph.registry().ids()) {
                            builder.suggest(id);
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> apply(ctx, null, null))
                    .then(Compat.argument("scale", DoubleArgumentType.doubleArg())
                        .executes(ctx -> apply(ctx,
                            ActorArgs.decimal(ctx, "scale"), null))
                        .then(Compat.argument("alpha", DoubleArgumentType.doubleArg())
                            .executes(ctx -> apply(ctx,
                                ActorArgs.decimal(ctx, "scale"),
                                ActorArgs.decimal(ctx, "alpha")))))));
    }

    private static int apply(CommandContext<FabricClientCommandSource> ctx,
                             Double wantedScale, Double wantedAlpha) {
        FabricClientCommandSource source = ctx.getSource();
        if (!FbSettings.morphEnabled()) {
            ActorArgs.fail(source, "Morphs are disabled. Turn them back on in FBA Settings.");
            return 0;
        }
        String morphId = ActorArgs.string(ctx, "morphId").trim();
        MorphDefinition morph = Morph.registry().byId(morphId);
        if (morph == null) {
            List<MorphDefinition> near =
                MorphSearch.search(Morph.registry(), morphId, null, 0, 1);
            ActorArgs.fail(source, "No morph with id \"" + morphId + "\"."
                + (near.isEmpty()
                    ? " Run /actor morph list to see the " + Morph.registry().size() + " available."
                    : " Did you mean " + near.get(0).id()
                        + " (\"" + near.get(0).displayName() + "\")?"));
            return 0;
        }

        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
        return ActorArgs.mutate(source, query, (replay, actor) -> {
            MorphSelection current = Morph.selectionFor(actor.take.id);


            MorphSelection next = current == null
                ? MorphSelection.of(actor.take.id, morph.id())
                : current.withMorph(morph.id());
            if (wantedScale != null) {
                next = next.withScale(wantedScale.floatValue());
            }
            if (wantedAlpha != null) {
                next = next.withAlpha(wantedAlpha.floatValue());
            }
            Morph.store().set(actor.take.id, next);
            Morph.save();

            ActorArgs.ok(source, actor.displayName() + " is now \"" + morph.displayName()
                + "\" (" + morph.id() + ") at scale " + next.scale()
                + ", alpha " + next.alpha() + ".");
            if (wantedScale != null && next.scale() != wantedScale.floatValue()) {
                ActorArgs.ok(source, "  Note: scale " + wantedScale + " is outside "
                    + MorphSelection.MIN_SCALE + "-" + MorphSelection.MAX_SCALE
                    + " and was clamped to " + next.scale() + ".");
            }
            if (wantedAlpha != null && next.alpha() != wantedAlpha.floatValue()) {
                ActorArgs.ok(source, "  Note: alpha " + wantedAlpha + " is outside 0-1 and was"
                    + " clamped to " + next.alpha() + ".");
            }
            if (!MorphCompatibility.canApply(morph, true)) {


                MorphCompatibility.report(morph);
                ActorArgs.ok(source, "  Note: this morph needs renderer support that this"
                    + " version does not have, so it is drawn at player size. The selection"
                    + " is saved and will apply once support lands.");
            }
        });
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> clear() {
        return Compat.literal("clear")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .executes(ctx -> {
                    FabricClientCommandSource source = ctx.getSource();
                    if (!FbSettings.morphEnabled()) {
                        ActorArgs.fail(source, "Morphs are disabled. Turn them back on in FBA Settings.");
                        return 0;
                    }
                    String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                    return ActorArgs.mutate(source, query, (replay, actor) -> {
                        MorphSelection current = Morph.selectionFor(actor.take.id);
                        boolean wasMorphed = current != null && current.isMorphed();
                        String was = wasMorphed ? current.morphId() : null;
                        boolean removed = Morph.store().clear(actor.take.id);
                        Morph.save();
                        if (!removed || !wasMorphed) {
                            ActorArgs.ok(source, actor.displayName()
                                + " was not morphed; nothing to clear.");
                            return;
                        }
                        ActorArgs.ok(source, actor.displayName() + " un-morphed (was \"" + was
                            + "\") and is a player again. Re-apply with /actor morph set "
                            + ActorArgs.ACTOR + " " + was + ".");
                    });
                }));
    }
}

