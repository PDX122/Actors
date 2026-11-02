package pdx.dev.fbactors.filters;

import com.moulberry.flashback.Flashback;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import pdx.dev.fbactors.FlashbackActors;

public final class ActorLooks {
    private static final Map<Object, UUID> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UUID, UUID> ALIASES = new ConcurrentHashMap<>();
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESSED = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static FilterGpuPipeline pipeline;
    private static boolean failed;
    private ActorLooks() {}

    public static void alias(UUID entity, UUID actor) { ALIASES.put(entity, actor); }
    public static void unalias(UUID entity) { ALIASES.remove(entity); }

    public static boolean suppress(boolean on) {
        boolean before = SUPPRESSED.get();
        if (on) SUPPRESSED.set(Boolean.TRUE);
        else SUPPRESSED.remove();
        return before;
    }

    public static boolean isSuppressed() { return SUPPRESSED.get(); }
    public static void note(Object state, Entity entity) {
        if (isSuppressed()) return;
        UUID id = ALIASES.getOrDefault(entity.getUUID(), entity.getUUID());
        STATES.put(state, id);
        if (pdx.dev.fbactors.debug.MosaicDiag.ENABLED)
            pdx.dev.fbactors.debug.MosaicDiag.logRare("note-" + id, "note entity=%s state=%s",
                entity.getUUID(), state.getClass().getSimpleName());
    }
    public static UUID begin(Object state) {
        if (isSuppressed()) return null;
        UUID before = CURRENT.get();
        UUID id = state instanceof Entity entity ? ALIASES.getOrDefault(entity.getUUID(), entity.getUUID()) : STATES.get(state);
        CURRENT.set(id);
        if (id != null && pdx.dev.fbactors.debug.MosaicDiag.ENABLED)
            pdx.dev.fbactors.debug.MosaicDiag.logRare("begin-" + id, "begin current=%s", id);
        return before;
    }
    public static void end(UUID previous) { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    public static UUID current() { return CURRENT.get(); }

    public static boolean needsOcclusion() {
        return !isSuppressed() && current() != null && ActorMosaic.needsOcclusion();
    }

    public static LookSettings look() {
        if (!FilterGpuPipeline.isGlAvailable()) return null;
        if (isSuppressed()) return null;
        if (failed || current() == null || Flashback.getReplayServer() == null || (Looks.compare && !Flashback.isExporting())) return null;
        return Effects.timeline().actorLook(Math.max(0, FlashbackActors.replayTick()), current());
    }

    public static LookSettings lookCpu() {
        if (isSuppressed()) return null;
        if (failed || current() == null || Flashback.getReplayServer() == null || (Looks.compare && !Flashback.isExporting())) return null;
        return Effects.timeline().actorLook(Math.max(0, FlashbackActors.replayTick()), current());
    }

    public static boolean render(int source, int target, int w, int h, LookSettings look) {
        if (!FilterGpuPipeline.isGlAvailable()) return false;
        if (isSuppressed()) return false;
        try {
            if (pipeline == null) pipeline = new FilterGpuPipeline();
            ActorMosaic.registerMaterial(target, current());
            LookSettings material = ActorEffectPlan.material(look);
            pipeline.material(source, target, w, h, material, Math.max(0, FlashbackActors.replayTick()) / 20f);
            return true;
        } catch (RuntimeException error) {
            failed = true;
            Effects.status = "Actor look unavailable. Reload shaders to retry.";
            FlashbackActors.LOGGER.warn("Actor look render failed", error);
            return false;
        }
    }

    public static void close() {
        if (pipeline != null) pipeline.close();
        pipeline = null;
        failed = false;
        STATES.clear();
        CURRENT.remove();
        SUPPRESSED.remove();
        ActorLookBridge.clear();
        ActorMosaic.close();
    }
}
