package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;


public enum CameraMove {


    STATIC,


    PUSH_IN,


    PULL_OUT,


    FOLLOW,


    LEAD,


    PASS_BY,


    ORBIT,


    CRANE,


    PAN;


    public String label() {
        return switch (this) {
            case STATIC -> "Static";
            case PUSH_IN -> "Zoom In";
            case PULL_OUT -> "Zoom Out";
            case FOLLOW -> "Follow";
            case LEAD -> "Lead";
            case PASS_BY -> "Pass By";
            case ORBIT -> "Orbit";
            case CRANE -> "Crane";
            case PAN -> "Pan";
        };
    }


    public boolean needsMovingSubject() {
        return this == FOLLOW || this == LEAD;
    }


    @Nullable
    public static CameraMove byOrdinal(int ordinal) {
        CameraMove[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}

