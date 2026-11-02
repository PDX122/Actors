package pdx.dev.fbactors.filters;

import com.moulberry.flashback.Flashback;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.FlashbackActors;

public final class Looks {
    private static UUID replay;
    private static LookSettings settings = new LookSettings();
    private static long saveAt;
    private static Map<String, LookSettings> presets;
    private static FilterGpuPipeline pipeline;
    private static long previewRequested;
    private static final LookPreviewAtlas previews = new LookPreviewAtlas();
    private static long previewHandle;
    private static boolean previewFailed;
    private static boolean renderFailed;
    private static boolean reloadRequested;
    public static void requestReload(){reloadRequested=true;}
    public static void requestPreviews() { previewRequested = System.nanoTime(); }
    public static long previewTexture() { return previewHandle; }
    public static float previewAspect() { return 16f / 9; }
    public static void refreshPreviews() { previews.close(); previewHandle = 0; previewFailed = false; }
    public static boolean compare;
    public static String status = "";

    private Looks() {}

    private static Path root() { return Flashback.getDataDirectory().resolve("fbactors"); }
    private static Path file() { return root().resolve(replay.toString()).resolve("looks.json"); }

    public static LookSettings settings() {
        UUID current = FlashbackActors.loadedReplayIdOrNull();
        if (!Objects.equals(current, replay)) {
            flush();
            replay = current;
            settings = replay == null ? new LookSettings() : LookStore.load(file());
            compare = false;
            status = "";
            if (replay != null) Effects.importReplayGrade(settings);
            reload();
            status = "";
        }
        return settings;
    }

    public static void changed() {
        settings.normalize();
        status = "";
        saveAt = System.nanoTime() + 500_000_000L;
    }

    public static void apply(LookSettings look) {
        settings = look.copy();
        settings.normalize();
        compare = false;
        changed();
    }

    public static void flush() {
        if (saveAt == 0 || replay == null) return;
        if (!LookStore.save(file(), settings)) status = "Could not save this look. Check folder permissions.";
        saveAt = 0;
    }

    public static Map<String, LookSettings> presets() {
        if (presets == null) presets = LookStore.presets(root().resolve("look-presets.json"));
        return presets;
    }

    public static void savePreset(String name) { savePreset(name, settings); }
    public static void savePreset(String name, LookSettings source) {
        name = name.strip();
        if (name.isEmpty() || name.length() > 64) { status = "Use a preset name between 1 and 64 characters."; return; }
        if (!presets().containsKey(name) && presets().size() >= 100) { status = "The library is full. Remove a preset first."; return; }
        presets().put(name, source.copy());
        status = LookStore.presets(root().resolve("look-presets.json"), presets()) ? "Preset saved." : "Could not save the preset.";
    }

    public static void deletePreset(String name) {
        presets().remove(name);
        status = LookStore.presets(root().resolve("look-presets.json"), presets()) ? "Preset removed." : "Could not save the preset library.";
    }

    public static void render() {
        if(reloadRequested){reloadRequested=false;reload();}
        Effects.tick();
        settings();
        ActorMosaic.render();
        LookSettings look = Effects.timeline().evaluate(Math.max(0, FlashbackActors.replayTick()), new LookSettings());
        if (saveAt != 0 && System.nanoTime() >= saveAt) flush();
        if (!FilterGpuPipeline.isGlAvailable()) {
            renderNative(look);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        boolean showPreviews = !previewFailed && !Flashback.isExporting() && previewRequested != 0 && System.nanoTime() - previewRequested < 1_000_000_000L;
        boolean apply = !renderFailed && look.enabled && (!compare || Flashback.isExporting()) && look.intensity > 0;
        if (!apply && pipeline != null) { pipeline.close(); pipeline = null; }
        if (showPreviews && previewHandle == 0) {
            try { previewHandle = previews.texture(); }
            catch (RuntimeException error) {
                previewFailed = true;
                status = "Look previews unavailable. Reload shaders to retry.";
                FlashbackActors.LOGGER.warn("Look preview load failed", error);
            }
        }
        if (Flashback.getReplayServer() == null || client.level == null || !apply) return;
        try {
            int texture = FilterRenderBridge.colorTexture(client);
            if (texture <= 0) { status = "Looks require the OpenGL graphics backend."; return; }
            if (pipeline == null) pipeline = new FilterGpuPipeline();
            var target = FilterRenderBridge.target(client);
            float time = Math.max(0, FlashbackActors.replayTick()) / 20.0f;
            if (apply) pipeline.render(texture, target.width, target.height, look, time);
        } catch (RuntimeException error) {
            look.enabled = false;
            renderFailed = true;
            previewRequested = 0;
            previewFailed = true;
            String message = String.valueOf(error.getMessage());
            status = "Look disabled: " + message.substring(0, Math.min(300, message.length()));
            FlashbackActors.LOGGER.warn("Look render failed", error);
        }
    }

    private static void renderNative(LookSettings look) {
        try {
            boolean showPreviews = !previewFailed && !Flashback.isExporting() && previewRequested != 0
                && System.nanoTime() - previewRequested < 1_000_000_000L;
            if (showPreviews && previewHandle == 0) previewHandle = previews.texture();
            if (Flashback.getReplayServer() == null || Minecraft.getInstance().level == null
                || renderFailed) return;
            if (compare && !Flashback.isExporting()) { look = look.copy(); look.enabled = false; }
            if (!FilterRenderBridge.renderNative(look, Math.max(0, FlashbackActors.replayTick()) / 20f))
                status = "The active graphics backend does not support scene looks.";
        } catch (RuntimeException error) {
            renderFailed = true;
            status = "Look rendering failed. Reload shaders to retry.";
            FlashbackActors.LOGGER.warn("Native look render failed", error);
        }
    }

    public static void reload() {
        CustomShaderPreview.close();
        FilterRenderBridge.closeNative();
        ActorLooks.close();
        if (pipeline != null) pipeline.close();
        pipeline = null;
        previews.close();
        previewHandle = 0;
        previewFailed = false;
        renderFailed = false;
        status = "Shaders reloaded.";
    }

    public static void close() { Effects.close(); flush(); reload(); }
}
