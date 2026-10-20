package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;


public enum CameraHeight {


    FEET(-1.4),


    EYE(0.0),


    HIGH(4.0);

    private final double blocks;

    CameraHeight(double blocks) {
        this.blocks = blocks;
    }


    public double blocks() {
        return this.blocks;
    }


    public String label() {
        return switch (this) {
            case FEET -> "Feet level";
            case EYE -> "Eye level";
            case HIGH -> "High";
        };
    }


    @Nullable
    public static CameraHeight byOrdinal(int ordinal) {
        CameraHeight[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}

