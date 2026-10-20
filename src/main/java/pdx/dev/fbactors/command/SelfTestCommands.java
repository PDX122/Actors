package pdx.dev.fbactors.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.selftest.SelfTest;


public final class SelfTestCommands {

    private SelfTestCommands() {
    }


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                LiteralArgumentBuilder<FabricClientCommandSource> parent) {
        LiteralArgumentBuilder<FabricClientCommandSource> selftest = Compat.literal("selftest");
        selftest.then(Compat.literal("start")
            .executes(ctx -> {
                ActorArgs.ok(ctx.getSource(), SelfTest.requestStart());
                return 1;
            }));
        selftest.then(Compat.literal("stop")
            .executes(ctx -> {
                ActorArgs.ok(ctx.getSource(), SelfTest.requestStop());
                return 1;
            }));
        selftest.then(Compat.literal("status")
            .executes(ctx -> {
                ActorArgs.ok(ctx.getSource(), SelfTest.status());
                return 1;
            }));
        parent.then(selftest);
    }
}

