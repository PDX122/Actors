package pdx.dev.fbactors.skin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.morph.*;

public final class SkinPreviews {
    private static GameProfile selected;
    private static Object level;
    private static MorphPreviewModel model;
    private static MorphThumbnail image;
    private static final MorphPreviewPlayback playback = new MorphPreviewPlayback();
    private static long lastAt, renderedAt;
    private static float lastRotation;
    private static boolean walking, failed, closeRequested;
    private static int misses;
    private SkinPreviews() {}

    public static void beginFrame() {
        Object current = Minecraft.getInstance().level;
        if (closeRequested || current != level) { release(); closeRequested = false; level = current; }
    }

    public static void close() { closeRequested = true; }
    public static boolean unavailable() { return failed; }
    private static void release() {
        if (model != null) try { model.close(); } catch (RuntimeException | LinkageError error) {
            FlashbackActors.LOGGER.debug("Could not release skin preview", error);
        }
        selected = null; model = null; image = null; failed = false; misses = 0;
        playback.reset(); playback.cycling = false; walking = false; renderedAt = 0;
    }
    public static MorphThumbnail get(GameProfile profile, float rotation, boolean walk) {
        if (selected != profile) { release(); selected = profile; lastAt = System.nanoTime(); }
        if (profile == null || failed) return null;
        try {
            if (model == null) {
                model = MorphPreviews.renderIsolated(() -> MorphPreviewBridge.openSkin(profile));
                if (model == null) { if (++misses > 120) failed = true; return null; }
            }
            long now = System.nanoTime();
            if (walk != walking) { walking = walk; playback.select(walk ? 1 : 0); }
            var frame = playback.advance((now-lastAt)/1_000_000_000.0, model.stages());
            lastAt = now;
            if (image != null && now-renderedAt < 33_333_333L && rotation == lastRotation) return image;
            model.rotation(rotation);
            var next = MorphPreviews.renderIsolated(() -> model.render(frame));
            if (next != null) { image = next; misses = 0; }
            renderedAt = now; lastRotation = rotation;
            if (image == null && ++misses > 120) failed = true;
            return image;
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            FlashbackActors.LOGGER.warn("Could not render player skin preview", error);
            return null;
        }
    }
}
