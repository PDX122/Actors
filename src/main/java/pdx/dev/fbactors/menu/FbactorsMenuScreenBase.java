package pdx.dev.fbactors.menu;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.convert.McprToFlashback;
import pdx.dev.fbactors.util.GameVersions;

import java.nio.file.Path;
import java.util.List;


public abstract class FbactorsMenuScreenBase extends Screen {

    protected static final int PER_PAGE = 8;
    protected static final int ROW_H = 20;
    protected static final int ROW_GAP = 4;
    protected static final int LIST_TOP = 64;

    protected final Screen parent;
    protected List<ReplayBrowser.Entry> entries = List.of();
    protected int page;
    protected String notice = "";

    protected FbactorsMenuScreenBase(Screen parent) {
        super(Component.literal("FBACTORS"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.entries = ReplayBrowser.scan(ReplayBrowser.flashbackFolder(), replayModFolder());
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
        int y = LIST_TOP;
        int from = this.page * PER_PAGE;
        for (int i = from; i < Math.min(this.entries.size(), from + PER_PAGE); i++) {
            ReplayBrowser.Entry entry = this.entries.get(i);
            addRenderableWidget(Button.builder(Component.literal(rowLabel(entry)),
                    b -> open(entry)).bounds(x, y, w, ROW_H).build());
            y += ROW_H + ROW_GAP;
        }

        int navY = this.height - 60;
        int half = (w - 8) / 2;
        Button prev = Button.builder(Component.literal("< Prev"), b -> turn(-1))
            .bounds(x, navY, half, ROW_H).build();
        prev.active = this.page > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal("Next >"), b -> turn(1))
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
        return Math.max(1, (this.entries.size() + PER_PAGE - 1) / PER_PAGE);
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
        String marker = entry.source() == ReplayBrowser.Source.FLASHBACK ? "> " : "@ ";
        String suffix = entry.source() == ReplayBrowser.Source.REPLAY_MOD
            ? "  (converts on open)" : "";
        return marker + entry.name() + "  -  " + entry.seconds() + "s" + suffix;
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

