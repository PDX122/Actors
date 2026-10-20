package pdx.dev.fbactors.take;


public record ActorSubtitle(int tick, int durationTicks, String speaker, String text,
                            int colorArgb, float size, byte position) {


    public static final byte POS_DEFAULT = 0;


    public static final byte POS_BOTTOM = 1;


    public static final byte POS_TOP = 2;


    public static final int MAX_DURATION_TICKS = 400;


    public static final int MAX_TEXT = 280;

    public ActorSubtitle {
        durationTicks = Math.max(1, Math.min(MAX_DURATION_TICKS, durationTicks));
        if (speaker != null && speaker.length() > 32) {
            speaker = speaker.substring(0, 32);
        }
        if (text == null) {
            text = "";
        } else if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
        if (size < 0.0f) {
            size = 0.0f;
        }
        if (position != POS_BOTTOM && position != POS_TOP) {
            position = POS_DEFAULT;
        }
    }


    public int endTick() {
        return tick + durationTicks - 1;
    }
}

