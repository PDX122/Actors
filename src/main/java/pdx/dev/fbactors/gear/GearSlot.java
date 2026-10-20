package pdx.dev.fbactors.gear;

import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public enum GearSlot {


    MAINHAND(EquipmentSlot.MAINHAND, "fbactors.gear.mainhand", 4, false),


    OFFHAND(EquipmentSlot.OFFHAND, "fbactors.gear.offhand", 5, false),


    HEAD(EquipmentSlot.HEAD, "fbactors.gear.head", 0, true),


    CHEST(EquipmentSlot.CHEST, "fbactors.gear.chest", 1, true),


    LEGS(EquipmentSlot.LEGS, "fbactors.gear.legs", 2, true),


    FEET(EquipmentSlot.FEET, "fbactors.gear.feet", 3, true);


    private static final GearSlot[] VALUES = values();


    private static final List<GearSlot> ARMOUR = List.of(HEAD, CHEST, LEGS, FEET);

    private static final List<GearSlot> SORTED =
        List.of(HEAD, CHEST, LEGS, FEET, MAINHAND, OFFHAND);

    private final EquipmentSlot equipmentSlot;
    private final String translationKey;
    private final int sortIndex;
    private final boolean armour;

    GearSlot(EquipmentSlot equipmentSlot, String translationKey, int sortIndex, boolean armour) {
        this.equipmentSlot = equipmentSlot;
        this.translationKey = translationKey;
        this.sortIndex = sortIndex;
        this.armour = armour;
    }


    public EquipmentSlot equipmentSlot() {
        return this.equipmentSlot;
    }


    public String translationKey() {
        return this.translationKey;
    }


    public int sortIndex() {
        return this.sortIndex;
    }


    @Nullable
    public static GearSlot fromName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        for (GearSlot slot : VALUES) {
            if (slot.name().equalsIgnoreCase(trimmed)) {
                return slot;
            }
        }
        return null;
    }


    public static List<GearSlot> armourSlots() {
        return ARMOUR;
    }


    public static List<GearSlot> sorted() {
        return SORTED;
    }


    public boolean isArmour() {
        return this.armour;
    }
}

