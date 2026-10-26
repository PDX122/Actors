package pdx.dev.fbactors.morph;

import net.minecraft.world.entity.LivingEntity;

public final class MorphPreviewVariants {
    private MorphPreviewVariants() {}
    public static java.util.List<String> stages(LivingEntity mob) {
        var result = new java.util.ArrayList<String>();
        if (mob instanceof net.minecraft.world.entity.animal.sheep.Sheep) {
            for (var color : net.minecraft.world.item.DyeColor.values()) result.add("Wool: " + color.name().toLowerCase(java.util.Locale.ROOT));
            result.add("Sheared");
        }
        if (mob instanceof net.minecraft.world.entity.monster.Slime) result.addAll(java.util.List.of("Small", "Medium", "Large"));
        if (mob instanceof net.minecraft.world.entity.TamableAnimal) result.addAll(java.util.List.of("Sitting", "Standing"));
        return java.util.List.copyOf(result);
    }
    public static void apply(LivingEntity mob, String stage) {
        if (mob instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep) {
            sheep.setSheared(stage.equals("Sheared"));
            if (stage.startsWith("Wool: ")) sheep.setColor(net.minecraft.world.item.DyeColor.valueOf(stage.substring(6).toUpperCase(java.util.Locale.ROOT)));
            else sheep.setColor(net.minecraft.world.item.DyeColor.WHITE);
        }
        if (mob instanceof net.minecraft.world.entity.monster.Slime slime) {
            int size = stage.equals("Large") ? 4 : stage.equals("Medium") ? 2 : 1;
            // Heal flag off: setSize(size, true) heals to max health, which would fight
            // the animator's per-stage health scaling applied just below. With false
            // the resize (entityData + own attributes + own dimensions) is identical
            // and the animator overwrites health right after, so the visual result
            // is unchanged. No level broadcast on either path for a non-added mob.
            if (slime.getSize() != size) slime.setSize(size, false);
        }
        if (mob instanceof net.minecraft.world.entity.TamableAnimal tame) tame.setInSittingPose(stage.equals("Sitting"));
    }
}
