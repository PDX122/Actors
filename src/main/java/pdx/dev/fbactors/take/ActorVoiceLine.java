package pdx.dev.fbactors.take;


public record ActorVoiceLine(int tick, short[] samples, String caption) {


    public static final int MAX_TOTAL_SAMPLES = 28_800_000;

    public ActorVoiceLine(int tick, short[] samples) {
        this(tick, samples, null);
    }

    public ActorVoiceLine {
        if (samples == null) {
            samples = new short[0];
        }
        if (caption != null) {
            caption = caption.strip();
            if (caption.isEmpty() || caption.length() > ActorSubtitle.MAX_TEXT) {
                caption = caption.isEmpty() ? null : caption.substring(0, ActorSubtitle.MAX_TEXT);
            }
        }
    }

    public int durationTicks() {
        return this.samples.length / 960;
    }
}

