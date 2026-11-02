package pdx.dev.fbactors.take;

public final class TakeTrimming {
    private TakeTrimming() {}

    public static int startAt(ActorTake take, int replayTick) {
        long wanted = (long) replayTick - take.offsetTicks - take.startTick;
        int maximum = Math.max(0, take.length() - 1 - Math.max(0, take.trimOut));
        return (int) Math.max(0, Math.min(maximum, wanted));
    }

    public static int endAt(ActorTake take, int replayTick) {
        long wanted = (long) take.startTick + take.length() - 1 + take.offsetTicks - replayTick;
        int maximum = Math.max(0, take.length() - 1 - Math.max(0, take.trimIn));
        return (int) Math.max(0, Math.min(maximum, wanted));
    }
}
