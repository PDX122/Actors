package pdx.dev.fbactors.morph;

import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.filters.ActorLooks;
import pdx.dev.fbactors.filters.FilterGpuPipeline;

public final class MorphPreviews {
    private static final MorphPreviewCache<MorphPreviewModel> CACHE = new MorphPreviewCache<>(8, 1);
    private static final MorphPreviewCache<PosePreview> POSES = new MorphPreviewCache<>(6, 1);
    private static final class PosePreview implements AutoCloseable {
        final MorphPreviewModel model;
        MorphThumbnail image;
        int attempts;
        final MorphPreviewPlayback playback=new MorphPreviewPlayback();
        long lastAt=System.nanoTime();
        long renderedAt;
        PosePreview(MorphPreviewModel model,String pose) {
            this.model=model;
            try { model.pose(pose); }
            catch(RuntimeException | LinkageError error) { model.close(); throw error; }
        }
        MorphThumbnail image(boolean animated,boolean clocked) {
            long now=System.nanoTime();
            if(clocked) {
                playback.playing=animated; playback.cycling=false;
                playback.advance((now-lastAt)/1_000_000_000.0,1);
                lastAt=now;
            }
            if(image==null || animated && now-renderedAt>33_333_333L) {
                image=model.render(playback.advance(0,1));
                renderedAt=now;
                if(image==null && ++attempts>120) throw new IllegalStateException("Pose renderer did not produce an image");
            }
            return image;
        }
        @Override public void close() { model.close(); }
    }
    private static final MorphPreviewPlayback PLAYBACK = new MorphPreviewPlayback();
    private static Object level;
    private static boolean open;
    private static String active;
    private static String selectedPose="auto";
    private static MorphPreviewModel model;
    private static MorphThumbnail image;
    private static MorphPreviewPlayback.Frame shown;
    private static MorphPreviewPlayback.Frame current;
    private static long lastAt, renderedAt;
    private static boolean unavailable;
    private static final java.util.Set<String> failed = new java.util.HashSet<>();
    private static int openNulls, renderNulls;
    private MorphPreviews() {}
    public static MorphPreviewPlayback playback() { return PLAYBACK; }
    public static java.util.List<String> stages() {
        if (model == null) return java.util.List.of();
        try { return model.stages(); }
        catch (RuntimeException | LinkageError error) { failPreview(active, error); return java.util.List.of(); }
    }
    public static int stage() { return current == null ? 0 : current.stage(); }
    public static boolean isUnavailable() { return unavailable; }

    public static boolean isPreviewing() { return ActorLooks.isSuppressed(); }

    public static void beginFrame(boolean visible) {
        var next = Minecraft.getInstance().level;
        if (next != level) {

            closeLocked();
            level = next;
        } else if (!visible && open) {

            closeLocked();
        }
        open = visible;
        CACHE.beginFrame();
        POSES.beginFrame();
    }

    public static void close() {
        closeLocked();
        open = false;
    }

    private static void closeLocked() {
        CACHE.close();
        POSES.close();
        failed.clear();
        active = null;
        model = null;

        image = null;
        shown = null;
        current = null;
        unavailable = false;
        openNulls = 0;
        renderNulls = 0;
    }
    public static MorphThumbnail get(String id) {
        return get(id,"auto");
    }

    public static MorphThumbnail poseCard(String body,String pose) {
        return poseCard(body,pose,false,false);
    }
    public static MorphThumbnail poseCard(String body,String pose,boolean animated) {
        return poseCard(body,pose,animated,true);
    }
    private static MorphThumbnail poseCard(String body,String pose,boolean animated,boolean clocked) {
        String key=body+"/"+pose;
        if(MorphLibraryPresets.decode(key,Morph.registry())==null || poseCardFailed(body,pose) || "player".equals(body)) return null;
        try {
            var value=previewing(() -> POSES.get(key,ignored -> {
                var model=MorphPreviewBridge.open(body);
                if(model==null) failed.add("card:"+key);
                return model==null?null:new PosePreview(model,pose);
            }));
            return value==null?null:previewing(() -> value.image(animated,clocked));
        } catch(RuntimeException | LinkageError error) {
            POSES.remove(key);
            pdx.dev.fbactors.debug.ErrorRecovery.report("Pose preview: "+key,error);
            return null;
        }
    }
    public static boolean poseCardFailed(String body,String pose) {
        return failed.contains("card:"+body+"/"+pose) || pdx.dev.fbactors.debug.ErrorRecovery.FAILURES.stopped("Pose preview: "+body+"/"+pose);
    }
    public static MorphThumbnail get(String id,String pose) {
        long now = System.nanoTime();
        if (!java.util.Objects.equals(id, active)) {
            active = id; model = null; image = null; shown = null; unavailable = failed.contains(id);
            openNulls = 0; renderNulls = 0;
            PLAYBACK.reset(); lastAt = now; renderedAt = 0;
        }
        if (unavailable) return null;
        try {
            if (model == null) {
                model = previewing(() -> CACHE.get(id, MorphPreviewBridge::open));
                if (model == null) {

                    if (++openNulls > 120) unavailable = true;
                    return image;
                }
                openNulls = 0;
            }
            String wanted=MorphPoses.normalize(id,pose);
            if(!wanted.equals(selectedPose)) { selectedPose=wanted; shown=null; renderedAt=0; }
            model.pose(wanted);
            double delta = (now - lastAt) / 1_000_000_000.0;
            lastAt = now;
            current = PLAYBACK.advance(delta, model.stages());

            if (image != null && (current.equals(shown) || now - renderedAt < 33_333_333L)) return image;
            var fresh = previewing(() -> model.render(current));
            renderedAt = now;
            if (fresh != null) {
                image = fresh;
                shown = current;
                renderNulls = 0;
            } else if (image == null && ++renderNulls > 120) {

                unavailable = true;
            }
            return image;
        } catch (RuntimeException | LinkageError error) {
            failPreview(id, error);
            return null;
        }
    }

    private static void failPreview(String id, Throwable error) {
        unavailable = true;
        image = null; shown = null; current = null; model = null;
        CACHE.remove(id);
        if (failed.add(id)) FlashbackActors.LOGGER.warn("Disabled failed morph preview {} until the picker is reopened", id, error);
    }

    public static <T> T renderIsolated(java.util.function.Supplier<T> work) { return previewing(work); }

    private static <T> T previewing(java.util.function.Supplier<T> work) {
        boolean before = ActorLooks.suppress(true);
        try {
            return guarded(work);
        } finally {
            ActorLooks.suppress(before);
        }
    }

    private static <T> T guarded(java.util.function.Supplier<T> work) {

        if (!FilterGpuPipeline.isGlAvailable()) {
            if (!MorphPreviewBridge.supportsNative())
                throw new IllegalStateException("This backend cannot render morph previews");
            return work.get();
        }

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
            pdx.dev.fbactors.filters.FilterRenderBridge.synchronizeGlState();
        }
    }
}
