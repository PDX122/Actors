package pdx.dev.fbactors.voice;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Capture meter overlay for the 1.21 generation, drawn through Fabric's
 * classic HUD callback with {@code GuiGraphics}.
 *
 * <p>Fixed to the bottom left, never interactive and never movable: HUD quads
 * are not windows, so there is nothing to drag. See {@link VoiceMeter} for
 * what the bar, the hold tick and the status line mean.
 */
public final class VoiceMeterOverlay {

    private VoiceMeterOverlay() {
    }

    public static void install() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    private static void render(GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.font == null || client.getWindow() == null) {
            return;
        }
        VoiceMeter.State state = VoiceMeter.sample();
        if (state == null) {
            return;
        }
        draw(context, client.font, client.getWindow().getGuiScaledWidth(),
            client.getWindow().getGuiScaledHeight(), state);
    }

    private static void draw(GuiGraphics context, Font font, int screenWidth, int screenHeight,
                             VoiceMeter.State state) {
        int x = VoiceMeter.PAD;
        int barY = screenHeight - VoiceMeter.PAD - VoiceMeter.BAR_HEIGHT;
        int barX = x;
        VoiceMeter.RectSink sink =
            (x1, y1, x2, y2, color) -> context.fill(x1, y1, x2, y2, color);

        VoiceMeter.roundedFill(sink, barX, barY, VoiceMeter.BAR_WIDTH, VoiceMeter.BAR_HEIGHT,
            VoiceMeter.BAR_RADIUS, 0x66000000);
        float level = Math.min(1.0f, state.hold());
        int fillW = Math.round(VoiceMeter.BAR_WIDTH * level);
        if (fillW > 0) {
            VoiceMeter.roundedFill(sink, barX, barY, fillW, VoiceMeter.BAR_HEIGHT,
                VoiceMeter.BAR_RADIUS, VoiceMeter.colourFor(level));
        }

        int statusX = barX + VoiceMeter.BAR_WIDTH + 6;
        if (state.clipping()) {
            context.drawString(font, "CLIP", statusX, barY - 3, 0xFFFF5555, true);
        } else if (state.quiet()) {
            context.drawString(font, "quiet?", statusX, barY - 3, 0xFFAAAAAA, true);
        }
    }
}
