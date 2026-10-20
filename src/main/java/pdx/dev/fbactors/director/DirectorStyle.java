package pdx.dev.fbactors.director;

import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.CameraMove;
import pdx.dev.fbactors.take.DirectorConfig;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;


public record DirectorStyle(
    DirectorConfig.DirectorStyle id,
    int maxMoments,
    int minGapTicks,
    int defaultHoldTicks,
    float anticipationTicks,
    float shakeIntensity,
    byte easing,
    Map<CameraKeyframe.ShotType, ShotProfile> shots,
    Map<DirectorAnalyzer.MomentKind, Float> momentBias
) {


    public record ShotProfile(
        float distanceMin, float distanceMax,
        float fovMin, float fovMax,
        float elevationDegrees,
        float preference
    ) {
    }

    public DirectorStyle {
        if (id == null) {
            id = DirectorConfig.DirectorStyle.SPIELBERG;
        }
        maxMoments = Math.max(1, maxMoments);
        minGapTicks = Math.max(0, minGapTicks);
        defaultHoldTicks = Math.max(1, defaultHoldTicks);
        anticipationTicks = Math.max(0.0f, anticipationTicks);
        shakeIntensity = Math.min(1.0f, Math.max(0.0f, shakeIntensity));
        if (easing < CameraKeyframe.EASING_LINEAR || easing > CameraKeyframe.EASING_CATMULL) {
            easing = CameraKeyframe.EASING_SMOOTH;
        }
        shots = shots == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(shots));
        momentBias = momentBias == null
            ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(momentBias));
    }


    public ShotProfile profile(CameraKeyframe.ShotType type) {
        ShotProfile profile = this.shots.get(type);
        return profile != null ? profile : BASE.get(type);
    }


    public float bias(DirectorAnalyzer.MomentKind kind) {
        Float value = this.momentBias.get(kind);
        return value == null ? 1.0f : value;
    }


    public static DirectorStyle of(DirectorConfig.DirectorStyle id) {
        DirectorStyle style = REGISTRY.get(id);
        return style != null ? style : REGISTRY.get(DirectorConfig.DirectorStyle.SPIELBERG);
    }


    private static final Map<CameraKeyframe.ShotType, ShotProfile> BASE = baseProfiles();

    private static Map<CameraKeyframe.ShotType, ShotProfile> baseProfiles() {
        EnumMap<CameraKeyframe.ShotType, ShotProfile> map = new EnumMap<>(CameraKeyframe.ShotType.class);
        map.put(CameraKeyframe.ShotType.WIDE,
            new ShotProfile(15.0f, 25.0f, 60.0f, 70.0f, 22.0f, 1.0f));
        map.put(CameraKeyframe.ShotType.MEDIUM,
            new ShotProfile(8.0f, 15.0f, 50.0f, 60.0f, 4.0f, 1.0f));
        map.put(CameraKeyframe.ShotType.CLOSE_UP,
            new ShotProfile(3.0f, 6.0f, 35.0f, 45.0f, -6.0f, 1.0f));
        map.put(CameraKeyframe.ShotType.OVER_SHOULDER,
            new ShotProfile(4.0f, 8.0f, 40.0f, 50.0f, 0.0f, 1.0f));
        map.put(CameraKeyframe.ShotType.TRACKING,
            new ShotProfile(6.0f, 10.0f, 55.0f, 65.0f, 6.0f, 1.0f));
        return Collections.unmodifiableMap(map);
    }


    private static final Map<DirectorConfig.DirectorStyle, DirectorStyle> REGISTRY = build();

    private static Map<DirectorConfig.DirectorStyle, DirectorStyle> build() {
        EnumMap<DirectorConfig.DirectorStyle, DirectorStyle> map =
            new EnumMap<>(DirectorConfig.DirectorStyle.class);


        map.put(DirectorConfig.DirectorStyle.SPIELBERG, new DirectorStyle(
            DirectorConfig.DirectorStyle.SPIELBERG, 12, 10, 60, 5.0f, 0.35f, CameraKeyframe.EASING_SMOOTH,
            profiles(1.0f, 1.0f, 0.8f, 1.1f, 1.3f, 1.1f, 1.2f),
            biases(1.2f, 1.4f, 1.0f, 1.0f, 1.0f, 1.3f)));
        map.put(DirectorConfig.DirectorStyle.NOLAN, new DirectorStyle(
            DirectorConfig.DirectorStyle.NOLAN, 8, 20, 70, 4.0f, 0.1f, CameraKeyframe.EASING_SMOOTH,
            profiles(1.15f, 1.05f, 1.6f, 1.0f, 0.7f, 0.8f, 1.4f),
            biases(1.0f, 1.3f, 1.3f, 1.1f, 1.0f, 0.7f)));
        map.put(DirectorConfig.DirectorStyle.TARANTINO, new DirectorStyle(
            DirectorConfig.DirectorStyle.TARANTINO, 6, 30, 90, 3.0f, 0.05f, CameraKeyframe.EASING_SMOOTH,
            profiles(1.0f, 1.0f, 0.6f, 0.9f, 1.6f, 1.5f, 0.9f),
            biases(1.6f, 1.2f, 1.0f, 1.3f, 0.9f, 0.9f)));
        map.put(DirectorConfig.DirectorStyle.KUBRICK, new DirectorStyle(
            DirectorConfig.DirectorStyle.KUBRICK, 9, 24, 80, 6.0f, 0.0f, CameraKeyframe.EASING_SMOOTH,
            profiles(1.0f, 1.0f, 1.4f, 1.5f, 0.4f, 0.6f, 1.0f),
            biases(0.8f, 1.1f, 1.2f, 0.9f, 1.1f, 0.9f)));
        map.put(DirectorConfig.DirectorStyle.SCORSESE, new DirectorStyle(
            DirectorConfig.DirectorStyle.SCORSESE, 10, 14, 55, 5.0f, 0.2f, CameraKeyframe.EASING_CATMULL,
            profiles(1.0f, 1.0f, 0.9f, 1.0f, 1.0f, 1.1f, 1.6f),
            biases(1.2f, 1.2f, 1.5f, 1.0f, 1.0f, 1.0f)));
        map.put(DirectorConfig.DirectorStyle.FINCHER, new DirectorStyle(
            DirectorConfig.DirectorStyle.FINCHER, 16, 8, 30, 3.0f, 0.0f, CameraKeyframe.EASING_LINEAR,
            profiles(0.9f, 1.0f, 1.0f, 1.3f, 1.2f, 0.9f, 0.8f),
            biases(1.1f, 1.2f, 1.0f, 1.0f, 1.2f, 0.8f)));
        map.put(DirectorConfig.DirectorStyle.VILLENEUVE, new DirectorStyle(
            DirectorConfig.DirectorStyle.VILLENEUVE, 7, 28, 85, 7.0f, 0.05f, CameraKeyframe.EASING_SMOOTH,
            profiles(1.25f, 1.0f, 1.7f, 1.1f, 0.5f, 0.7f, 1.0f),
            biases(0.8f, 1.1f, 1.3f, 1.0f, 1.0f, 0.9f)));
        map.put(DirectorConfig.DirectorStyle.CAMERON, new DirectorStyle(
            DirectorConfig.DirectorStyle.CAMERON, 11, 16, 50, 6.0f, 0.15f, CameraKeyframe.EASING_SMOOTH,
            profiles(1.1f, 1.0f, 1.3f, 1.0f, 0.9f, 1.0f, 1.3f),
            biases(1.2f, 1.3f, 1.1f, 1.0f, 1.0f, 0.9f)));
        map.put(DirectorConfig.DirectorStyle.WES_ANDERSON, new DirectorStyle(
            DirectorConfig.DirectorStyle.WES_ANDERSON, 10, 18, 40, 4.0f, 0.0f, CameraKeyframe.EASING_LINEAR,
            profiles(1.0f, 0.95f, 0.9f, 1.7f, 0.8f, 1.2f, 0.7f),
            biases(0.9f, 1.0f, 1.0f, 1.0f, 1.2f, 1.4f)));
        return Collections.unmodifiableMap(map);
    }


    public float moveBias(CameraMove move) {
        return switch (this.id) {
            case SPIELBERG -> switch (move) {
                case PUSH_IN, FOLLOW, LEAD -> 1.6f;
                case STATIC -> 0.5f;
                default -> 1.0f;
            };
            case NOLAN -> switch (move) {
                case CRANE, PASS_BY, ORBIT -> 1.6f;
                case PUSH_IN, PAN -> 0.6f;
                default -> 1.0f;
            };
            case TARANTINO -> switch (move) {
                case PUSH_IN, PASS_BY -> 1.8f;
                case CRANE, ORBIT, PAN -> 0.4f;
                default -> 1.0f;
            };
            case KUBRICK -> switch (move) {
                case STATIC, PUSH_IN, ORBIT -> 1.7f;
                case PAN, PASS_BY -> 0.4f;
                default -> 1.0f;
            };
            case SCORSESE -> switch (move) {
                case FOLLOW, LEAD, PASS_BY -> 1.8f;
                case STATIC -> 0.3f;
                default -> 1.0f;
            };
            case FINCHER -> switch (move) {
                case STATIC, PUSH_IN -> 1.5f;
                case ORBIT, CRANE -> 0.6f;
                default -> 1.0f;
            };
            case VILLENEUVE -> switch (move) {
                case CRANE, PULL_OUT, STATIC -> 1.7f;
                case PAN, PASS_BY -> 0.5f;
                default -> 1.0f;
            };
            case CAMERON -> switch (move) {
                case CRANE, ORBIT, LEAD -> 1.7f;
                case STATIC -> 0.4f;
                default -> 1.0f;
            };
            case WES_ANDERSON -> switch (move) {
                case STATIC, PUSH_IN, PAN -> 1.6f;
                case ORBIT, CRANE -> 0.5f;
                default -> 1.0f;
            };
        };
    }


    private static Map<CameraKeyframe.ShotType, ShotProfile> profiles(
        float distanceScale, float fovScale,
        float widePref, float mediumPref, float closePref, float overPref, float trackPref) {
        EnumMap<CameraKeyframe.ShotType, ShotProfile> map = new EnumMap<>(CameraKeyframe.ShotType.class);
        map.put(CameraKeyframe.ShotType.WIDE,
            scaled(CameraKeyframe.ShotType.WIDE, distanceScale, fovScale, widePref));
        map.put(CameraKeyframe.ShotType.MEDIUM,
            scaled(CameraKeyframe.ShotType.MEDIUM, distanceScale, fovScale, mediumPref));
        map.put(CameraKeyframe.ShotType.CLOSE_UP,
            scaled(CameraKeyframe.ShotType.CLOSE_UP, distanceScale, fovScale, closePref));
        map.put(CameraKeyframe.ShotType.OVER_SHOULDER,
            scaled(CameraKeyframe.ShotType.OVER_SHOULDER, distanceScale, fovScale, overPref));
        map.put(CameraKeyframe.ShotType.TRACKING,
            scaled(CameraKeyframe.ShotType.TRACKING, distanceScale, fovScale, trackPref));
        return map;
    }

    private static ShotProfile scaled(CameraKeyframe.ShotType type, float distanceScale,
                                      float fovScale, float preference) {
        ShotProfile base = BASE.get(type);
        return new ShotProfile(
            base.distanceMin() * distanceScale,
            base.distanceMax() * distanceScale,
            base.fovMin() * fovScale,
            base.fovMax() * fovScale,
            base.elevationDegrees(),
            preference);
    }


    private static Map<DirectorAnalyzer.MomentKind, Float> biases(
        float combat, float death, float movementStart, float shot, float item, float emoteStart) {
        EnumMap<DirectorAnalyzer.MomentKind, Float> map =
            new EnumMap<>(DirectorAnalyzer.MomentKind.class);
        for (DirectorAnalyzer.MomentKind kind : DirectorAnalyzer.MomentKind.values()) {
            map.put(kind, 1.0f);
        }
        map.put(DirectorAnalyzer.MomentKind.COMBAT, combat);
        map.put(DirectorAnalyzer.MomentKind.DEATH, death);
        map.put(DirectorAnalyzer.MomentKind.MOVEMENT_START, movementStart);
        map.put(DirectorAnalyzer.MomentKind.SHOT_FIRED, shot);
        map.put(DirectorAnalyzer.MomentKind.ITEM_USE, item);
        map.put(DirectorAnalyzer.MomentKind.EMOTE_START, emoteStart);
        return map;
    }
}

