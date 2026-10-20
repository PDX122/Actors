package pdx.dev.fbactors.morph;

import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;


public record MorphSelection(
    UUID takeId,
    @Nullable String morphId,
    float scale,
    float alpha,
    boolean hideName) {


    public static final float MIN_SCALE = 0.25f;


    public static final float MAX_SCALE = 4.0f;


    public static final float DEFAULT_SCALE = 1.0f;


    public static final float DEFAULT_ALPHA = 1.0f;

    public MorphSelection {
        if (takeId == null) {
            throw new IllegalArgumentException("A morph selection must belong to a take");
        }


        if (morphId != null && morphId.isBlank()) {
            morphId = null;
        }


        if (!Float.isFinite(scale)) {
            scale = DEFAULT_SCALE;
        }
        if (!Float.isFinite(alpha)) {
            alpha = DEFAULT_ALPHA;
        }
        scale = Mth.clamp(scale, MIN_SCALE, MAX_SCALE);
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);
    }


    public boolean isMorphed() {
        return this.morphId != null;
    }


    public boolean isInvisible() {
        return this.alpha <= 0.0f;
    }


    public static MorphSelection none(UUID takeId) {
        return new MorphSelection(takeId, null, DEFAULT_SCALE, DEFAULT_ALPHA, false);
    }


    public static MorphSelection of(UUID takeId, @Nullable String morphId) {
        return new MorphSelection(takeId, morphId, DEFAULT_SCALE, DEFAULT_ALPHA, false);
    }

    public MorphSelection withScale(float newScale) {
        return new MorphSelection(this.takeId, this.morphId, newScale, this.alpha,
            this.hideName);
    }


    public MorphSelection withAlpha(float newAlpha) {
        return new MorphSelection(this.takeId, this.morphId, this.scale, newAlpha,
            this.hideName);
    }


    public MorphSelection withMorph(@Nullable String newMorphId) {
        return new MorphSelection(this.takeId, newMorphId, this.scale, this.alpha,
            this.hideName);
    }


    public MorphSelection withNameHidden(boolean hidden) {
        return new MorphSelection(this.takeId, this.morphId, this.scale, this.alpha, hidden);
    }
}
