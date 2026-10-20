package pdx.dev.fbactors.actor;

import pdx.dev.fbactors.compat.Compat;
import net.minecraft.world.item.ItemStack;

public final class ActorItems {

    private ActorItems() {
    }

    public static String idOf(ItemStack stack) {
        return Compat.itemId(stack);
    }
}

