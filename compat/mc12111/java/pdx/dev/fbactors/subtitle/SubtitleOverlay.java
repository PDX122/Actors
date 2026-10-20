package pdx.dev.fbactors.subtitle;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.settings.FbSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Subtitle renderer for the 1.21 generation, drawn through Fabric's classic
 * HUD callback with {@code GuiGraphics}.
 *
 * <p>Plain HUD quads, so captions show in the editor preview and in exports
 * with GUI enabled, and stay out of no-GUI exports on their own. See
 * {@link SubtitleTrack} for what is drawn and {@link SubtitleMenu} for where
 * the words come from.
 */
public final class SubtitleOverlay {

    private SubtitleOverlay() {
    }

    public static void install() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    private static void render(GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.font == null || client.getWindow() == null) {
            return;
        }
        int tick = FlashbackActors.replayTick();
        if (tick < 0) {
            return;
        }
        List<SubtitleTrack.Entry> entries = SubtitleTrack.collect(tick);
        if (entries.isEmpty()) {
            return;
        }
        draw(context, client.font, client.getWindow().getGuiScaledWidth(),
            client.getWindow().getGuiScaledHeight(), entries);
    }

    private static void draw(GuiGraphics context, Font font, int screenW, int screenH,
                             List<SubtitleTrack.Entry> entries) {
        FbSettings settings = FbSettings.settings();
        List<Block> bottoms = new ArrayList<>();
        List<Block> tops = new ArrayList<>();
        for (SubtitleTrack.Entry entry : entries) {
            if (entry.alpha() <= 0.01f || entry.text().isEmpty()) {
                continue;
            }
            Block block = layout(font, settings, entry);
            if (block == null) {
                continue;
            }
            if (block.top()) {
                tops.add(block);
            } else {
                bottoms.add(block);
            }
        }
        int cursor = screenH - 64;
        for (Block block : bottoms) {
            cursor -= block.height;
            paint(context, font, block, (screenW - block.width) / 2, cursor);
            cursor -= 6;
        }
        cursor = 24;
        for (Block block : tops) {
            paint(context, font, block, (screenW - block.width) / 2, cursor);
            cursor += block.height + 6;
        }
    }

    /** One caption with its wrapped lines and measured box, unscaled pixels. */
    private record Block(SubtitleTrack.Entry entry, List<String> textLines, boolean speaker,
                         int width, int height, float scale) {
        boolean top() {
            if (entry.position() == pdx.dev.fbactors.take.ActorSubtitle.POS_TOP) {
                return true;
            }
            if (entry.position() == pdx.dev.fbactors.take.ActorSubtitle.POS_BOTTOM) {
                return false;
            }
            return FbSettings.settings().subPosition == 1;
        }
    }

    private static Block layout(Font font, FbSettings settings, SubtitleTrack.Entry entry) {
        float scale = entry.size() > 0.0f ? entry.size()
            : Math.max(0.5f, Math.min(2.0f, settings.subScale));
        List<String> lines = SubtitleTrack.wrap(entry.text(), font::width,
            Math.max(200, settings.subMaxWidth));
        if (lines.isEmpty()) {
            return null;
        }
        boolean speaker = settings.subSpeaker
            && entry.speaker() != null && !entry.speaker().isBlank();
        int pad = Math.max(0, settings.subPadding);
        int width = 0;
        if (speaker) {
            width = Math.max(width, font.width(entry.speaker()));
        }
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int height = pad * 2 + (lines.size() + (speaker ? 1 : 0)) * (font.lineHeight + 1);
        return new Block(entry, lines, speaker, width + pad * 2, height, scale);
    }

    private static void paint(GuiGraphics context, Font font, Block block, int left, int top) {
        FbSettings settings = FbSettings.settings();
        SubtitleTrack.Entry entry = block.entry();
        // 1.21.8+ GuiGraphics.pose() is a JOML Matrix3x2fStack (pushMatrix /
        // popMatrix, 2-arg translate/scale). The PoseStack shape lives in the
        // 1.21.1-1.21.5 per-version overrides.
        context.pose().pushMatrix();
        context.pose().translate(left, top);
        context.pose().scale(block.scale(), block.scale());
        try {
            int pad = Math.max(0, settings.subPadding);
            if (settings.subBackground) {
                int alpha = Math.round(255.0f
                    * Math.max(0.0f, Math.min(0.9f, settings.subBgOpacity)) * entry.alpha());
                context.fill(0, 0, block.width(), block.height(),
                    (alpha << 24) | rgb(settings.subBgColor));
            }
            int y = pad;
            if (block.speaker()) {
                int speaker = withAlpha(rgb(settings.subSpeakerColor), entry.alpha());
                drawLine(context, font, settings, entry, entry.speaker(), speaker, block.width(), y,
                    pad);
                y += font.lineHeight + 1;
            }
            int color = entry.colorArgb() != 0 ? withAlpha(entry.colorArgb(), entry.alpha())
                : withAlpha(rgb(settings.subTextColor), entry.alpha());
            for (String line : block.textLines()) {
                drawLine(context, font, settings, entry, line, color, block.width(), y, pad);
                y += font.lineHeight + 1;
            }
        } finally {
            context.pose().popMatrix();
        }
    }

    private static void drawLine(GuiGraphics context, Font font, FbSettings settings,
                                 SubtitleTrack.Entry entry, String line, int color, int blockWidth,
                                 int y, int pad) {
        int x = (blockWidth - font.width(line)) / 2;
        if (x < pad) {
            x = pad;
        }
        if (settings.subOutline) {
            int outline = withAlpha(rgb(settings.subOutlineColor), entry.alpha());
            context.drawString(font, line, x - 1, y, outline, false);
            context.drawString(font, line, x + 1, y, outline, false);
            context.drawString(font, line, x, y - 1, outline, false);
            context.drawString(font, line, x, y + 1, outline, false);
        }
        context.drawString(font, line, x, y, color, settings.subShadow && !settings.subOutline);
    }

    private static int rgb(float[] color) {
        if (color == null || color.length < 3) {
            return 0xFFFFFF;
        }
        int r = Math.round(Math.max(0.0f, Math.min(1.0f, color[0])) * 255.0f);
        int g = Math.round(Math.max(0.0f, Math.min(1.0f, color[1])) * 255.0f);
        int b = Math.round(Math.max(0.0f, Math.min(1.0f, color[2])) * 255.0f);
        return (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(255.0f * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
