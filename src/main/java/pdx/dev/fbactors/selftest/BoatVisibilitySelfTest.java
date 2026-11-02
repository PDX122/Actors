package pdx.dev.fbactors.selftest;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorSpawn;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BoatVisibilitySelfTest {
    private static boolean read;
    private static volatile boolean done;
    private static Path report;
    private static long began;
    private static int stage, age;
    private static volatile Entity boat;
    private static volatile pdx.dev.fbactors.actor.Actor chunkActor;
    private static volatile net.minecraft.world.level.ChunkPos viewerCenter;
    private static volatile String failure;
    private static net.minecraft.client.CameraType previousCamera;
    private BoatVisibilitySelfTest() {}
    static void arm(Path file) { read=true; done=false; report=file; began=System.currentTimeMillis(); }

    public static void tick() {
        if (!read) {
            read = true;
            String path = System.getenv("FBACTORS_BOAT_VISIBILITY_TEST");
            if (path != null && !path.isBlank()) report = Path.of(path);
            began = System.currentTimeMillis();
        }
        if (report == null || done) return;
        if (failure != null) { finish("FAIL: " + failure); return; }
        if (System.currentTimeMillis() - began > 150000) { finish("FAIL: timeout, stage=" + stage); return; }
        var replay = Flashback.getReplayServer();
        var mc = Minecraft.getInstance();
        if (replay == null || mc.level == null || mc.player == null) return;
        if (stage == 0) {
            previousCamera=mc.options.getCameraType();
            stage = 1;
            replay.execute(() -> {
                var viewer = FlashbackActors.viewerOf(replay);
                if (viewer == null) { failure = "no replay viewer"; return; }
                ActorMode.enterSetup(replay, viewer);
                ActorMode.confirmSetup();
            });
        } else if (stage == 1 && ActorMode.phase() == ActorMode.Phase.PREP) {
            stage = 2;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            mc.player.setXRot(25);
            replay.execute(() -> {
                try {
                    var viewer = FlashbackActors.viewerOf(replay);
                    var level = (ServerLevel) viewer.level();
                    boat = Compat.spawnEntityFromRecord(level, new ActorSpawn(replay.getReplayTick(),
                        "minecraft:oak_boat", viewer.getX(), viewer.getY(), viewer.getZ(), 0, 0, ""));
                    if (boat == null) { failure = "boat did not spawn on server"; return; }
                    pdx.dev.fbactors.actor.ActorWorldSimulation.track(boat);
                    viewer.startRiding(boat);
                    FlashbackActors.LOGGER.info("Boat visibility probe: server boat id={} pos={} chunk={} passenger={}",
                        boat.getId(), boat.position(), level.hasChunkAt(boat.blockPosition()), boat.getFirstPassenger());
                } catch (Exception e) { failure = e.toString(); }
            });
        } else if (stage == 2 && boat != null && ++age >= 160) {
            Entity clientBoat = mc.level.getEntity(boat.getId());
            if (clientBoat == null || mc.player.getVehicle() != clientBoat) {
                finish("FAIL: client boat=" + clientBoat + " client mount=" + mc.player.getVehicle());
                return;
            }
            String original = "minecraft:textures/entity/boat/oak.png";
            try (var input = mc.getResourceManager().getResource(Compat.boatTextureId(original)).orElseThrow().open();
                 var image = com.mojang.blaze3d.platform.NativeImage.read(input)) {
                boolean hidden = pdx.dev.fbactors.actor.BoatTextureVisibility.fullyTransparent(image.getWidth(), image.getHeight(),
                    (x, y) -> image.getLuminanceOrAlpha(x, y) & 255);
                var fallback = mc.getTextureManager().getTexture(Compat.boatTextureId("fbactors:boat_fallback/textures/entity/boat/oak.png"));
                if (hidden && !(fallback instanceof net.minecraft.client.renderer.texture.DynamicTexture)) {
                    finish("FAIL: transparent server hull was not replaced by the boat renderer");
                    return;
                }
                if (hidden) {
                    var pixels = ((net.minecraft.client.renderer.texture.DynamicTexture) fallback).getPixels();
                    if (pixels == null || pdx.dev.fbactors.actor.BoatTextureVisibility.fullyTransparent(pixels.getWidth(), pixels.getHeight(),
                        (x, y) -> pixels.getLuminanceOrAlpha(x, y) & 255)) {
                        finish("FAIL: fallback has no visible pixels");
                        return;
                    }
                }
                if (!original.equals(pdx.dev.fbactors.actor.BoatTextures.texture(original))) {
                    finish("FAIL: fallback leaked outside the authored boat render context");
                    return;
                }
                stage = 3;
                age = 0;
                replay.execute(() -> {
                    try {
                        var viewer = FlashbackActors.viewerOf(replay);
                        viewer.stopRiding();
                        FlashbackActors.RECORDER.stop();
                        ActorMode.cancelSetup();
                        viewer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                        viewerCenter = viewer.chunkPosition();
                        var level = (ServerLevel)viewer.level();
                        var take = new pdx.dev.fbactors.take.ActorTake(java.util.UUID.randomUUID(), "Chunk anchor probe",
                            Compat.dimensionId(level), replay.getReplayTick());
                        var near = new pdx.dev.fbactors.take.ActorFrame(viewer.getX(), viewer.getY(), viewer.getZ(), 0, 0, 0, (byte)0, (byte)4);
                        var far = new pdx.dev.fbactors.take.ActorFrame(near.x() + 1024, near.y(), near.z(), 0, 0, 0, (byte)0, (byte)4);
                        for (int i = 0; i < 200; i++) take.frames.add(far);
                        var actor = FlashbackActors.ACTORS.add(take);
                        actor.setClone(pdx.dev.fbactors.actor.ActorClone.spawn(replay, level, actor.profile(), near));
                        if (actor.clone() == null) throw new IllegalStateException("chunk probe clone did not spawn");
                        chunkActor = actor;
                    } catch (Exception e) { failure = e.toString(); }
                });
            } catch (Exception e) { finish("FAIL: " + e); }
        } else if (stage == 3 && chunkActor != null && ++age >= 80) {
            stage = 4;
            replay.execute(() -> {
                try {
                    var clone = chunkActor.clone();
                    var viewer = FlashbackActors.viewerOf(replay);
                    if (clone == null) throw new IllegalStateException("actor lost its clone");
                    requireCenter(clone, "actor away from viewer");
                    if (clone.requestedViewDistance() != viewer.requestedViewDistance())
                        throw new IllegalStateException("actor retained the silent player's default view distance");
                    if (!clone.getLastSectionPos().equals(net.minecraft.core.SectionPos.of(clone)))
                        throw new IllegalStateException("actor loading tickets stayed in its old section");
                    if (!viewerCenter.equals(viewer.chunkPosition()) || viewerCenter.equals(clone.chunkPosition()))
                        throw new IllegalStateException("probe did not keep actor and viewer separate");
                    var block = clone.blockPosition();
                    if (((ServerLevel)clone.level()).getChunkSource().getChunkNow((block.getX() >> 4) + 5, block.getZ() >> 4) == null)
                        throw new IllegalStateException("actor did not load chunks beyond the entity ticket radius");
                    FlashbackActors.ACTORS.remove(chunkActor);
                    finish("PASS: transparent actor hull restored; actor moved 64 chunks from stationary viewer; native loading tickets and five-chunk neighbor follow actor");
                } catch (Exception e) { failure = e.toString(); }
            });
        }
    }

    private static void requireCenter(net.minecraft.server.level.ServerPlayer viewer, String label) {
        if (!(viewer.getChunkTrackingView() instanceof net.minecraft.server.level.ChunkTrackingView.Positioned positioned)
            || !positioned.center().equals(viewer.chunkPosition()))
            throw new IllegalStateException("stale chunk center for " + label);
    }

    private static void finish(String verdict) {
        done = true;
        var mc=Minecraft.getInstance();
        mc.execute(() -> { if(previousCamera!=null) mc.options.setCameraType(previousCamera); });
        var replay=Flashback.getReplayServer();
        if(replay!=null) replay.execute(() -> {
            var viewer=FlashbackActors.viewerOf(replay);
            if(viewer!=null && ActorMode.phase()!=ActorMode.Phase.IDLE) ActorMode.finish(replay,viewer);
            if(chunkActor!=null) FlashbackActors.ACTORS.remove(chunkActor);
            if(boat!=null && !boat.isRemoved()) boat.discard();
        });
        try { Files.createDirectories(report.toAbsolutePath().getParent()); Files.writeString(report, verdict); }
        catch (Exception e) { FlashbackActors.LOGGER.warn("Cannot write boat visibility verdict", e); }
        FlashbackActors.LOGGER.info("Boat visibility probe: {}", verdict);
    }
}
