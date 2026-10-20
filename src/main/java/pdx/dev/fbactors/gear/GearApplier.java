package pdx.dev.fbactors.gear;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


public final class GearApplier {


    private static final Map<LivingEntity, GearLoadout> LAST_APPLIED =
        Collections.synchronizedMap(new IdentityHashMap<>());


    private static final Map<LivingEntity, Map<GearSlot, ItemStack>> ORIGINALS =
        Collections.synchronizedMap(new IdentityHashMap<>());


    private static final Set<String> WARNED = Collections.synchronizedSet(
        new LinkedHashSet<>());

    private GearApplier() {
    }


    public static void apply(@Nullable LivingEntity clone, @Nullable GearLoadout loadout) {
        if (clone == null) {
            return;
        }
        GearLoadout wanted = loadout == null ? GearLoadout.empty() : loadout;
        GearLoadout previous = LAST_APPLIED.get(clone);


        if (wanted.equals(previous)) {
            return;
        }


        Set<GearSlot> touched = new LinkedHashSet<>(wanted.slots());
        if (previous != null) {
            touched.addAll(previous.slots());
        }

        for (GearSlot slot : touched) {
            GearOverride override = wanted.overrideFor(slot);
            if (override != null && !override.isNoOp()) {
                write(clone, slot, override);
            } else {
                restore(clone, slot);
            }
        }

        if (wanted.isEmpty()) {


            LAST_APPLIED.remove(clone);
            ORIGINALS.remove(clone);
        } else {
            LAST_APPLIED.put(clone, wanted);
        }
    }


    private static void write(LivingEntity clone, GearSlot slot, GearOverride override) {
        Map<GearSlot, ItemStack> originals =
            ORIGINALS.computeIfAbsent(clone, key -> new EnumMap<>(GearSlot.class));


        if (!originals.containsKey(slot)) {
            originals.put(slot, snapshot(clone, slot));
        }
        clone.setItemSlot(slot.equipmentSlot(), stackFor(clone, override));
    }


    private static void restore(LivingEntity clone, GearSlot slot) {
        Map<GearSlot, ItemStack> originals = ORIGINALS.get(clone);
        if (originals == null) {
            return;
        }
        ItemStack original = originals.remove(slot);
        if (original != null) {
            clone.setItemSlot(slot.equipmentSlot(), original);
        }
        if (originals.isEmpty()) {
            ORIGINALS.remove(clone);
        }
    }


    public static void clear(@Nullable LivingEntity clone) {
        if (clone == null) {
            return;
        }
        Map<GearSlot, ItemStack> originals = ORIGINALS.remove(clone);
        if (originals != null) {
            for (Map.Entry<GearSlot, ItemStack> entry : originals.entrySet()) {
                clone.setItemSlot(entry.getKey().equipmentSlot(), entry.getValue());
            }
        }
        LAST_APPLIED.remove(clone);
    }


    public static void forget(@Nullable LivingEntity clone) {
        if (clone == null) {
            return;
        }
        LAST_APPLIED.remove(clone);
        ORIGINALS.remove(clone);
    }


    @Nullable
    public static GearLoadout lastAppliedTo(@Nullable LivingEntity clone) {
        return clone == null ? null : LAST_APPLIED.get(clone);
    }


    private static ItemStack snapshot(LivingEntity clone, GearSlot slot) {
        ItemStack current = clone.getItemBySlot(slot.equipmentSlot());
        return current == null ? ItemStack.EMPTY : current.copy();
    }


    private static ItemStack stackFor(LivingEntity clone, GearOverride override) {
        if (override.removesItem()) {
            return ItemStack.EMPTY;
        }
        if (override.nbt() != null) {
            ItemStack exact = Compat.itemStackFromNbt(clone.level(),
                override.itemId(), override.count(), override.nbt());
            if (!exact.isEmpty()) {
                return exact;
            }


            FlashbackActors.LOGGER.debug("Gear SNBT for {} did not parse; using id and count",
                override.itemId());
        }
        ItemStack stack = resolve(override.itemId(), override.count());
        if (stack.isEmpty() || override.enchantments().isEmpty()) {
            return stack;
        }
        if (!GearEnchantments.apply(stack, override.enchantments())) {


            FlashbackActors.LOGGER.debug("Some enchantments on {} did not apply",
                override.itemId());
        }
        return stack;
    }


    private static ItemStack resolve(String itemId, int count) {
        ItemStack stack = Compat.itemStack(itemId, count);
        if (!stack.isEmpty()) {
            return stack;
        }
        String fallback = GearPresets.fallbackFor(itemId);
        if (fallback != null) {
            stack = Compat.itemStack(fallback, count);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        if (WARNED.add(itemId)) {
            FlashbackActors.LOGGER.warn("Gear item '{}' does not exist on this version"
                + " and was skipped", itemId);
        }
        return ItemStack.EMPTY;
    }


    public static String currentItemId(@Nullable LivingEntity clone, @Nullable GearSlot slot) {
        if (clone == null || slot == null) {
            return "";
        }
        return Compat.itemId(clone.getItemBySlot(slot.equipmentSlot()));
    }


    public static boolean hasGearOn(@Nullable LivingEntity clone) {
        return clone != null && LAST_APPLIED.containsKey(clone);
    }
}
