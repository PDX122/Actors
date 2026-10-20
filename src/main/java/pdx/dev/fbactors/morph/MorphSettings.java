package pdx.dev.fbactors.morph;

import org.jetbrains.annotations.Nullable;

import pdx.dev.fbactors.settings.FbSettings;
import pdx.dev.fbactors.take.ActorTake;


public final class MorphSettings {

    private static String morphId;

    private MorphSettings() {
    }


    public static void capture(@Nullable String id) {
        morphId = id;
    }


    public static void applyTo(ActorTake take) {
        if (!FbSettings.morphEnabled()) {
            return;
        }
        if (morphId != null && !morphId.isBlank()) {
            Morph.assign(take.id, morphId);
        }
    }

    public static void reset() {
        morphId = null;
    }

    @Nullable
    public static String morphId() {
        return morphId;
    }
}

