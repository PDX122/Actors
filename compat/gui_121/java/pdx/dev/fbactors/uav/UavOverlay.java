package pdx.dev.fbactors.uav;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class UavOverlay {
    private UavOverlay() {}
    public static void install() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }
    private static void render(GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.font == null || client.getWindow() == null) return;
        UavHud.State state = UavHudState.snapshot();
        if (state == null) return;
        float scale = context.guiHeight() / 1080f;
        if (!(scale > 0)) return;
        float designW = context.guiWidth() / scale;
        UavHud.render(new McCanvas(context, client.font, scale), state, designW, 1080f, System.currentTimeMillis());
    }
    private record McCanvas(GuiGraphics g, Font font, float s) implements UavHud.Canvas {
        public void rect(float x, float y, float w, float h, int argb) {
            int x0 = Math.round(x * s);
            int y0 = Math.round(y * s);
            int x1 = Math.max(x0 + 1, Math.round((x + w) * s));
            int y1 = Math.max(y0 + 1, Math.round((y + h) * s));
            g.fill(x0, y0, x1, y1, argb);
        }
        public void text(String str, float x, float y, float size, int argb) {
            g.drawString(font, str, Math.round(x * s), Math.round(y * s), argb, true);
        }
        public float textWidth(String str, float size) {
            return font.width(str) * size / font.lineHeight;
        }
    }
}
