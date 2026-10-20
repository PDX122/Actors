package pdx.dev.fbactors.morph;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;


public final class MorphApplier {


    private static final Map<LivingEntity, Applied> LAST_APPLIED =
        Collections.synchronizedMap(new IdentityHashMap<>());


    private static final Map<LivingEntity, Map<EquipmentSlot, ItemStack>> ORIGINALS =
        Collections.synchronizedMap(new IdentityHashMap<>());


    private static final List<EquipmentSlot> SLOTS = List.of(
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET);

    private MorphApplier() {
    }

    public static void apply(@Nullable LivingEntity clone, @Nullable MorphDefinition morph,
        @Nullable MorphSelection selection) {
        if (clone == null) {
            return;
        }


        float scale = (morph == null ? 1.0f : morph.scale())
            * (selection == null ? 1.0f : selection.scale());
        float alpha = selection == null ? 1.0f : selection.alpha();
        boolean hideName = selection != null && selection.hideName();


        Applied wanted = new Applied(
            morph == null ? null : morph.id(),
            morph == null ? null : morph.stance().pose(),
            scale,
            alpha,
            hideName,
            morph != null && morph.quadruped());

        Applied previous = LAST_APPLIED.get(clone);
        if (wanted.equals(previous)) {


            if (morph != null && wanted.pose() != null && clone.getPose() != wanted.pose()) {
                writePose(clone, morph);
            }
            return;
        }

        if (morph == null) {
            restore(clone, previous);
            LAST_APPLIED.remove(clone);
            ORIGINALS.remove(clone);
            MorphRenderState.clear(clone.getUUID());
            return;
        }

        writePose(clone, morph);
        writeName(clone, morph, hideName);
        writeVisibility(clone, alpha);
        writeEquipment(clone, morph, previous);
        writeRideHeight(clone, morph);

        LAST_APPLIED.put(clone, wanted);
        MorphRenderState.set(clone.getUUID(),
            new MorphRenderInfo(morph.id(), scale, alpha));
    }


    private static void writePose(LivingEntity clone, MorphDefinition morph) {
        Pose pose = morph.stance().pose();
        if (pose == Pose.FALL_FLYING && !morph.elytraCapable()) {
            return;
        }
        boolean needsReassert = morph.stance().horizontal() && !morph.swimmer();
        if (needsReassert || clone.getPose() != pose) {
            clone.setPose(pose);
        }
    }


    private static void writeName(LivingEntity clone, MorphDefinition morph, boolean hideName) {
        String name = morph.displayName() == null || morph.displayName().isBlank()
            ? morph.id() : morph.displayName();
        clone.setCustomName(Component.literal(name));

        clone.setCustomNameVisible(!hideName);
    }


    private static void writeVisibility(LivingEntity clone, float alpha) {
        clone.setInvisible(alpha <= 0.0f);
    }

    private static void writeEquipment(LivingEntity clone, MorphDefinition morph,
        @Nullable Applied previous) {
        boolean shouldClear = morph.quadruped();
        boolean wasCleared = previous != null && previous.quadruped();

        if (!shouldClear && !wasCleared) {

            return;
        }
        if (shouldClear) {
            clearEquipment(clone);
            return;
        }

        restoreEquipment(clone);
    }


    public static void clearEquipment(LivingEntity clone) {
        Map<EquipmentSlot, ItemStack> originals =
            ORIGINALS.computeIfAbsent(clone, key -> new EnumMap<>(EquipmentSlot.class));
        for (EquipmentSlot slot : SLOTS) {
            ItemStack current = clone.getItemBySlot(slot);
            if (current == null || current.isEmpty()) {
                continue;
            }

            if (!originals.containsKey(slot)) {
                originals.put(slot, current.copy());
            }
            clone.setItemSlot(slot, ItemStack.EMPTY);
        }
    }


    private static void restoreEquipment(LivingEntity clone) {
        Map<EquipmentSlot, ItemStack> originals = ORIGINALS.remove(clone);
        if (originals == null) {
            return;
        }
        for (Map.Entry<EquipmentSlot, ItemStack> entry : originals.entrySet()) {
            clone.setItemSlot(entry.getKey(), entry.getValue());
        }
    }

    private static void writeRideHeight(LivingEntity clone, MorphDefinition morph) {
        if (morph.rideOffset() <= 0.0 && clone.isPassenger()) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.debug(
                "Morph {} is not tall enough to seat a rider; leaving the mount as is",
                morph.id());
        }
    }


    private static void restore(LivingEntity clone, @Nullable Applied previous) {
        if (previous == null) {

            return;
        }
        if (previous.pose() != Pose.STANDING) {
            clone.setPose(Pose.STANDING);
        }
        restoreEquipment(clone);
    }

    public static void forget(@Nullable LivingEntity clone) {
        if (clone == null) {
            return;
        }
        LAST_APPLIED.remove(clone);
        ORIGINALS.remove(clone);
        MorphRenderState.clear(clone.getUUID());
    }


    @Nullable
    public static String morphOn(@Nullable LivingEntity clone) {
        if (clone == null) {
            return null;
        }
        Applied applied = LAST_APPLIED.get(clone);
        return applied == null ? null : applied.morphId();
    }


    public static float scaleOn(@Nullable LivingEntity clone) {
        if (clone == null) {
            return 0.0f;
        }
        Applied applied = LAST_APPLIED.get(clone);
        return applied == null ? 0.0f : applied.scale();
    }


    private record Applied(
        @Nullable String morphId,
        @Nullable Pose pose,
        float scale,
        float alpha,
        boolean hideName,
        boolean quadruped) {
    }
}
