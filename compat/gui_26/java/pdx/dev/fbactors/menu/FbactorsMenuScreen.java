package pdx.dev.fbactors.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The FBACTORS browser on the 26 render generation.
 *
 * <p>Same buttons and behaviour as the 1.21 variant; only the text calls use
 * the extractor API this generation moved to.
 */
public class FbactorsMenuScreen extends FbactorsMenuScreenBase {

    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0xA0A0A0;
    private static final int RED = 0xFF5555;

    public FbactorsMenuScreen(Screen parent) {
        super(parent);
    }

    private void centered(GuiGraphicsExtractor g, String text, int y, int colour) {
        g.text(this.font, Component.literal(text),
            this.width / 2 - this.font.width(text) / 2, y, colour);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        centered(g, this.title.getString(), 20, WHITE);
        centered(g, "Flashback (" + flashbackCount() + ")  -  ReplayMod ("
            + replayModCount() + ")", 36, GREY);
        if (pageCount() > 1) {
            centered(g, "Page " + (this.page + 1) + " / " + pageCount(), LIST_TOP - 16, GREY);
        }
        if (!this.notice.isEmpty()) {
            centered(g, this.notice, this.height - 84, RED);
        }
    }
}
