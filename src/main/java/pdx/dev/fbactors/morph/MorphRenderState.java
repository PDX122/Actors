package pdx.dev.fbactors.morph;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class MorphRenderState {


    private static final Logger LOGGER = LoggerFactory.getLogger("fbactors");

    private static final Map<UUID, MorphRenderInfo> BY_ENTITY = new ConcurrentHashMap<>();

    private MorphRenderState() {
    }


    public static void set(@Nullable UUID entityId, @Nullable MorphRenderInfo info) {
        if (entityId == null) {
            return;
        }
        if (info == null) {
            BY_ENTITY.remove(entityId);
        } else {
            BY_ENTITY.put(entityId, info);


            EVER_MORPHED.add(entityId);
        }
    }


    @Nullable
    public static MorphRenderInfo get(@Nullable UUID entityId) {
        if (entityId == null) {
            return null;
        }
        return BY_ENTITY.get(entityId);
    }


    public static void clear(@Nullable UUID entityId) {
        if (entityId == null) {
            return;
        }
        BY_ENTITY.remove(entityId);
    }


    public static void clearAll() {
        BY_ENTITY.clear();
    }


    public static Map<UUID, MorphRenderInfo> snapshot() {
        return Map.copyOf(BY_ENTITY);
    }


    private static final java.util.Set<UUID> EVER_MORPHED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();


    private static final Map<UUID, Boolean> LAST_SEEN = new ConcurrentHashMap<>();


    private static final Map<UUID, Long> LAST_FLAP_LOG = new ConcurrentHashMap<>();


    private static final Map<UUID, Long> LAST_VISIBLE_LOG = new ConcurrentHashMap<>();


    private static final Map<UUID, Boolean> LAST_VISIBLE = new ConcurrentHashMap<>();


    public static void noteVisibility(@Nullable UUID entityId, boolean visible) {
        if (entityId == null || !EVER_MORPHED.contains(entityId)) {
            return;
        }
        Boolean was = LAST_VISIBLE.put(entityId, visible);
        if (was == null || was == visible) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_VISIBLE_LOG.get(entityId);
        if (last != null && now - last < 5000) {
            return;
        }
        LAST_VISIBLE_LOG.put(entityId, now);
        LOGGER.warn(
            "Morph visibility for {} flipped {} -> {}; the actor is being culled or vetoed, "
                + "not un-rendered by the swap",
            entityId, was ? "visible" : "hidden", visible ? "visible" : "hidden");
    }


    public static void noteRenderLookup(@Nullable UUID entityId, boolean present) {
        if (entityId == null || (!present && !EVER_MORPHED.contains(entityId))) {
            return;
        }
        if (present) {
            EVER_MORPHED.add(entityId);
        }
        Boolean was = LAST_SEEN.put(entityId, present);
        if (was == null || was == present) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_FLAP_LOG.get(entityId);
        if (last != null && now - last < 5000) {
            return;
        }
        LAST_FLAP_LOG.put(entityId, now);
        LOGGER.warn(
            "Morph render entry for {} went {} -> {}; if the actor is blinking, "
                + "something is churning the entry (respawn loop, store reload, "
                + "or a second writer) - see the respawn lines for the same actor",
            entityId, was ? "present" : "absent", present ? "present" : "absent");
    }
}
