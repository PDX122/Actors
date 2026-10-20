package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.editor.ui.windows.PreferencesWindow;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PreferencesWindow.class, remap = false)
public class MixinPreferencesWindow {
}


