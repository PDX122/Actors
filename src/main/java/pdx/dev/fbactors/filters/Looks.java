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
    private static boolean pearlProbed;
    private static boolean vulkanNotified;
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
        Effects.tick();
        settings();
        ActorMosaic.render();
        LookSettings look = Effects.timeline().evaluate(Math.max(0, FlashbackActors.replayTick()), new LookSettings());
        if (saveAt != 0 && System.nanoTime() >= saveAt) flush();
        if (!FilterGpuPipeline.isGlAvailable()) {
            status = "Looks require the OpenGL graphics backend.";
            pearlProbeOnce();
            notifyVulkanOnce();
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

    /**
     * One-shot live check that the renderpearl fullscreen path works on this
     * driver. Runs the mc263 passthrough spike once per session (reflection so
     * other versions, which have no spike class, simply skip it) and logs the
     * outcome. Never throws; never runs twice.
     */
    private static void pearlProbeOnce() {
        if (pearlProbed) return;
        pearlProbed = true;
        try {
            Class<?> spike = Class.forName("pdx.dev.fbactors.filters.FilterPearlSpike");
            var target = FilterRenderBridge.target(Minecraft.getInstance());
            if (target == null || target.width <= 0 || target.height <= 0) return;
            boolean ok = (Boolean) spike.getMethod("runPassthrough", int.class, int.class)
                .invoke(null, target.width, target.height);
            FlashbackActors.LOGGER.info("Vulkan renderpearl fullscreen probe: {}",
                ok ? "draw submitted" : "failed, see warnings above");
        } catch (Throwable ignored) {
        }
    }

    /**
     * One-shot in-chat notice on Vulkan, where looks are degraded. Fires at
     * most once per session, only once a local player exists to read it.
     */
    private static void notifyVulkanOnce() {
        if (vulkanNotified) return;
        try {
            var client = Minecraft.getInstance();
            if (client == null || client.player == null) return;
            if (!FilterGpuPipeline.isVulkanBackend()) return;
            vulkanNotified = true;
            sendClientChat(client, net.minecraft.network.chat.Component.literal(
                "We noticed you're on Vulkan - at this moment shaders and filters will not work. -PDX"));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Version-proof client chat send: {@code displayClientMessage} on older
     * targets, the chat listener path on 26.3+ where it was removed.
     */
    private static void sendClientChat(Minecraft client, net.minecraft.network.chat.Component text) {
        var player = client.player;
        if (player == null) return;
        try {
            player.getClass()
                .getMethod("displayClientMessage", net.minecraft.network.chat.Component.class, boolean.class)
                .invoke(player, text, false);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) {
            return;
        }
        try {
            Object listener = client.gui.getClass().getMethod("chatListener").invoke(client.gui);
            if (listener == null) return;
            listener.getClass()
                .getMethod("handleSystemMessage", net.minecraft.network.chat.Component.class, boolean.class)
                .invoke(listener, text, false);
        } catch (Throwable ignored) {
        }
    }

    public static void reload() {
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
