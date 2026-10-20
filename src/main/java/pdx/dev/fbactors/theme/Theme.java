package pdx.dev.fbactors.theme;

import com.moulberry.flashback.visuals.ReplayVisuals;
import org.jetbrains.annotations.Nullable;


public enum Theme {


    NONE("None", -1, null, false, null, 0.0f, 0.0f, false),


    MIDDAY("Midday", 6000, new float[] {0.50f, 0.70f, 1.00f}, false, null, 0.0f, 0.0f, false),


    GOLDEN_HOUR("Golden Hour", 12300, new float[] {1.00f, 0.72f, 0.42f},
        true, new float[] {1.00f, 0.78f, 0.52f}, 24.0f, 220.0f, false),


    BLUE_HOUR("Blue Hour", 13400, new float[] {0.34f, 0.45f, 0.76f},
        true, new float[] {0.34f, 0.45f, 0.76f}, 20.0f, 180.0f, false),


    OVERCAST("Overcast", 6000, new float[] {0.72f, 0.74f, 0.78f},
        true, new float[] {0.70f, 0.72f, 0.76f}, 12.0f, 110.0f, false),


    NIGHT("Night", 18000, new float[] {0.04f, 0.06f, 0.14f},
        true, new float[] {0.05f, 0.07f, 0.16f}, 16.0f, 160.0f, false);

    private final String label;
    private final int timeOfDay;
    @Nullable
    private final float[] skyColour;
    private final boolean fog;
    @Nullable
    private final float[] fogColour;
    private final float fogStart;
    private final float fogEnd;
    private final boolean nightVision;

    Theme(String label, int timeOfDay, @Nullable float[] skyColour, boolean fog,
          @Nullable float[] fogColour, float fogStart, float fogEnd, boolean nightVision) {
        this.label = label;
        this.timeOfDay = timeOfDay;
        this.skyColour = skyColour;
        this.fog = fog;
        this.fogColour = fogColour;
        this.fogStart = fogStart;
        this.fogEnd = fogEnd;
        this.nightVision = nightVision;
    }


    public String label() {
        return this.label;
    }


    public boolean isReset() {
        return this == NONE;
    }


    public void apply(ReplayVisuals visuals) {
        if (this.skyColour != null) {
            if (visuals.skyColour == null || visuals.skyColour.length < 3) {
                visuals.skyColour = new float[3];
            }
            System.arraycopy(this.skyColour, 0, visuals.skyColour, 0, 3);
        }
        visuals.overrideTimeOfDay = this.timeOfDay;
        visuals.overrideFog = this.fog;
        visuals.overrideFogColour = this.fogColour != null;
        if (this.fogColour != null) {
            if (visuals.fogColour == null || visuals.fogColour.length < 3) {
                visuals.fogColour = new float[3];
            }
            System.arraycopy(this.fogColour, 0, visuals.fogColour, 0, 3);
        }
        visuals.overrideFogStart = this.fogStart;
        visuals.overrideFogEnd = this.fogEnd;
        visuals.overrideNightVision = this.nightVision;
    }
}

