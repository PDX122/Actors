package pdx.dev.fbactors.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TimelineScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int BOX_H = 20;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55DD55;

    private static final String[] DESCRIPTION = {
        "A temporary copy of this world will be simulated.",
        "Your original world will not advance.",
        "No player will appear in the finished timeline.",
        "The temporary world runs at an accelerated rate.",
    };

    private EditBox name;
    private EditBox minutes;
    private EditBox seconds;
    private int left;
    private int top;

    public TimelineScreen() {
        super(Component.literal("Timeline"));
    }

    public static TimelineScreen create() {
        TimelineForm.reset();
        return new TimelineScreen();
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_W) / 2;
        top = Math.max(20, this.height / 2 - 110);

        int half = (PANEL_W - 5) / 2;

        this.name = new EditBox(this.font, left, top + 74, PANEL_W, BOX_H,
            Component.literal("Recording name"));
        this.name.setMaxLength(TimelineForm.MAX_NAME);
        this.name.setValue(TimelineForm.name());
        addRenderableWidget(this.name);

        this.minutes = new EditBox(this.font, left, top + 116, half, BOX_H,
            Component.literal("Minutes"));
        this.minutes.setMaxLength(4);
        this.minutes.setValue(TimelineForm.minutes());
        addRenderableWidget(this.minutes);

        this.seconds = new EditBox(this.font, left + half + 5, top + 116, half, BOX_H,
            Component.literal("Seconds"));
        this.seconds.setMaxLength(2);
        this.seconds.setValue(TimelineForm.seconds());
        addRenderableWidget(this.seconds);

        addRenderableWidget(Button.builder(Component.literal("Create"), b -> onCreate())
            .bounds(left, top + 166, PANEL_W / 2 - 3, BOX_H).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(left + PANEL_W / 2 + 3, top + 166, PANEL_W / 2 - 3, BOX_H).build());

        setInitialFocus(this.name);
    }

    private void onCreate() {
        sync();

        TimelineForm.create();
    }

    private void sync() {
        TimelineForm.name(this.name.getValue());
        TimelineForm.duration(parse(this.minutes.getValue()), parse(this.seconds.getValue()));
    }

    private static int parse(String raw) {
        String digits = raw.trim();
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return 0;
            }
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void centered(GuiGraphicsExtractor g, String text, int y, int colour) {
        g.text(this.font, Component.literal(text), this.width / 2 - this.font.width(text) / 2,
            y, colour);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        sync();
        if (TimelineForm.busy()) {
            centered(g, "Capturing the world...", top + 146, GREY);
            return;
        }
        if (TimelineForm.completed()) {
            onClose();
            return;
        }

        int y = top;
        centered(g, this.title.getString(), y, WHITE);
        y += 14;

        for (String line : DESCRIPTION) {
            centered(g, line, y, GREY);
            y += 11;
        }

        y = top + 62;
        g.text(this.font, Component.literal("Recording Name"), left, y, GREY);
        y = top + 104;
        int half = (PANEL_W - 5) / 2;
        g.text(this.font, Component.literal("Minutes"), left, y, GREY);
        g.text(this.font, Component.literal("Seconds"), left + half + 5, y, GREY);

        centered(g, TimelineForm.readout(), top + 146, WHITE);

        String notice = TimelineForm.notice();
        if (notice != null) {
            centered(g, notice, top + 192, TimelineForm.noticeOk() ? GREEN : RED);
        }
        String error = TimelineForm.error();
        if (error != null) {
            centered(g, error, top + (notice != null ? 204 : 192), RED);
        }
    }
}
