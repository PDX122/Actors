package pdx.dev.fbactors.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.moulberry.flashback.Flashback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.convert.McprToFlashback;
import pdx.dev.fbactors.util.GameVersions;

import java.nio.file.Files;
import java.nio.file.Path;


public final class ConvertCommands {

    private ConvertCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                LiteralArgumentBuilder<FabricClientCommandSource> parent) {
        LiteralArgumentBuilder<FabricClientCommandSource> convert = Compat.literal("convert");
        convert.then(Compat.argument("file", StringArgumentType.greedyString())
            .executes(ctx -> {
                String raw = StringArgumentType.getString(ctx, "file").trim();
                ActorArgs.ok(ctx.getSource(), run(raw));
                return 1;
            }));
        parent.then(convert);
    }

    private static String run(String raw) {
        Path mcpr = resolve(raw);
        if (mcpr == null) {
            return "No such .mcpr file: " + raw + " (looked in replay_recordings)";
        }

        RegistryAccess registries = registries();
        if (registries == null) {
            return "Open a world first, so there are registries to convert with";
        }

        Path folder = Flashback.getReplayFolder();
        if (folder == null) {
            return "Flashback has no replay folder to write into";
        }

        String gameVersion = GameVersions.gameVersionName();
        int dataVersion = GameVersions.gameDataVersion();
        int protocolVersion = GameVersions.gameProtocolVersion();

        McprToFlashback.Result result =
            McprToFlashback.convert(mcpr, registries, gameVersion, dataVersion, protocolVersion,
                folder);
        if (!result.ok() || result.path() == null) {
            return result.message();
        }

        try {
            Flashback.openReplayWorld(result.path());
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Converted {} but could not open it", result.path(), e);
            return "Converted " + result.path().getFileName() + " but Flashback would not open it";
        }
        return "Converted and opened " + result.path().getFileName();
    }

    @Nullable
    private static Path resolve(String raw) {
        Path direct = Path.of(raw);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        String name = raw.endsWith(".mcpr") ? raw : raw + ".mcpr";
        Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve("replay_recordings");
        Path candidate = folder.resolve(name);
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        Path fallback = folder.resolve(raw);
        return Files.isRegularFile(fallback) ? fallback : null;
    }

    @Nullable
    private static RegistryAccess registries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.registryAccess();
        }
        if (mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().registryAccess();
        }
        return null;
    }
}

