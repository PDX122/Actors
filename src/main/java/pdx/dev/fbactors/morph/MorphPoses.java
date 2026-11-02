package pdx.dev.fbactors.morph;

import java.util.List;

public final class MorphPoses {
    private MorphPoses() {}
    private static final java.util.Set<String> SEATED_HUMANOIDS=java.util.Set.of(
        "zombie","husk","parched","drowned","zombie_villager","giant",
        "skeleton","stray","bogged","wither_skeleton","piglin","piglin_brute","zombified_piglin");
    public static List<String> choices(String morph) {
        if(morph==null) return List.of("auto");
        String id=morph.startsWith("minecraft:")?morph.substring(10):morph;
        if(id.equals("player") || id.startsWith("custom_")) return List.of("auto");
        if(id.equals("cat")) return List.of("auto","idle","standing","walking","running","sitting","relaxing");
        if(java.util.Set.of("wolf","dog","parrot").contains(id)) return List.of("auto","idle","standing","walking","running","sitting");
        if(SEATED_HUMANOIDS.contains(id)) return List.of("auto","idle","standing","walking","running","crouching","sitting");
        var builtin=MorphRegistry.builtIns().stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
        if(builtin!=null && "projectile".equals(builtin.kind())) return List.of("auto");
        return List.of("auto","idle","walking","running");
    }
    public static float speed(String action) {
        return switch(action) { case "walking" -> .45f; case "running" -> .9f; default -> 0; };
    }
    public static boolean animated(String action) { return speed(action)>0; }
    public static String normalize(String morph, String pose) {
        return pose != null && choices(morph).contains(pose) ? pose : "auto";
    }
    public static String resolve(String pose,boolean crouching,boolean canSit) {
        return "auto".equals(pose) && crouching && canSit ? "sitting" : pose;
    }
    public static String preview(String morph,String selected,String animation) {
        String pose=normalize(morph,selected);
        if(!"auto".equals(pose)) return pose;
        if("Sitting".equals(animation)) return normalize(morph,"sitting");
        if("Standing".equals(animation)) return normalize(morph,"standing");
        return "auto";
    }
    public static String label(String pose) {
        return switch (pose) { case "idle" -> "Idle"; case "walking" -> "Walk"; case "running" -> "Run"; case "crouching" -> "Crouch"; case "standing" -> "Standing"; case "sitting" -> "Sitting"; case "relaxing" -> "Relaxing"; default -> "Auto"; };
    }
    public static String description(String pose) {
        return switch(pose) {
            case "idle" -> "Keep the legs still while native idle motion continues.";
            case "walking" -> "Play a walking cycle without changing the recorded movement path.";
            case "running" -> "Play a running cycle without changing the recorded movement path.";
            case "crouching" -> "Hold a crouched body pose.";
            case "standing" -> "Stay upright, even when the recorded actor crouches.";
            case "sitting" -> "Hold a seated pose and keep the legs still. Humanoids sit as if on a chair; animals use their native sit.";
            case "relaxing" -> "Lie down in the cat's native relaxing pose.";
            default -> "Follow the actor's recorded movement and native idle animation.";
        };
    }
}
