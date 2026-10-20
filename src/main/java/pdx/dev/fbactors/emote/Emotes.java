package pdx.dev.fbactors.emote;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorClonePlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class Emotes {

    private Emotes() {
    }


    public static Path directory() {
        return Flashback.getDataDirectory().resolve("fbactors").resolve("emotes");
    }


    private static final EmoteLibrary LIBRARY = new EmoteLibrary();


    private static final Map<UUID, EmoteTrack> TRACKS = new ConcurrentHashMap<>();


    private static boolean installed = false;


    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;

        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {


            FlashbackActors.LOGGER.error(
                "Could not create the emote directory {}; custom emotes will not load or save",
                dir, e);
        }

        int loaded = LIBRARY.load(dir);
        if (loaded > 0) {
            FlashbackActors.LOGGER.info("Loaded {} custom emote(s) from {}", loaded, dir);
        }

        int tracks = loadTracks(dir);
        if (tracks > 0) {
            FlashbackActors.LOGGER.info("Loaded emote clips for {} take(s) from {}", tracks, dir);
        }

        ServerTickEvents.END_SERVER_TICK.register(Emotes::onServerTick);
    }


    public static boolean installed() {
        return installed;
    }


    public static EmoteLibrary library() {
        return LIBRARY;
    }


    public static EmoteTrack trackFor(UUID takeId) {
        return TRACKS.computeIfAbsent(takeId, id -> new EmoteTrack(id, LIBRARY));
    }


    @Nullable
    public static EmoteTrack existingTrack(UUID takeId) {
        return takeId == null ? null : TRACKS.get(takeId);
    }


    public static void forgetTakes(Iterable<UUID> takeIds) {
        if (takeIds == null) {
            return;
        }
        for (UUID takeId : takeIds) {
            if (takeId != null) {
                TRACKS.remove(takeId);
            }
        }
    }


    public static void forgetTake(UUID takeId) {
        if (takeId != null) {
            TRACKS.remove(takeId);
        }
    }


    public static void forgetAll() {
        TRACKS.clear();
    }


    @Nullable
    public static com.google.gson.JsonArray exportClips(UUID takeId) {
        EmoteTrack track = takeId == null ? null : TRACKS.get(takeId);
        if (track == null) {
            return null;
        }
        List<EmoteTrack.Clip> clips = track.clips();
        if (clips.isEmpty()) {
            return null;
        }
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (EmoteTrack.Clip clip : clips) {
            array.add(EmoteIO.encodeClip(clip));
        }
        return array;
    }


    public static int importClips(String key, com.google.gson.JsonElement value) {
        UUID takeId;
        try {
            takeId = UUID.fromString(key.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            return 0;
        }
        if (value == null || !value.isJsonArray()) {
            return 0;
        }
        List<EmoteTrack.Clip> clips = new ArrayList<>();
        for (com.google.gson.JsonElement element : value.getAsJsonArray()) {
            if (element != null && element.isJsonObject()) {
                EmoteTrack.Clip clip = EmoteIO.decodeClip(element.getAsJsonObject());
                if (clip != null) {
                    clips.add(clip);
                }
            }
        }
        if (clips.isEmpty()) {
            return 0;
        }
        trackFor(takeId).setAll(clips);
        return clips.size();
    }


    private static void onServerTick(net.minecraft.server.MinecraftServer server) {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }

        try {

            int replayTick = replay.getReplayTick();

            for (Actor actor : FlashbackActors.ACTORS.actors()) {
                try {
                    poseActor(actor, replayTick);
                } catch (RuntimeException e) {


                    FlashbackActors.LOGGER.warn(
                        "Failed to apply emote pose for actor {} at tick {}",
                        actor.take.id, replayTick, e);
                }
            }
        } finally {
            EmoteApplier.endTick();
        }
    }


    private static void poseActor(Actor actor, int replayTick) {


        Vec3 live = actor.livePosition();
        if (live == null) {
            return;
        }

        EmoteTrack track = TRACKS.get(actor.take.id);
        if (track == null || track.isEmpty()) {
            return;
        }


        int takeTick = actor.takeTickFor(replayTick);

        EmotePose pose = track.poseAt(takeTick);
        if (pose.isIdentity()) {
            return;
        }

        ActorClonePlayer clone = actor.clone();
        if (clone == null) {
            return;
        }

        EmoteApplier.apply(clone, pose, takeTick);
    }


    public static EmotePose previewPose(UUID takeId, int takeTick) {
        EmoteTrack track = existingTrack(takeId);
        if (track == null || track.isEmpty()) {
            return EmotePose.identity();
        }
        return track.poseAt(takeTick);
    }


    public static void save() {
        try {
            int written = LIBRARY.saveAll(directory());
            if (written > 0) {
                FlashbackActors.LOGGER.info("Saved {} custom emote(s) to {}",
                    written, directory());
            }
        } catch (IOException e) {
            FlashbackActors.LOGGER.error("Failed to save custom emotes to {}", directory(), e);
        }
        try {
            saveTracks(directory());
        } catch (IOException e) {
            FlashbackActors.LOGGER.error("Failed to save emote clips to {}", directory(), e);
        }
    }


    static final String TRACKS_FILE = "tracks.json";

    private static void saveTracks(Path dir) throws IOException {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        com.google.gson.JsonObject tracks = new com.google.gson.JsonObject();
        for (Map.Entry<UUID, EmoteTrack> entry : TRACKS.entrySet()) {
            List<EmoteTrack.Clip> clips = entry.getValue().clips();
            if (clips.isEmpty()) {
                continue;
            }
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            for (EmoteTrack.Clip clip : clips) {
                array.add(EmoteIO.encodeClip(clip));
            }
            tracks.add(entry.getKey().toString(), array);
        }
        root.add("tracks", tracks);
        Path file = dir.resolve(TRACKS_FILE);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting()
            .create().toJson(root), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int loadTracks(Path dir) {
        Path file = dir.resolve(TRACKS_FILE);
        if (!Files.isRegularFile(file)) {
            return 0;
        }
        int takes = 0;
        try {
            String text = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonElement root =
                com.google.gson.JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                return 0;
            }
            com.google.gson.JsonElement tracksElement = root.getAsJsonObject().get("tracks");
            if (tracksElement == null || !tracksElement.isJsonObject()) {
                return 0;
            }
            for (Map.Entry<String, com.google.gson.JsonElement> entry :
                    tracksElement.getAsJsonObject().entrySet()) {
                UUID takeId;
                try {
                    takeId = UUID.fromString(entry.getKey());
                } catch (IllegalArgumentException e) {
                    FlashbackActors.LOGGER.warn(
                        "Emote clips name '{}', which is not a take id; skipping it", entry.getKey());
                    continue;
                }
                if (!entry.getValue().isJsonArray()) {
                    continue;
                }
                List<EmoteTrack.Clip> clips = new ArrayList<>();
                for (com.google.gson.JsonElement element :
                        entry.getValue().getAsJsonArray()) {
                    if (element != null && element.isJsonObject()) {
                        EmoteTrack.Clip clip = EmoteIO.decodeClip(element.getAsJsonObject());
                        if (clip != null) {
                            clips.add(clip);
                        }
                    }
                }
                if (!clips.isEmpty()) {
                    trackFor(takeId).setAll(clips);
                    takes++;
                }
            }
        } catch (IOException | RuntimeException e) {
            FlashbackActors.LOGGER.warn("Failed to load emote clips from {}; starting empty",
                file, e);
        }
        return takes;
    }


    public static List<String> referencedEmotes() {
        java.util.TreeSet<String> ids = new java.util.TreeSet<>();
        for (EmoteTrack track : TRACKS.values()) {
            ids.addAll(track.referencedEmotes());
        }
        return List.copyOf(ids);
    }
}


