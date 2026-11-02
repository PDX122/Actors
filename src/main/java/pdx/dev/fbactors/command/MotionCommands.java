package pdx.dev.fbactors.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.MotionSmoothing;
import pdx.dev.fbactors.ui.MotionEdits;

public final class MotionCommands {
    private MotionCommands() {}

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        var root = Compat.literal("smooth");
        for (MotionSmoothing.Mode mode : MotionSmoothing.Mode.values()) {
            root.then(Compat.literal(mode.name().toLowerCase(java.util.Locale.ROOT))
                .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                    .executes(ctx -> apply(ctx.getSource(), ActorArgs.string(ctx, ActorArgs.ACTOR), mode, 2, 0.5f))
                    .then(Compat.argument("radius", IntegerArgumentType.integer(1, MotionSmoothing.MAX_RADIUS))
                        .then(Compat.argument("strength", FloatArgumentType.floatArg(0, 1))
                            .executes(ctx -> apply(ctx.getSource(), ActorArgs.string(ctx, ActorArgs.ACTOR), mode,
                                IntegerArgumentType.getInteger(ctx, "radius"),
                                FloatArgumentType.getFloat(ctx, "strength")))))));
        }
        return root;
    }

    private static int apply(FabricClientCommandSource source, String query,
                             MotionSmoothing.Mode mode, int radius, float strength) {
        return ActorArgs.mutate(source, query, (replay, actor) -> {
            if (ActorMode.phase() != ActorMode.Phase.IDLE || actor.take.locked) {
                ActorArgs.fail(source, "Finish actor mode and unlock this track before smoothing.");
                return;
            }
            if (MotionEdits.apply(actor, radius, strength, mode)) {
                ActorArgs.ok(source, "Smoothed " + actor.displayName() + ". Undo restores the previous motion.");
            } else {
                ActorArgs.ok(source, "No motion changes needed.");
            }
        });
    }
}
