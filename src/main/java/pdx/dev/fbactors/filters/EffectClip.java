package pdx.dev.fbactors.filters;

import java.util.UUID;

public final class EffectClip {
    public UUID id = UUID.randomUUID();
    public String name = "Effect";
    public UUID actor;
    public int start;
    public int end = 100;
    public int fadeIn = 10;
    public int fadeOut = 10;
    public boolean enabled = true;
    public LookSettings look = new LookSettings();

    public EffectClip copy() {
        EffectClip copy = new EffectClip();
        copy.id = id;
        copy.name = name;
        copy.actor = actor;
        copy.start = start;
        copy.end = end;
        copy.fadeIn = fadeIn;
        copy.fadeOut = fadeOut;
        copy.enabled = enabled;
        copy.look = look.copy();
        return copy;
    }

    public void normalize() {
        if (id == null) id = UUID.randomUUID();
        if (name == null || name.isBlank()) name = "Effect";
        name = name.substring(0, Math.min(64, name.length()));
        start = Math.max(0, Math.min(100_000_000, start));
        end = Math.max(start + 1, Math.min(100_000_001, end));
        fadeIn = Math.max(0, Math.min(end - start, fadeIn));
        fadeOut = Math.max(0, Math.min(end - start, fadeOut));
        if (look == null) look = new LookSettings();
        look.normalize();
    }

    public float weight(float tick) {
        if (!enabled || tick < start || tick >= end || !Float.isFinite(tick)) return 0;
        float in = fadeIn == 0 ? 1 : (tick - start) / fadeIn;
        float out = fadeOut == 0 ? 1 : (end - tick) / fadeOut;
        float value = Math.max(0, Math.min(1, Math.min(in, out)));
        return value * value * (3 - 2 * value);
    }
}
