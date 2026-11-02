package pdx.dev.fbactors.actor;

public final class ItemActionPolicy {
    private ItemActionPolicy() {}
    public static boolean hasSpawnTrack(String id) {
        if (id == null) return false;
        return id.endsWith("_boat") || id.endsWith("_raft") || id.contains("minecart")
            || id.endsWith("_spawn_egg") || id.equals("minecraft:armor_stand")
            || id.equals("minecraft:end_crystal") || id.equals("minecraft:painting")
            || id.equals("minecraft:item_frame") || id.equals("minecraft:glow_item_frame");
    }
    public static boolean usesVanilla(String id) {
        return id != null && !id.isBlank() && !id.equals("minecraft:air")
            && !id.equals("minecraft:bow") && !id.equals("minecraft:crossbow");
    }
}
