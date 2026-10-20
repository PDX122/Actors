package pdx.dev.fbactors.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;


public final class ScreenUi {


    public static final int PANEL_W = 320;


    public static final int ROW_H = 20;


    public static final int LINE_H = 12;


    public static final int GAP_S = 4;


    public static final int GAP = 8;


    public static final int GAP_L = 16;


    public static final int GAP_XL = 24;


    public static final int TITLE = 0xFFFFFFFF;


    public static final int TEXT = 0xFFE0E0E0;


    public static final int MUTED = 0xFFA0A0A0;


    public static final int FAINT = 0xFF6E6E6E;


    public static final int ACCENT = 0xFF9ECBFF;


    public static final int OK = 0xFF6FCF6F;


    public static final int WARN = 0xFFE0B24B;


    public static final int DANGER = 0xFFE06A6A;

    private ScreenUi() {
    }


    public static int leftEdge(int screenWidth) {
        return (screenWidth - PANEL_W) / 2;
    }


    public static int topEdge(int screenHeight, int contentHeight) {
        return Math.max(GAP_XL, (screenHeight - contentHeight) / 2);
    }


    public static EditBox label(int x, int y, int w, Component text, int colour) {
        EditBox box = new EditBox(Minecraft.getInstance().font, x, y, w, ROW_H, text);
        box.setEditable(false);
        box.setBordered(false);
        box.setCanLoseFocus(true);
        box.setValue(text.getString());
        box.setTextColor(colour);
        return box;
    }


    public static EditBox label(int x, int y, int w, String text, int colour) {
        return label(x, y, w, Component.literal(text), colour);
    }


    public static Button button(int x, int y, int w, Component text, Button.OnPress onPress) {
        return Button.builder(text, onPress).bounds(x, y, w, ROW_H).build();
    }


    public static EditBox header(int x, int y, int w, Component text) {
        return label(x, y, w, text, TITLE);
    }


    public static int textWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }


    public static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }


    public static final class Column {

        private final int x;
        private final int width;
        private int y;

        public Column(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
        }

        public int x() {
            return this.x;
        }

        public int width() {
            return this.width;
        }

        public int y() {
            return this.y;
        }


        public void gap(int pixels) {
            this.y += pixels;
        }


        public EditBox label(Component text, int colour) {
            EditBox box = ScreenUi.label(this.x, this.y, this.width, text, colour);
            this.y += LINE_H;
            return box;
        }


        public EditBox label(String text, int colour) {
            return label(Component.literal(text), colour);
        }


        public void row() {
            this.y += ROW_H;
        }


        public void row(int after) {
            this.y += ROW_H + after;
        }
    }
}

