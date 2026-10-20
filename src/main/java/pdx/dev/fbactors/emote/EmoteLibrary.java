package pdx.dev.fbactors.emote;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;


public final class EmoteLibrary {


    private static final List<Emote> BUILT_IN_LIST = buildAll();

    private final Map<String, Emote> byId = new LinkedHashMap<>();


    public EmoteLibrary() {
        reset();
    }


    public static List<Emote> builtIns() {
        return BUILT_IN_LIST;
    }


    @Nullable
    public Emote byId(String id) {
        return id == null ? null : this.byId.get(id);
    }


    public boolean has(String id) {
        return id != null && this.byId.containsKey(id);
    }


    public List<Emote> all() {
        return List.copyOf(this.byId.values());
    }


    public int size() {
        return this.byId.size();
    }


    public List<String> names() {
        List<String> names = new ArrayList<>(this.byId.size());
        for (Emote emote : this.byId.values()) {
            names.add(emote.name());
        }
        return List.copyOf(names);
    }


    @Nullable
    public Emote register(Emote emote) {
        if (emote == null || emote.id() == null || emote.id().isBlank()) {

            throw new IllegalArgumentException("Cannot register an emote with no id");
        }
        return this.byId.put(emote.id(), emote);
    }


    @Nullable
    public Emote remove(String id) {
        return id == null ? null : this.byId.remove(id);
    }


    public void reset() {
        this.byId.clear();
        for (Emote emote : BUILT_IN_LIST) {
            this.byId.put(emote.id(), emote);
        }
    }


    public EmoteLibrary builtInsOnly() {
        EmoteLibrary copy = new EmoteLibrary();
        copy.byId.clear();
        for (Emote emote : this.byId.values()) {
            for (Emote builtIn : BUILT_IN_LIST) {
                if (builtIn.id().equals(emote.id())) {
                    copy.byId.put(emote.id(), builtIn);
                    break;
                }
            }
        }
        return copy;
    }


