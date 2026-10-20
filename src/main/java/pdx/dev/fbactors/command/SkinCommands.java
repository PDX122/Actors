package pdx.dev.fbactors.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pdx.dev.fbactors.actor.ActorSkins;
import pdx.dev.fbactors.compat.Compat;


public final class SkinCommands {

    private SkinCommands() {
    }


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                               LiteralArgumentBuilder<FabricClientCommandSource> parent) {
        LiteralArgumentBuilder<FabricClientCommandSource> skin = Compat.literal("skin");
        skin.then(file());
        skin.then(clear());
        skin.then(name());
        parent.then(skin);
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> file() {
        return Compat.literal("file")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("path", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        FabricClientCommandSource source = ctx.getSource();
                        String path = ActorArgs.string(ctx, "path").trim();
                        if (path.isEmpty()) {
                            ActorArgs.fail(source,
                                "Give a path to a 64x64 skin PNG, e.g. "
                                    + ActorSkins.defaultSkinPath());
                            return 0;
                        }
                        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                        return ActorArgs.mutate(source, query, (replay, actor) -> {


                            String hadSkinName = actor.take.skinName;
                            ActorSkins.applyFile(actor, path);
                            ActorArgs.save(actor.take);
                            ActorArgs.ok(source, actor.displayName()
                                + " skin set from file " + path + ".");
                            if (hadSkinName != null && !hadSkinName.isBlank()) {
                                ActorArgs.ok(source, "  Their name was overridden to \""
                                    + hadSkinName + "\", which a file skin replaces; use"
                                    + " /actor skin name to set it again.");
                            }
                        });
                    })));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> clear() {
        return Compat.literal("clear")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .executes(ctx -> {
                    FabricClientCommandSource source = ctx.getSource();
                    String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                    return ActorArgs.mutate(source, query, (replay, actor) -> {
                        ActorSkins.clear(actor);
                        ActorArgs.save(actor.take);
                        ActorArgs.ok(source, actor.displayName()
                            + " skin cleared; they use the default skin again.");
                    });
                }));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> name() {
        return Compat.literal("name")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        FabricClientCommandSource source = ctx.getSource();
                        String wanted = ActorArgs.string(ctx, "name").trim();
                        boolean clearing = wanted.isEmpty()
                            || wanted.equalsIgnoreCase("none")
                            || wanted.equalsIgnoreCase("off");
                        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                        return ActorArgs.mutate(source, query, (replay, actor) -> {
                            ActorSkins.applyName(actor, clearing ? null : wanted);
                            ActorArgs.ok(source, clearing
                                ? "Cleared the floating name for " + actor.displayName() + "."
                                : actor.displayName() + " is labelled \"" + wanted
                                    + "\" above their head. /actor rename changes their name"
                                    + " everywhere else.");
                        });
                    })));
    }
}
