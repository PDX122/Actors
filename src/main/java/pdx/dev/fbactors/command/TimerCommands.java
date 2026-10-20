package pdx.dev.fbactors.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.timer.TakeTimer;


public final class TimerCommands {

    private TimerCommands() {
    }


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                               LiteralArgumentBuilder<FabricClientCommandSource> parent) {
        parent.then(Compat.literal("timer")
            .executes(ctx -> {
                FabricClientCommandSource source = ctx.getSource();
                int seconds = TakeTimer.durationSeconds();
                if (seconds <= 0) {
                    source.sendFeedback(Component.translatable("fbactors.timer.off"));
                } else {
                    source.sendFeedback(Component.translatable(
                        "fbactors.timer.current", format(seconds)));
                }
                return 1;
            })
            .then(Compat.argument("duration", StringArgumentType.word())
                .executes(ctx -> {
                    FabricClientCommandSource source = ctx.getSource();
                    String raw = StringArgumentType.getString(ctx, "duration");
                    int seconds = ActorCommands.parseSeconds(raw);
                    if (seconds < 0) {
                        source.sendFeedback(Component.translatable(
                            "fbactors.timer.unreadable", raw.trim()));
                        return 0;
                    }
                    TakeTimer.durationSeconds(seconds);
                    if (seconds <= 0) {
                        source.sendFeedback(Component.translatable("fbactors.timer.disabled"));
                    } else {
                        source.sendFeedback(Component.translatable(
                            "fbactors.timer.set", format(seconds)));
                    }
                    return 1;
                })));
    }


    private static String format(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}

