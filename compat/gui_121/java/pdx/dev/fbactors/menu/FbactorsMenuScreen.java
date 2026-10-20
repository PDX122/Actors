package pdx.dev.fbactors.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The FBACTORS browser on the 1.21 render generation.
 *
 * <p>Everything but the text drawing lives in {@link FbactorsMenuScreenBase};
 * this class only draws the chrome around the shared buttons.
 */
public class FbactorsMenuScreen extends FbactorsMenuScreenBase {

    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0xA0A0A0;
    private static final int RED = 0xFF5555;

    public FbactorsMenuScreen(Screen parent) {
        super(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 20, WHITE);
        g.drawCenteredString(this.font,
            Component.literal("Flashback (" + flashbackCount() + ")  -  ReplayMod ("
                + replayModCount() + ")"),
            this.width / 2, 36, GREY);
        if (pageCount() > 1) {
            g.drawCenteredString(this.font,
                Component.literal("Page " + (this.page + 1) + " / " + pageCount()),
                this.width / 2, LIST_TOP - 16, GREY);
        }
        if (!this.notice.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal(this.notice),
                this.width / 2, this.height - 84, RED);
        }
    }
}
