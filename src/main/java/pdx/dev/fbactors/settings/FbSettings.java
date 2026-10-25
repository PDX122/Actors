package pdx.dev.fbactors.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pdx.dev.fbactors.FlashbackActors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public final class FbSettings {

    private static final Path CONFIG_PATH = FlashbackActors.fbSettingsPath();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    public boolean combatEnabled = false;

    public boolean actorPacketMovement = true;


    public boolean hostDamageEnabled = false;


    public boolean voiceEnabled = false;


    public boolean morphEnabled = true;


    public boolean selfTestOnLoad = false;


    public int takeTimerSeconds = 0;


    public int countdownSeconds = 3;


    public float micGain = 1.0f;


    public boolean voiceActivation = true;


    public int voiceThreshold = -45;


    public String voiceDevice = null;


    public boolean subEnabled = false;


    public float subScale = 1.0f;


    public float[] subTextColor = {1.0f, 1.0f, 1.0f};


    public boolean subOutline = true;


    public float[] subOutlineColor = {0.0f, 0.0f, 0.0f};


    public boolean subShadow = false;


    public boolean subBackground = true;


    public float[] subBgColor = {0.0f, 0.0f, 0.0f};


    public float subBgOpacity = 0.55f;


    public int subPadding = 6;


    public int subPosition = 0;


    public boolean subSpeaker = true;


    public float[] subSpeakerColor = {1.0f, 0.85f, 0.4f};


    public int subFadeTicks = 6;


    public int subMaxLines = 2;


    public int subMaxWidth = 640;


    public String whisperBinary = "";


    public boolean stationSidebar = true;


    public String uiTheme = "flashback";


    public boolean customLightBase = false;


    public float[] customAccent = {0.30f, 0.55f, 0.90f};


    public float[] customBackground = {0.09f, 0.11f, 0.16f};


    public float customRounding = 6.0f;


    public boolean hotkeysEnabled = true;


    private static final FbSettings INSTANCE = load();

    private FbSettings() {
    }


    public static FbSettings settings() {
        return INSTANCE;
    }


    public static boolean combatEnabled() {
        return INSTANCE.combatEnabled;
    }


    public static boolean hostDamageEnabled() {
        return INSTANCE.hostDamageEnabled;
    }


    public static boolean voiceEnabled() {
        return INSTANCE.voiceEnabled;
    }


    public static boolean selfTestOnLoad() {
        return INSTANCE.selfTestOnLoad;
    }


    public static boolean morphEnabled() {
        return INSTANCE.morphEnabled;
    }


    public static boolean subtitlesEnabled() {
        return INSTANCE.subEnabled;
    }


    public static boolean stationSidebar() {
        return INSTANCE.stationSidebar;
    }


    public static boolean hotkeysEnabled() {
        return INSTANCE.hotkeysEnabled;
    }


    public static int takeTimerSeconds() {
        return INSTANCE.takeTimerSeconds;
    }


    public static int countdownSeconds() {
        return INSTANCE.countdownSeconds;
    }


    public static String uiTheme() {
        return INSTANCE.uiTheme;
    }


    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            FlashbackActors.LOGGER.error("Failed to save the FBA settings", e);
        }
    }


    private static FbSettings load() {
        if (Files.isRegularFile(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                FbSettings settings = GSON.fromJson(json, FbSettings.class);
                if (settings != null) {
                    return settings;
                }
            } catch (IOException | RuntimeException e) {
                FlashbackActors.LOGGER.warn("Failed to load the FBA settings; using defaults", e);
            }
        }
        return new FbSettings();
    }
}
