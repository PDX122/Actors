package pdx.dev.fbactors.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;

/**
 * No-op on this target: vanilla MouseHandler.onMove accumulates absolute
 * cursor deltas unconditionally here, so there is no relative/absolute
 * branch to repair. The 26.3 copy of this mixin forces the relative
 * (SDL) deltas while Flashback holds the editor grab.
 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerRelative {
}
