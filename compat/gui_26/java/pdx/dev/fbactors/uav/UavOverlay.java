package pdx.dev.fbactors.uav;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class UavOverlay {
    private UavOverlay() {}
    public static void install() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fbactors", "uav"), (graphics, tickCounter) -> render(graphics));
    }
    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.font == null) return;
        UavHud.State state = UavHudState.snapshot();
        if (state == null) return;
        float scale = graphics.guiHeight() / 1080f;
        if (!(scale > 0)) return;
        float designW = graphics.guiWidth() / scale;
        UavHud.render(new McCanvas(graphics, client.font, scale), state, designW, 1080f, System.currentTimeMillis());
    }
    private record McCanvas(GuiGraphicsExtractor g, Font font, float s) implements UavHud.Canvas {
        public void rect(float x, float y, float w, float h, int argb) {
            int x0 = Math.round(x * s);
            int y0 = Math.round(y * s);
            int x1 = Math.max(x0 + 1, Math.round((x + w) * s));
            int y1 = Math.max(y0 + 1, Math.round((y + h) * s));
            g.fill(x0, y0, x1, y1, argb);
        }
        public void text(String str, float x, float y, float size, int argb) {
            float k = size * s / font.lineHeight;
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x * s, y * s);
            pose.scale(k, k);
            g.text(font, str, 0, 0, argb, true);
            pose.popMatrix();
        }
        public float textWidth(String str, float size) {
            return font.width(str) * size / font.lineHeight;
        }
    }
}
