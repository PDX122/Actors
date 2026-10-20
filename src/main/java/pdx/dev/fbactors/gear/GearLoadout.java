package pdx.dev.fbactors.gear;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;


public record GearLoadout(String id, String name, Map<GearSlot, GearOverride> overrides) {


    private static final GearLoadout EMPTY = new GearLoadout("none", "None", Map.of());


    public GearLoadout {
        id = id == null || id.isBlank() ? "none" : id.trim();
        name = name == null || name.isBlank() ? id : name.trim();


        Map<GearSlot, GearOverride> copy = new EnumMap<>(GearSlot.class);
        if (overrides != null) {
            for (Map.Entry<GearSlot, GearOverride> entry : overrides.entrySet()) {
                GearSlot slot = entry.getKey();
                GearOverride value = entry.getValue();
                if (slot == null || value == null) {
                    continue;
                }
                if (value.isNoOp()) {


                    continue;
                }
                if (value.slot() != slot) {


                    throw new IllegalArgumentException(
                        "gear override keyed by " + slot + " claims slot " + value.slot());
                }
                copy.put(slot, value);
            }
        }
        overrides = copy.isEmpty() ? Map.of() : Collections.unmodifiableMap(copy);
    }


    public static GearLoadout empty() {
        return EMPTY;
    }


    public boolean isEmpty() {
        return this.overrides.isEmpty();
    }

    public GearOverride overrideFor(GearSlot slot) {
        return this.overrides.get(slot);
    }


    public java.util.Set<GearSlot> slots() {
        return this.overrides.keySet();
    }

    public static GearLoadout merge(@Nullable GearLoadout base, @Nullable GearLoadout over) {
        GearLoadout bottom = base == null ? EMPTY : base;
        GearLoadout top = over == null ? null : over;


        if (top == null || top.isEmpty()) {
            return bottom;
        }

        Map<GearSlot, GearOverride> merged = new EnumMap<>(GearSlot.class);
        merged.putAll(bottom.overrides);

        for (Map.Entry<GearSlot, GearOverride> entry : top.overrides.entrySet()) {
            GearSlot slot = entry.getKey();
            GearOverride incoming = entry.getValue();


            if (incoming.isNoOp()) {
                continue;
            }


            merged.put(slot, incoming);
        }

        return new GearLoadout(
            top.id,
            top.name,
            merged.isEmpty() ? Map.of() : merged);
    }


    public GearLoadout overlay(@Nullable GearLoadout over) {
        return merge(this, over);
    }


    public GearLoadout with(GearOverride override) {
        if (override == null) {
            return this;
        }
        if (this.overrides.isEmpty() && override.isNoOp()) {
            return this;
        }
        Map<GearSlot, GearOverride> next = new EnumMap<>(GearSlot.class);
        next.putAll(this.overrides);
        next.put(override.slot(), override);
        return new GearLoadout(this.id, this.name, next);
    }

    @Override
    public String toString() {
        return "GearLoadout[" + this.id + ' ' + this.name + ' '
            + this.overrides.keySet() + ']';
    }
}
