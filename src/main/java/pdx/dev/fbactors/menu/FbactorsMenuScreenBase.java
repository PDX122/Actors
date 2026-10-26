package pdx.dev.fbactors.menu;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.convert.McprToFlashback;
import pdx.dev.fbactors.util.GameVersions;

import java.nio.file.Path;
import java.util.List;


public abstract class FbactorsMenuScreenBase extends Screen {

    protected static final int PER_PAGE = 8;
    protected static final int ROW_H = 24;
    protected static final int ROW_GAP = 4;
    protected static final int LIST_TOP = 96;

    protected final Screen parent;
    protected List<ReplayBrowser.Entry> entries = List.of();
    protected int page;
    protected String notice = "";
    private boolean loaded;
    private String query = "";
    private EditBox search;
    private List<ReplayBrowser.Entry> filtered = List.of();

    protected FbactorsMenuScreenBase(Screen parent) {
        super(Component.literal("Flashback Actors"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!this.loaded) {
            this.entries = ReplayBrowser.scan(ReplayBrowser.flashbackFolder(), replayModFolder());
            this.loaded = true;
        }
        this.filtered = ReplayMenuModel.filter(this.entries, this.query);
        int cursor = this.search == null ? this.query.length() : this.search.getCursorPosition();
        int pages = pageCount();
        if (this.page > pages - 1) {
            this.page = pages - 1;
        }
        if (this.page < 0) {
            this.page = 0;
        }
        clearWidgets();

        int w = Math.min(440, this.width - 40);
        int x = this.width / 2 - w / 2;
        this.search = new EditBox(this.font, x, 52, w - 80, 20, Component.literal("Search replays"));
        this.search.setHint(Component.literal("Search replays..."));
        this.search.setValue(this.query);
        this.search.setCursorPosition(cursor);
        this.search.setResponder(value -> {
            this.query = value;
            this.page = 0;
            init();
            setInitialFocus(this.search);
        });
        addRenderableWidget(this.search);
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
            this.loaded = false;
            this.notice = "";
            init();
        }).bounds(x + w - 72, 52, 72, 20).build());
        int y = LIST_TOP;
        int rows = ReplayMenuModel.rows(this.height);
        int from = this.page * rows;
        for (int i = from; i < Math.min(this.filtered.size(), from + rows); i++) {
            ReplayBrowser.Entry entry = this.filtered.get(i);
            String label = rowLabel(entry);
            if (this.font.width(label) > w - 20) label = this.font.plainSubstrByWidth(label, w - 32) + "...";
            Button row = Button.builder(Component.literal(label), b -> open(entry)).bounds(x, y, w, ROW_H).build();
            row.active = entry.source() == ReplayBrowser.Source.FLASHBACK;
            addRenderableWidget(row);
            y += ROW_H + ROW_GAP;
        }
        if (this.filtered.isEmpty()) {
            addRenderableWidget(pdx.dev.fbactors.ui.ScreenUi.label(x, y, w,
                this.query.isBlank() ? "No replays yet. Record one with Flashback to begin." : "No matching replays. Try another search.",
                pdx.dev.fbactors.ui.ScreenUi.MUTED));
        }

        int navY = this.height - 64;
        int half = (w - 8) / 2;
        Button prev = Button.builder(Component.literal("Previous page"), b -> turn(-1))
            .bounds(x, navY, half, ROW_H).build();
        prev.active = this.page > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal("Next page"), b -> turn(1))
            .bounds(x + half + 8, navY, half, ROW_H).build();
        next.active = this.page < pageCount() - 1;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(x, navY + ROW_H + ROW_GAP, w, ROW_H).build());
    }

    private void turn(int direction) {
        this.page += direction;
        init();
    }

    protected int pageCount() {
        return ReplayMenuModel.pages(this.filtered.size(), ReplayMenuModel.rows(this.height));
    }

    protected long flashbackCount() {
        return this.entries.stream()
            .filter(e -> e.source() == ReplayBrowser.Source.FLASHBACK).count();
    }

    protected long replayModCount() {
        return this.entries.stream()
            .filter(e -> e.source() == ReplayBrowser.Source.REPLAY_MOD).count();
    }

    protected static String rowLabel(ReplayBrowser.Entry entry) {
        String suffix = entry.source() == ReplayBrowser.Source.REPLAY_MOD
            ? "  (conversion unavailable)" : "";
        return entry.name() + "  |  " + ReplayMenuModel.duration(entry.seconds()) + suffix;
    }

    private void open(ReplayBrowser.Entry entry) {
        if (entry.source() == ReplayBrowser.Source.FLASHBACK) {
            try {
                Flashback.openReplayWorld(entry.path());
            } catch (Exception e) {
                FlashbackActors.LOGGER.error("Could not open replay {}", entry.path(), e);
                this.notice = "Could not open it, see the log";
            }
            return;
        }


        this.notice = "Converting is disabled right now";
    }


    private void convertAndOpen(ReplayBrowser.Entry entry) {
        Path folder = ReplayBrowser.flashbackFolder();
        if (folder == null) {
            this.notice = "Flashback has no replay folder to write into";
            return;
        }
        try {
            String gameVersion = GameVersions.gameVersionName();
            int dataVersion = GameVersions.gameDataVersion();
            int protocolVersion = GameVersions.gameProtocolVersion();
            McprToFlashback.Result result = McprToFlashback.convert(
                entry.path(), null, gameVersion, dataVersion, protocolVersion, folder);
            if (!result.ok() || result.path() == null) {
                this.notice = result.message();
                return;
            }
            Flashback.openReplayWorld(result.path());
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Could not convert {}", entry.path(), e);
            this.notice = "Convert failed, see the log";
        }
    }

    @Nullable
    private Path replayModFolder() {
        return ReplayBrowser.replayModFolder(Minecraft.getInstance().gameDirectory.toPath());
    }

    @Override
    public void onClose() {
        pdx.dev.fbactors.compat.Compat.openScreen(this.parent);
    }
}

