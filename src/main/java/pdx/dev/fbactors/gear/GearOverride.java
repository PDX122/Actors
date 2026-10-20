package pdx.dev.fbactors.gear;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


public record GearOverride(GearSlot slot, String itemId, int count,
                           Map<String, Integer> enchantments, String nbt, boolean enabled) {


    public GearOverride {
        Objects.requireNonNull(slot, "gear override slot");


        String id = itemId == null ? "" : itemId.trim();


        Map<String, Integer> safe = new LinkedHashMap<>();
        if (enchantments != null) {
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                String key = entry.getKey();
                Integer level = entry.getValue();
                if (key == null || key.isBlank() || level == null || level <= 0) {
                    continue;
                }
                safe.put(key.trim(), level);
            }
        }

        int safeCount = Math.max(1, count);


        if (id.isEmpty()) {
            safe.clear();
            safeCount = 1;
        }


        String safeNbt = nbt == null || nbt.isBlank() || id.isEmpty() ? null : nbt;

        itemId = id;
        count = safeCount;


        enchantments = safe.isEmpty() ? Map.of() : Map.copyOf(safe);
        nbt = safeNbt;
    }


    public static GearOverride empty(GearSlot slot) {
        return new GearOverride(slot, "", 1, Map.of(), null, true);
    }


    public static GearOverride of(GearSlot slot, String itemId, int count) {
        return new GearOverride(slot, itemId, count, Map.of(), null, true);
    }


    public static GearOverride of(GearSlot slot, String itemId, int count,
                                  Map<String, Integer> enchantments) {
        return new GearOverride(slot, itemId, count, enchantments, null, true);
    }


    public static GearOverride ofSnbt(GearSlot slot, String itemId, int count, String nbt) {
        return new GearOverride(slot, itemId, count, Map.of(), nbt, true);
    }


    public boolean removesItem() {
        return this.itemId.isEmpty();
    }


    public boolean isNoOp() {
        return !this.enabled;
    }

    public GearOverride withEnabled(boolean value) {
        return value == this.enabled ? this : new GearOverride(
            this.slot, this.itemId, this.count, this.enchantments, this.nbt, value);
    }


    public GearOverride withEnchantment(String enchantmentId, int level) {
        if (enchantmentId == null || enchantmentId.isBlank() || level <= 0) {
            return this;
        }
        String key = enchantmentId.trim();
        Integer existing = this.enchantments.get(key);
        if (existing != null && existing == level) {
            return this;
        }
        Map<String, Integer> next = new LinkedHashMap<>(this.enchantments);
        next.put(key, level);
        return new GearOverride(this.slot, this.itemId, this.count, next, this.nbt, this.enabled);
    }


    public GearOverride withNbt(String snbt) {
        String want = snbt == null || snbt.isBlank() ? null : snbt;
        if (Objects.equals(want, this.nbt)) {
            return this;
        }
        return new GearOverride(this.slot, this.itemId, this.count, this.enchantments,
            want, this.enabled);
    }
}
