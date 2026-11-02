package pdx.dev.fbactors.util;

import net.fabricmc.loader.api.FabricLoader;
import pdx.dev.fbactors.FlashbackActors;

public final class StartupBanner {
    private static final String PDX = """
        ██████╗ ██████╗ ██╗  ██╗
        ██╔══██╗██╔══██╗╚██╗██╔╝
        ██████╔╝██║  ██║ ╚███╔╝
        ██╔═══╝ ██║  ██║ ██╔██╗
        ██║     ██████╔╝██╔╝ ██╗
        ╚═╝     ╚═════╝ ╚═╝  ╚═╝
        """;

    private StartupBanner() {}

    public static void log() {
        FlashbackActors.LOGGER.info("\nFlashback Actors {} | Minecraft {}\n{}By PDX | Ready for recording and replay editing.\nBuild: {}",
            version("fbactors"),version("minecraft"),PDX,FlashbackActors.BUILD_TAG);
    }

    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }
}
