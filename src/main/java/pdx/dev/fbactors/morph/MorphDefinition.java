package pdx.dev.fbactors.morph;

import org.jetbrains.annotations.Nullable;


public record MorphDefinition(
    String id,
    String displayName,
    String kind,
    float scale,
    double hitboxWidth,
    float hitboxHeight,
    double eyeHeight,
    double rideOffset,
    MorphStance stance,
    boolean elytraCapable,
    boolean swimmer,
    boolean quadruped) {


@Override
public String id() {
    return this.id;
}


@Override
public String displayName() {
    return this.displayName;
}


@Override
public String kind() {
    return this.kind;
}


@Override
public float scale() {
    return this.scale;
}


@Override
public double hitboxWidth() {
    return this.hitboxWidth;
}


@Override
public float hitboxHeight() {
    return this.hitboxHeight;
}


@Override
public double eyeHeight() {
    return this.eyeHeight;
}


@Override
public double rideOffset() {
    return this.rideOffset;
}


@Override
public MorphStance stance() {
    return this.stance;
}


@Override
public boolean elytraCapable() {
    return this.elytraCapable;
}


@Override
public boolean swimmer() {
    return this.swimmer;
}


@Override
public boolean quadruped() {
    return this.quadruped;
}


public static MorphDefinition player() {
    return PLAYER;
}


private static final MorphDefinition PLAYER = new MorphDefinition(
    "player", "Player", "humanoid",
    1.0f, 0.6, 1.8f, 1.62, 0.0,
    MorphStance.HUMANOID, true, false, false);


    public static MorphDefinition of(String id, @Nullable String displayName) {
        return new MorphDefinition(
            id,
            displayName == null || displayName.isBlank() ? id : displayName,
            "monster",
            1.0f,
            0.6,
            1.8f,
            1.62,
            0.0,
            MorphStance.HUMANOID,
            true,
            false,
            false);
    }


    public boolean isPlayer() {
        return "player".equals(this.id);
    }
}

