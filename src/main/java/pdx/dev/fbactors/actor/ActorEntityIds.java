package pdx.dev.fbactors.actor;

import java.util.concurrent.atomic.AtomicInteger;

public final class ActorEntityIds {
    private static final AtomicInteger NEXT = new AtomicInteger(-980_000_000);

    private ActorEntityIds() {}

    public static int next() { return NEXT.getAndIncrement(); }
}
