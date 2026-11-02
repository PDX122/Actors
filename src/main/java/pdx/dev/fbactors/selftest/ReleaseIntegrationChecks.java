package pdx.dev.fbactors.selftest;

import java.nio.file.*;

final class ReleaseIntegrationChecks {
    private static int stage;
    private static Path root;
    private static String error;
    static String error() { return error; }
    static boolean tick() {
        if(stage==3) return true;
        try {
            if(stage==0) {
                String directory=System.getenv("FBACTORS_RELEASE_QA_DIR");
                if(directory==null || directory.isBlank()) { stage=3; return true; }
                root=Path.of(directory);
                Files.deleteIfExists(root.resolve("boat-visibility.txt"));
                Files.deleteIfExists(root.resolve("boat-studio.txt"));
                BoatVisibilitySelfTest.arm(root.resolve("boat-visibility.txt")); stage=1;
            } else if(stage==1 && Files.isRegularFile(root.resolve("boat-visibility.txt"))) {
                if(!Files.readString(root.resolve("boat-visibility.txt")).startsWith("PASS"))
                    error=Files.readString(root.resolve("boat-visibility.txt"));
                BoatStudioSelfTest.arm(root.resolve("boat-studio.txt")); stage=2;
            } else if(stage==2 && Files.isRegularFile(root.resolve("boat-studio.txt"))) {
                if(!Files.readString(root.resolve("boat-studio.txt")).startsWith("PASS"))
                    error=Files.readString(root.resolve("boat-studio.txt"));
                stage=3;
            }
        } catch(Exception failure) { error=failure.toString(); stage=3; }
        return stage==3;
    }
}
