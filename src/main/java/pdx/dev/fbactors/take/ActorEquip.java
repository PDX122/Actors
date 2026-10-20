package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;


public record ActorEquip(int tick, byte slot, String itemId, int count, @Nullable String nbt) {


    public boolean isEmpty() {
        return this.itemId == null || this.itemId.isEmpty();
    }


    public boolean hasComponents() {
        return this.nbt != null && !this.nbt.isEmpty();
    }


    public ActorEquip(int tick, byte slot, String itemId, int count) {
        this(tick, slot, itemId, count, null);
    }
}

