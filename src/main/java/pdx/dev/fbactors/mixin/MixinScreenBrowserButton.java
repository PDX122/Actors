package pdx.dev.fbactors.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.menu.FbactorsMenuScreen;


@Mixin(Screen.class)
public abstract class MixinScreenBrowserButton {

    @Shadow
    protected abstract <T extends GuiEventListener & Renderable & NarratableEntry>
        T addRenderableWidget(T widget);

    @Shadow
    public int width;

    @Shadow
    public int height;


    @Unique
    private Button fbactors$button;


    @Unique
    private static boolean fbactors$keysLogged;


    @Unique
    private static final boolean FBACTORS_TITLE_BUTTON_ENABLED = false;

    @Inject(method = "init", at = @At("TAIL"))
    private void fbactors$addBrowserButton(CallbackInfo ci) {
        if (!FBACTORS_TITLE_BUTTON_ENABLED) {
            return;
        }
        if (!((Object) this instanceof TitleScreen self)) {
            return;
        }
        if (this.fbactors$button != null && self.children().contains(this.fbactors$button)) {
            return;
        }
        logKeysOnce(self);
        int[] anchor = anchor(self);
        if (anchor == null) {
            return;
        }
        Button browser = Button.builder(Component.literal("FB"),
                pressed -> pdx.dev.fbactors.compat.Compat.openScreen(new FbactorsMenuScreen(self)))
            .bounds(anchor[0], anchor[1], anchor[2], anchor[2]).build();
        browser.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("FBACTORS - browse Flashback and ReplayMod replays")));
        this.addRenderableWidget(browser);
        this.fbactors$button = browser;
    }

    private static void logKeysOnce(Screen self) {
        if (fbactors$keysLogged) {
            return;
        }
        fbactors$keysLogged = true;
        StringBuilder keys = new StringBuilder();
        for (GuiEventListener child : self.children()) {
            if (child instanceof Button button
                && button.getMessage().getContents() instanceof TranslatableContents text) {
                if (keys.length() > 0) {
                    keys.append(' ');
                }
                keys.append(text.getKey());
            }
        }
        pdx.dev.fbactors.FlashbackActors.LOGGER.info("FBACTORS title buttons: {}", keys);
    }


    @org.jetbrains.annotations.Nullable
    private int[] anchor(Screen self) {
        for (GuiEventListener child : self.children()) {
            if (child instanceof Button button && isVanillaSingleplayer(button)) {
                return new int[]{button.getRight() + 4, button.getY(), button.getHeight()};
            }
        }
        int top = Integer.MAX_VALUE;
        int column = -1;
        int size = 20;
        for (GuiEventListener child : self.children()) {
            if (child instanceof Button button && isHubButton(button)) {
                top = Math.min(top, button.getY());
                column = button.getX();
                size = button.getHeight();
            }
        }
        if (column < 0) {
            return null;
        }
        int y = top - size - 4;
        if (y < 0) {
            y = top;
            for (GuiEventListener child : self.children()) {
                if (child instanceof Button button && isHubButton(button)) {
                    y = Math.max(y, button.getY() + button.getHeight());
                }
            }
            y += 4;
        }
        return new int[]{column, y, size};
    }

    private static boolean isVanillaSingleplayer(Button button) {
        return button.getMessage().getContents() instanceof TranslatableContents text
            && text.getKey().equals("menu.singleplayer");
    }

    private static boolean isHubButton(Button button) {
        if (!(button.getMessage().getContents() instanceof TranslatableContents text)) {
            return false;
        }
        return text.getKey().equals("flashback.open_replays")
            || text.getKey().equals("replaymod.gui.replayviewer");
    }
}

