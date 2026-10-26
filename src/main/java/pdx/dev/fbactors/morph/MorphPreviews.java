package pdx.dev.fbactors.morph;

import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.filters.ActorLooks;
import pdx.dev.fbactors.filters.FilterGpuPipeline;

public final class MorphPreviews {
    private static final MorphPreviewCache<MorphPreviewModel> CACHE = new MorphPreviewCache<>(8, 1);
    private static final MorphPreviewPlayback PLAYBACK = new MorphPreviewPlayback();
    private static Object level;
    private static boolean open;
    private static String active;
    private static MorphPreviewModel model;
    private static MorphThumbnail image;
    private static MorphPreviewPlayback.Frame shown;
    private static MorphPreviewPlayback.Frame current;
    private static long lastAt, renderedAt;
    private static boolean unavailable;
    private static int openNulls, renderNulls;
    private MorphPreviews() {}
    public static MorphPreviewPlayback playback() { return PLAYBACK; }
    public static java.util.List<String> stages() { return model == null ? java.util.List.of() : model.stages(); }
    public static int stage() { return current == null ? 0 : current.stage(); }

    /** Whether the current thread is inside a preview open/render. */
    public static boolean isPreviewing() { return ActorLooks.isSuppressed(); }

    public static void beginFrame(boolean visible) {
        var next = Minecraft.getInstance().level;
        if (next != level) {
            // World change: cached models point at the old level's dispatcher
            // and their GL textures are gone with it.
            closeLocked();
            level = next;
        } else if (!visible && open) {
            // Menu just closed: release once. Closing on *every* hidden frame
            // deletes GL textures mid-frame while ImGui may still sample the
            // last handle, and forces a full reopen storm on the next open.
            closeLocked();
        }
        open = visible;
        CACHE.beginFrame();
    }

    /** Releases cached models and the displayed image. Safe to call redundantly. */
    public static void close() {
        closeLocked();
        open = false;
    }

    private static void closeLocked() {
        CACHE.close();
        active = null;
        model = null;
        // Views are non-owning (see MorphThumbnail.view), so dropping the
        // handle is correct: the texture dies with the cache above.
        image = null;
        shown = null;
        current = null;
        unavailable = false;
        openNulls = 0;
        renderNulls = 0;
    }
    public static MorphThumbnail get(String id) {
        long now = System.nanoTime();
        if (!java.util.Objects.equals(id, active)) {
            active = id; model = null; image = null; shown = null; unavailable = false;
            openNulls = 0; renderNulls = 0;
            PLAYBACK.reset(); lastAt = now; renderedAt = 0;
        }
        if (unavailable) return null;
        try {
            if (model == null) {
                model = previewing(() -> CACHE.get(id, MorphPreviewBridge::open));
                if (model == null) {
                    // Loader miss or budget spent: retry next frame. Only give
                    // up after repeated misses (unknown id, no level) so one
                    // bad first frame never locks "Model unavailable".
                    if (++openNulls > 120) unavailable = true;
                    return image;
                }
                openNulls = 0;
            }
            double delta = (now - lastAt) / 1_000_000_000.0;
            lastAt = now;
            current = PLAYBACK.advance(delta, model.stages());
            // 33ms throttle: holds across mob and stage switches because
            // renderedAt only advances on an actual render; a switch resets
            // renderedAt to 0 so the first frame of the new mob/stage draws
            // immediately, then gates again.
            if (image != null && (current.equals(shown) || now - renderedAt < 33_333_333L)) return image;
            var fresh = previewing(() -> model.render(current));
            renderedAt = now;
            if (fresh != null) {
                image = fresh;
                shown = current;
                renderNulls = 0;
            } else if (image == null && ++renderNulls > 120) {
                // PiP texture not ready yet (first prepare still warming up):
                // retry throttled rather than locking "Model unavailable".
                unavailable = true;
            }
            return image;
        } catch (RuntimeException error) {
            unavailable = true;
            FlashbackActors.LOGGER.warn("Could not animate morph preview {}", id, error);
            return null;
        }
    }

    /**
     * Runs preview GL work with the actor-look pipeline suppressed, so the
     * preview mob's LivingEntityRenderer submit cannot note/begin actor state
     * or register mosaic materials (see ActorLooks.suppress). Covers every
     * version because all bridges funnel through here.
     */
    private static <T> T previewing(java.util.function.Supplier<T> work) {
        boolean before = ActorLooks.suppress(true);
        try {
            return guarded(work);
        } finally {
            ActorLooks.suppress(before);
        }
    }

    private static <T> T guarded(java.util.function.Supplier<T> work) {
        // The whole preview pipeline is GL. Without a current GL context
        // (Vulkan backend) the first glGet aborts the JVM, so fail here with
        // a catchable error instead; callers treat it as "preview unavailable".
        if (!FilterGpuPipeline.isGlAvailable())
            throw new IllegalStateException("Morph previews require the OpenGL graphics backend");
        // FilterGpuPipeline.State snapshots draw/read framebuffers, program, VAO,
        // active texture unit plus bindings/samplers, viewport, scissor box,
        // blend func/equation/color, depth range, logic op, pixel-store alignment,
        // color mask, polygon mode and enable flags: everything entity rendering
        // may dirty. The locals below cover legacy fixed-function state State
        // predates (depth func/mask, clear values, scissor box belt-and-braces).
        var state = new FilterGpuPipeline.State();
        int depthFunction = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
        boolean depthWrite = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        double clearDepth = org.lwjgl.opengl.GL11.glGetDouble(org.lwjgl.opengl.GL11.GL_DEPTH_CLEAR_VALUE);
        float[] clearColor = new float[4];
        org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        int[] scissor = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, scissor);
        try { return work.get(); } finally {
            org.lwjgl.opengl.GL11.glClearDepth(clearDepth);
            org.lwjgl.opengl.GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            org.lwjgl.opengl.GL11.glDepthFunc(depthFunction);
            org.lwjgl.opengl.GL11.glDepthMask(depthWrite);
            org.lwjgl.opengl.GL11.glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            state.restore();
        }
    }
}
