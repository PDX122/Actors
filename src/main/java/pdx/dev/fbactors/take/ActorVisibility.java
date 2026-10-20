package pdx.dev.fbactors.take;


public record ActorVisibility(int startTick, int endTick) {


    public static final int MAX_DURATION_TICKS = 12000;

    public ActorVisibility {
        if (endTick < startTick) {
            int swap = startTick;
            startTick = endTick;
            endTick = swap;
        }
        if (endTick - startTick > MAX_DURATION_TICKS) {
            endTick = startTick + MAX_DURATION_TICKS;
        }
    }
}