    public int load(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {

            return 0;
        }


        for (Emote emote : new ArrayList<>(this.byId.values())) {
            if (!isBuiltIn(emote.id())) {
                this.byId.remove(emote.id());
            }
        }

        int loaded = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                try {
                    register(EmoteIO.read(file));
                    loaded++;
                } catch (IOException | RuntimeException e) {
                    pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                        "Skipping unreadable emote file {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.error(
                "Failed to list emote files in {}", dir, e);
        }
        return loaded;
    }


    public int saveAll(Path dir) throws IOException {
        Files.createDirectories(dir);
        int written = 0;
        for (Emote emote : this.byId.values()) {
            if (isBuiltIn(emote.id())) {
                continue;
            }
            EmoteIO.write(dir.resolve(emote.id() + ".json"), emote);
            written++;
        }
        return written;
    }


    public static boolean isBuiltIn(String id) {
        for (Emote emote : BUILT_IN_LIST) {
            if (emote.id().equals(id)) {
                return true;
            }
        }
        return false;
    }


    public List<Emote> custom() {
        List<Emote> custom = new ArrayList<>();
        for (Emote emote : this.byId.values()) {
            if (!isBuiltIn(emote.id())) {
                custom.add(emote);
            }
        }
        return List.copyOf(custom);
    }


    public List<Emote> overrides() {
        List<Emote> overrides = new ArrayList<>();
        for (Emote emote : this.byId.values()) {
            if (isBuiltIn(emote.id())) {
                Emote builtIn = null;
                for (Emote candidate : BUILT_IN_LIST) {
                    if (candidate.id().equals(emote.id())) {
                        builtIn = candidate;
                        break;
                    }
                }
                if (builtIn != null && !builtIn.equals(emote)) {
                    overrides.add(emote);
                }
            }
        }
        return List.copyOf(overrides);
    }


    private static Key key(int tick) {
        return new Key(tick);
    }


    private static Emote emote(String id, String name, boolean loop, int duration,
            Key... keys) {
        EmoteKeyframe[] built = new EmoteKeyframe[keys.length];
        for (int i = 0; i < keys.length; i++) {
            built[i] = keys[i].build();
        }
        return new Emote(id, name, loop, duration, List.of(built));
    }


    private static final class Key {
        private final int tick;
        private double x;
        private double y;
        private double z;
        private float bodyYaw;
        private float bodyPitch;
        private float headYaw;
        private float headPitch;
        private float leftArmPitch;
        private float leftArmYaw;
        private float leftArmRoll;
        private float rightArmPitch;
        private float rightArmYaw;
        private float rightArmRoll;
        private float leftLegPitch;
        private float leftLegYaw;
        private float leftLegRoll;
        private float rightLegPitch;
        private float rightLegYaw;
        private float rightLegRoll;
        private String mode = EmoteKeyframe.SMOOTH;
        private String stance;

        Key(int tick) {
            this.tick = tick;
        }


        Key at(double ox, double oy, double oz) {
            this.x = ox;
            this.y = oy;
            this.z = oz;
            return this;
        }


        Key head(float yaw, float pitch) {
            this.headYaw = yaw;
            this.headPitch = pitch;
            return this;
        }


        Key body(float yaw, float pitch) {
            this.bodyYaw = yaw;
            this.bodyPitch = pitch;
            return this;
        }


        Key armL(float pitch, float yaw, float roll) {
            this.leftArmPitch = pitch;
            this.leftArmYaw = yaw;
            this.leftArmRoll = roll;
            return this;
        }


        Key armL(float pitch) {
            return armL(pitch, 0.0f, 0.0f);
        }


        Key armR(float pitch, float yaw, float roll) {
            this.rightArmPitch = pitch;
            this.rightArmYaw = yaw;
            this.rightArmRoll = roll;
            return this;
        }


        Key armR(float pitch) {
            return armR(pitch, 0.0f, 0.0f);
        }


        Key arms(float pitch) {
            return armL(pitch).armR(pitch);
        }


        Key legL(float pitch, float yaw, float roll) {
            this.leftLegPitch = pitch;
            this.leftLegYaw = yaw;
            this.leftLegRoll = roll;
            return this;
        }


        Key legL(float pitch) {
            return legL(pitch, 0.0f, 0.0f);
        }


        Key legR(float pitch, float yaw, float roll) {
            this.rightLegPitch = pitch;
            this.rightLegYaw = yaw;
            this.rightLegRoll = roll;
            return this;
        }


        Key legR(float pitch) {
            return legR(pitch, 0.0f, 0.0f);
        }


        Key legs(float pitch) {
            return legL(pitch).legR(pitch);
        }


        Key smooth() {
            this.mode = EmoteKeyframe.SMOOTH;
            return this;
        }


        Key linear() {
            this.mode = EmoteKeyframe.LINEAR;
            return this;
        }


        Key catmull() {
            this.mode = EmoteKeyframe.CATMULL;
            return this;
        }


        Key stance(String stance) {
            this.stance = stance;
            return this;
        }

        EmoteKeyframe build() {
            return new EmoteKeyframe(this.tick, this.x, this.y, this.z,
                this.bodyYaw, this.bodyPitch, this.headYaw, this.headPitch,
                this.leftArmPitch, this.leftArmYaw, this.leftArmRoll,
                this.rightArmPitch, this.rightArmYaw, this.rightArmRoll,
                this.leftLegPitch, this.leftLegYaw, this.leftLegRoll,
                this.rightLegPitch, this.rightLegYaw, this.rightLegRoll,
                this.mode, this.stance);
        }
    }


    private static List<Emote> buildAll() {
        List<Emote> emotes = new ArrayList<>(32);


        emotes.add(emote("idle", "Idle", true, 40,

            key(0).head(0, 0).body(0, 0).armL(4, 0, 2).armR(-4, 0, -2),
            key(20).head(3, -2).body(1, -1).armL(2, 0, 1).armR(-2, 0, -1),
            key(40).head(0, 0).body(0, 0).armL(4, 0, 2).armR(-4, 0, -2).catmull()));

        emotes.add(emote("sneak_idle", "Sneaking", true, 40,

            key(0).at(0, -0.18, 0).head(0, 6).body(0, 8)
                .armL(30, 0, 4).armR(-28, 0, -4).legL(-24, 0, 6).legR(24, 0, -6),
            key(20).at(0, -0.19, 0).head(-4, 4).body(-1, 7)
                .armL(27, 0, 3).armR(-25, 0, -3).legL(-22, 0, 5).legR(22, 0, -5),
            key(40).at(0, -0.18, 0).head(0, 6).body(0, 8)
                .armL(30, 0, 4).armR(-28, 0, -4).legL(-24, 0, 6).legR(24, 0, -6).catmull()));


        emotes.add(emote("wave", "Wave", false, 30,

            key(0).head(0, 0).armR(6, 0, 0),
            key(6).head(-6, -4).armR(-160, -20, -18),
            key(12).head(-6, -4).armR(-160, 18, 14),
            key(18).head(-6, -4).armR(-160, -20, -18),
            key(24).head(-4, -2).armR(-150, 10, 8),
            key(30).head(0, 0).armR(6, 0, 0)));

        emotes.add(emote("nod", "Nod", false, 24,

            key(0).head(0, 0),
            key(6).head(0, -16),
            key(12).head(0, 14),
            key(18).head(0, -6),
            key(24).head(0, 0)));

        emotes.add(emote("shake_head", "Shake head", false, 28,

            key(0).head(0, 0),
            key(7).head(-26, 4),
            key(14).head(26, 4),
            key(21).head(-26, 4),
            key(28).head(0, 0)));


        emotes.add(emote("salute", "Salute", false, 26,

            key(0).head(0, 0).body(0, 0).armR(-8, 0, 0),
            key(3).head(0, -2).body(0, 0).armR(-60, 0, 0).linear(),
            key(8).head(0, -4).body(0, -2).armR(-150, 0, -12),
            key(20).head(0, -4).body(0, -2).armR(-150, 0, -12),
            key(26).head(0, 0).body(0, 0).armR(-8, 0, 0)));

        emotes.add(emote("point", "Point", false, 26,

            key(0).head(0, 0).armR(-10, 0, 0).armL(8, 0, 0),
            key(7).head(-4, 0).body(-6, -2).armR(-88, -8, -4).armL(16, 0, 4),
            key(18).head(-4, 0).body(-6, -2).armR(-88, -8, -4).armL(16, 0, 4),
            key(26).head(0, 0).armR(-10, 0, 0).armL(8, 0, 0)));

        emotes.add(emote("point_up", "Point up", false, 24,

            key(0).head(0, 0).armR(-10, 0, 0),
            key(7).head(-2, -30).body(0, -4).armR(-172, -6, -2),
            key(16).head(-2, -30).body(0, -4).armR(-172, -6, -2),
            key(24).head(0, 0).armR(-10, 0, 0)));

        emotes.add(emote("thumbs_up", "Thumbs up", false, 24,

            key(0).head(0, 0).armR(-12, 0, 0),
            key(7).head(-4, 8).body(-4, -3).armR(-84, -10, -34),
            key(15).head(-4, 8).body(-4, -3).armR(-84, -10, -34),
            key(24).head(0, 0).armR(-12, 0, 0)));

        emotes.add(emote("shrug", "Shrug", false, 26,

            key(0).head(0, 0).armL(8, 0, 0).armR(-8, 0, 0),
            key(9).head(0, -6).body(0, -2).armL(-28, -34, -52).armR(28, 34, 52),
            key(19).head(0, -6).body(0, -2).armL(-28, -34, -52).armR(28, 34, 52),
            key(26).head(0, 0).armL(8, 0, 0).armR(-8, 0, 0)));

        emotes.add(emote("crossed_arms", "Crossed arms", false, 22,

            key(0).head(0, 0).armL(-72, 26, 14).armR(72, -26, -14),
            key(6).head(-4, 4).body(-2, 4).armL(-70, 24, 12).armR(70, -24, -12),
            key(15).head(-4, 4).body(-2, 4).armL(-70, 24, 12).armR(70, -24, -12),
            key(22).head(0, 0).armL(-72, 26, 14).armR(72, -26, -14)));


        emotes.add(emote("clap", "Clap", false, 24,

            key(0).head(0, 0).armL(-46, 22, 12).armR(46, -22, -12),
            key(5).head(-2, 4).body(0, 2).armL(-84, 8, 4).armR(84, -8, -4),
            key(11).head(-2, 4).body(0, 2).armL(-46, 22, 12).armR(46, -22, -12),
            key(17).head(-2, 4).body(0, 2).armL(-84, 8, 4).armR(84, -8, -4),
            key(24).head(0, 0).armL(-46, 22, 12).armR(46, -22, -12)));

        emotes.add(emote("applaud", "Applaud", true, 16,

            key(0).head(-2, 3).body(0, 2).armL(-84, 8, 4).armR(84, -8, -4),
            key(8).head(-2, 3).body(0, 2).armL(-44, 24, 13).armR(44, -24, -13),
            key(16).head(-2, 3).body(0, 2).armL(-84, 8, 4).armR(84, -8, -4).catmull()));

        emotes.add(emote("cheer", "Cheer", false, 30,

            key(0).head(0, 0).body(0, 0).arms(-12),
            key(8).at(0, 0.55, 0).head(0, -26).body(0, -10).arms(-172),
            key(16).at(0, 0.15, 0).head(0, -18).body(0, -6).arms(-168),
            key(22).at(0, 0, 0).head(0, -6).body(0, -2).arms(-60),
            key(30).head(0, 0).body(0, 0).arms(-12)));


        emotes.add(emote("laugh", "Laugh", false, 32,

            key(0).head(0, -6).body(0, -6).armL(-30, 14, 20).armR(30, -14, -20),
            key(6).head(-6, 24).body(-4, 10).armL(-40, 20, 26).armR(40, -20, -26),
            key(12).head(4, 22).body(3, 9).armL(-38, 18, 24).armR(38, -18, -24),
            key(19).head(-4, 18).body(-3, 8).armL(-36, 16, 22).armR(36, -16, -22),
            key(25).head(2, 8).body(2, 2).armL(-32, 15, 21).armR(32, -15, -21),
            key(32).head(0, -6).body(0, -6).armL(-30, 14, 20).armR(30, -14, -20)));

        emotes.add(emote("cry", "Cry", false, 36,

            key(0).head(0, 4).body(0, 2).armL(-14, 8, 6),
            key(7).head(-4, 20).body(-3, 8).armL(-96, 18, 8),
            key(14).head(3, 18).body(2, 7).armL(-94, 16, 7),
            key(22).head(-2, 14).body(-2, 5).armL(-92, 17, 8),
            key(29).head(0, 9).body(0, 3).armL(-88, 16, 7),
            key(36).head(0, 4).body(0, 2).armL(-14, 8, 6)));

        emotes.add(emote("facepalm", "Facepalm", false, 30,

            key(0).head(0, 0).body(0, 0).armR(-14, 0, 0),
            key(8).head(-8, 6).body(-4, 4).armR(-120, -26, -18),
            key(15).head(-14, 16).body(-8, 8).armR(-152, -18, -8),
            key(24).head(-14, 16).body(-8, 8).armR(-152, -18, -8),
            key(30).head(0, 0).body(0, 0).armR(-14, 0, 0)));

        emotes.add(emote("angry", "Angry", false, 34,

            key(0).head(0, 0).body(0, 0),
            key(9).head(-6, 14).body(-4, 14).armL(18, 12, 4).armR(-18, -12, -4),
            key(20).head(-6, 14).body(-4, 14).armL(18, 12, 4).armR(-18, -12, -4),
            key(27).head(-3, 8).body(-2, 8),
            key(34).head(0, 0).body(0, 0)));


        emotes.add(emote("bow", "Bow", false, 36,

            key(0).head(0, 0).body(0, 0).armL(-6, 0, 0).armR(6, 0, 0),
            key(14).head(0, 22).body(0, 62).armL(-16, 8, 2).armR(16, -8, -2),
            key(22).head(0, 22).body(0, 62).armL(-16, 8, 2).armR(16, -8, -2),
            key(36).head(0, 0).body(0, 0).armL(-6, 0, 0).armR(6, 0, 0)));

        emotes.add(emote("think", "Think", false, 40,

            key(0).head(0, 0).body(0, 0).armL(-10, 0, 0).armR(10, 0, 0),
            key(12).head(-16, 8).body(-6, 4).armL(-104, 22, 16).armR(-38, -10, -14),
            key(28).head(-18, 10).body(-6, 4).armL(-106, 23, 17).armR(-40, -11, -15),
            key(40).head(0, 0).body(0, 0).armL(-10, 0, 0).armR(10, 0, 0)));

        emotes.add(emote("taunt", "Taunt", false, 32,

            key(0).head(0, 0).body(0, 0).arms(-10),
            key(7).head(-10, -8).body(-8, -4).armL(-24, -30, -46).armR(24, 30, 46),
            key(14).head(6, -6).body(6, -2).armL(-20, 26, 42).armR(20, -26, -42),
            key(22).head(-8, -6).body(-6, -4).armL(-24, -30, -46).armR(24, 30, 46),
            key(32).head(0, 0).body(0, 0).arms(-10)));


        emotes.add(emote("sleep", "Sleep", true, 60,

            key(0).at(0, -0.62, 0).head(6, 12).body(0, 78).arms(-24).legs(6),
            key(30).at(0, -0.63, 0).head(8, 14).body(0, 80).arms(-20).legs(4),
            key(60).at(0, -0.62, 0).head(6, 12).body(0, 78).arms(-24).legs(6).catmull()));

        emotes.add(emote("sit", "Sit", false, 40,


            key(0).head(0, 0).body(0, 0).arms(-8).stance("sitting"),
            key(12).at(0, -0.12, 0).head(0, 6).body(0, 14)
                .armL(-52, 0, 6).armR(52, 0, -6).legL(-70).legR(-70).stance("sitting")));

        emotes.add(emote("run", "Run", true, 20,


            key(0).body(0, 14).arms(-64).legL(-58).legR(46),
            key(10).body(0, 14).arms(-46).legL(46).legR(-58),
            key(20).body(0, 14).arms(-64).legL(-58).legR(46).catmull()));

        return List.copyOf(emotes);
    }
}


