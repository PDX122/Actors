package pdx.dev.fbactors.morph;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.Entity;

public final class PreviewIdentity {
    private static final AtomicInteger NEXT = new AtomicInteger(-1_600_000_000);
    private PreviewIdentity() {}
    public static int next() { return NEXT.getAndDecrement(); }
    public static void assign(Entity entity) { entity.setId(next()); }
}
