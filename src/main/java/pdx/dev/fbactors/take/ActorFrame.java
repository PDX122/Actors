package pdx.dev.fbactors.take;

public record ActorFrame(
    double x, double y, double z,
    float yRot,
    float xRot,
    float yHeadRot,
    byte pose,
    byte flags
) {
    public static final int FLAG_SNEAKING   = 1;
    public static final int FLAG_SPRINTING  = 1 << 1;
    public static final int FLAG_ON_GROUND  = 1 << 2;
    public static final int FLAG_SWINGING   = 1 << 3;
    public static final int FLAG_FALL_FLYING = 1 << 4;

    public boolean sneaking()   { return (flags & FLAG_SNEAKING) != 0; }
    public boolean sprinting()  { return (flags & FLAG_SPRINTING) != 0; }
    public boolean onGround()   { return (flags & FLAG_ON_GROUND) != 0; }
    public boolean swinging()   { return (flags & FLAG_SWINGING) != 0; }
    public boolean fallFlying() { return (flags & FLAG_FALL_FLYING) != 0; }

    public static byte packFlags(boolean sneaking, boolean sprinting, boolean onGround,
                                 boolean swinging, boolean fallFlying) {
        int f = 0;
        if (sneaking)   f |= FLAG_SNEAKING;
        if (sprinting)  f |= FLAG_SPRINTING;
        if (onGround)   f |= FLAG_ON_GROUND;
        if (swinging)   f |= FLAG_SWINGING;
        if (fallFlying) f |= FLAG_FALL_FLYING;
        return (byte) f;
    }
}

