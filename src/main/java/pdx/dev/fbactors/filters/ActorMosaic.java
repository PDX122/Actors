package pdx.dev.fbactors.filters;

import com.moulberry.flashback.Flashback;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import pdx.dev.fbactors.FlashbackActors;

public final class ActorMosaic {
    private static final Map<Integer, UUID> MATERIALS = new HashMap<>();
    private static final Map<UUID, ActorDepthMask> MASKS = new HashMap<>();
    private static FilterGpuPipeline pipeline;
    private static boolean active;
    private static boolean failed;
    private static int color;
    private static int width;
    private static int height;
    private ActorMosaic() {}

    public static boolean isCapturing() { return active && !failed; }

    public static void registerMaterial(int texture, UUID actor) {
        if (actor == null) return;
        MATERIALS.put(texture, actor);
        if (pdx.dev.fbactors.debug.MosaicDiag.ENABLED)
            pdx.dev.fbactors.debug.MosaicDiag.logRare("reg-" + actor, "registerMaterial tex=%d actor=%s", texture, actor);
    }
    public static void unregisterMaterial(int texture) { MATERIALS.remove(texture); }

    public static void beginFrame() {
        active = false;
        if (!FilterGpuPipeline.isGlAvailable()) return;
        MASKS.values().forEach(ActorDepthMask::beginFrame);
        if (failed || Flashback.getReplayServer() == null || Looks.compare && !Flashback.isExporting()) return;
        int tick = Math.max(0, FlashbackActors.replayTick());
        boolean needed = Effects.timeline().clips.stream().anyMatch(clip -> clip.actor != null && clip.enabled
            && clip.look.enabled && clip.look.filters.contains("tessera") && tick >= clip.start && tick < clip.end);
        if (pdx.dev.fbactors.debug.MosaicDiag.ENABLED) {
            long actorClips = Effects.timeline().clips.stream().filter(clip -> clip.actor != null && clip.enabled
                && clip.look.enabled && clip.look.filters.contains("tessera") && tick >= clip.start && tick < clip.end).count();
            pdx.dev.fbactors.debug.MosaicDiag.logRare("beginFrame",
                "tick=%d needed=%b actorClips=%d masks=%d materials=%d color=%d %dx%d",
                tick, needed, actorClips, MASKS.size(), MATERIALS.size(), color, width, height);
        }
        if (!needed) return;
        var client = Minecraft.getInstance();
        color = FilterRenderBridge.colorTexture(client);
        var target = FilterRenderBridge.target(client);
        width = target.width;
        height = target.height;
        active = color > 0 && width > 0 && height > 0;
    }

    private static int lastCapProg = Integer.MIN_VALUE;
    private static int lastCapTex = Integer.MIN_VALUE;
    private static long lastCapLog;

    public static void capture(Runnable draw) {
        // The morph preview renders through the same draw path while the menu
        // is open. Capturing it would re-enter this draw inside the preview's
        // PiP target and register the preview texture as an actor material,
        // so the world mosaic pass that follows mis-shades real mobs.
        if (ActorLooks.isSuppressed() || !active) return;
        if (!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) return;
        if (pdx.dev.fbactors.debug.MosaicDiag.ENABLED) {
            int program = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
            int bound = ActorMaterialSampler.findTexture(program, MATERIALS::containsKey);
            UUID seen = MATERIALS.get(bound);
            int drawFb = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int attachment = drawFb == 0 ? -1 : GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            String lookOk = "?";
            if (seen != null) {
                var look = Effects.timeline().actorLook(Math.max(0, FlashbackActors.replayTick()), seen);
                lookOk = look == null ? "null" : (look.filters.contains("tessera") && look.intensity > 0 ? "YES" : "no-tessera");
            }
            long now = System.nanoTime();
            if (program != lastCapProg || bound != lastCapTex || now - lastCapLog > 5_000_000_000L) {
                lastCapProg = program;
                lastCapTex = bound;
                lastCapLog = now;
                pdx.dev.fbactors.debug.MosaicDiag.log(
                    "capture prog=%d boundTex=%d actor=%s drawFb=%d attach=%d colorFb=%d materials=%d look=%s",
                    program, bound, String.valueOf(seen), drawFb, attachment, color,
                    MATERIALS.size(), lookOk);
            }
        }
        try {
            int program = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
        UUID actor = MATERIALS.get(ActorMaterialSampler.findTexture(program, MATERIALS::containsKey));
        if (actor == null) return;
        // Player skins use OIT in 26.3: its color buffers differ from the main
        // scene, but its depth attachment still identifies world geometry.
        if (!ActorDepthMask.matchesTarget(color, FilterRenderBridge.depthTexture(Minecraft.getInstance()))) return;
        var look = Effects.timeline().actorLook(Math.max(0, FlashbackActors.replayTick()), actor);
        if (look == null || !look.filters.contains("tessera") || look.intensity <= 0) return;
        try {
            ActorDepthMask mask = MASKS.get(actor);
            if (mask != null && (mask.width != width || mask.height != height)) { mask.close(); MASKS.remove(actor); mask = null; }
            if (mask == null) {
                if (MASKS.size() >= 32) { Effects.status = "Mosaic supports up to 32 visible actors at once."; return; }
                mask = new ActorDepthMask(width, height, FilterRenderBridge.reverseDepth());
                MASKS.put(actor, mask);
            }
            mask.capture(draw);
            } catch (RuntimeException error) { fail(error); }
        } catch (RuntimeException error) { fail(error); }
    }

    public static void render() {
        if (pdx.dev.fbactors.debug.MosaicDiag.ENABLED) {
            int seen = 0;
            for (var mask : MASKS.values()) if (mask.seen()) seen++;
            pdx.dev.fbactors.debug.MosaicDiag.logRare("render",
                "active=%b failed=%b masks=%d seen=%d", active, failed, MASKS.size(), seen);
        }
        if (active && !failed) {
            var client = Minecraft.getInstance();
            int depth = FilterRenderBridge.depthTexture(client);
            if (depth > 0) {
                try {
                    if (pipeline == null) pipeline = new FilterGpuPipeline();
                    for (var entry : MASKS.entrySet()) {
                        if (!entry.getValue().seen()) continue;
                        var look = Effects.timeline().actorLook(Math.max(0, FlashbackActors.replayTick()), entry.getKey());
                        if (look == null || !look.filters.contains("tessera")) continue;
                        var param = FilterCatalog.params("tessera").getFirst();
                        pipeline.actorMosaic(color, entry.getValue().texture(), depth, width, height,
                            look.parameter("tessera", param), look.intensity, entry.getValue().reverse);
                    }
                } catch (RuntimeException error) { fail(error); }
            }
        }
        Iterator<ActorDepthMask> masks = MASKS.values().iterator();
        while (masks.hasNext()) { var mask = masks.next(); if (!mask.seen()) { mask.close(); masks.remove(); } }
        active = false;
    }

    private static void fail(RuntimeException error) {
        failed = true;
        Effects.status = "Actor mosaic unavailable. Reload shaders to retry.";
        FlashbackActors.LOGGER.warn("Actor mosaic render failed", error);
    }

    public static void close() {
        MASKS.values().forEach(ActorDepthMask::close);
        MASKS.clear();
        MATERIALS.clear();
        ActorMaterialSampler.clear();
        if (pipeline != null) pipeline.close();
        pipeline = null;
        active = failed = false;
    }
}
