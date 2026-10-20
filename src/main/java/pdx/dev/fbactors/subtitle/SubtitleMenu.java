package pdx.dev.fbactors.subtitle;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.take.ActorSubtitle;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.ActorVoiceLine;
import pdx.dev.fbactors.voice.Transcriber;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class SubtitleMenu {

    private static final float PANEL_WIDTH = 480.0f;
    private static final float PANEL_HEIGHT = 560.0f;

    private static boolean open;


    private static final ImBoolean OPEN = new ImBoolean();


    private static final Map<String, ImString> CAPTIONS = new HashMap<>();


    private static String captionEdit = null;


    private static String editTake;
    private static int editIndex = -2;

    private static final ImString EDIT_TEXT = new ImString(512);
    private static final ImString EDIT_SPEAKER = new ImString(64);
    private static final int[] EDIT_START = {0};
    private static final int[] EDIT_DURATION = {60};
    private static final boolean[] EDIT_CUSTOM_COLOR = {false};
    private static final float[] EDIT_COLOR = {1.0f, 1.0f, 1.0f};
    private static final float[] EDIT_SIZE = {0.0f};
    private static final int[] EDIT_POSITION = {0};

    private static String notice = "";

    private static ActorTake pendingSave;


    private static final ImString ENGINE_PATH = new ImString(512);

    private SubtitleMenu() {
    }

    public static boolean isOpen() {
        return open;
    }


    public static void open() {
        if (!pdx.dev.fbactors.settings.FbSettings.subtitlesEnabled()) {
            return;
        }
        open = true;
        OPEN.set(true);
        String custom = pdx.dev.fbactors.settings.FbSettings.settings().whisperBinary;
        ENGINE_PATH.set(custom == null ? "" : custom);
    }

    public static void close() {
        open = false;
        OPEN.set(false);
    }


    public static void renderMenuItem() {
        if (!pdx.dev.fbactors.settings.FbSettings.subtitlesEnabled()) {
            return;
        }
        if (ImGui.menuItem("Subtitles##SubtitleOpen", open)) {
            if (open) {
                close();
            } else {
                open();
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Captions on voice lines and hand-authored subtitle lines");
        }
    }


    public static void render() {
        if (!open) {
            return;
        }
        if (!pdx.dev.fbactors.settings.FbSettings.subtitlesEnabled()) {
            close();
            return;
        }
        ImGui.setNextWindowSize(PANEL_WIDTH, PANEL_HEIGHT);
        OPEN.set(open);
        if (!ImGui.begin("Subtitles", OPEN)) {
            ImGui.end();
            return;
        }
        open = OPEN.get();

        List<Actor> actors = FlashbackActors.ACTORS.actors();
        if (actors.isEmpty()) {
            ImGui.textDisabled("No actors yet. Record a take first.");
        }
        applyTranscriptions(actors);
        for (Actor actor : actors) {
            renderActor(actor);
            ImGui.separator();
        }
        if (!notice.isEmpty()) {
            ImGui.textDisabled(notice);
        }
        ImGui.textDisabled("Edits save to the take at once. Captions show in preview"
            + " and in exports with GUI on.");

        ImGui.end();
        open = OPEN.get();
        flush();
    }

    private static void renderActor(Actor actor) {
        ActorTake take = actor.take;
        if (take == null) {
            return;
        }
        ImGui.text(actor.displayName());
        renderTranscribe(actor, take);
        renderVoices(actor, take);
        renderManual(actor, take);
    }


    private static void applyTranscriptions(List<Actor> actors) {
        List<Transcriber.Caption> captions = Transcriber.drain();
        if (captions.isEmpty()) {
            return;
        }
        for (Transcriber.Caption caption : captions) {
            for (Actor actor : actors) {
                ActorTake take = actor.take;
                if (take == null || !take.id.equals(caption.takeId())) {
                    continue;
                }
                for (int i = 0; i < take.voices.size(); i++) {
                    ActorVoiceLine line = take.voices.get(i);
                    if (line.tick() == caption.lineTick()) {
                        String text = caption.text() == null || caption.text().isBlank()
                            ? null : caption.text().strip();
                        String current = line.caption();
                        boolean same = text == null ? current == null : text.equals(current);
                        if (!same) {
                            take.voices.set(i,
                                new ActorVoiceLine(line.tick(), line.samples(), text));
                            pendingSave = take;
                        }
                    }
                }
            }
        }
    }

    private static void renderTranscribe(Actor actor, ActorTake take) {
        Transcriber.refresh();
        int withAudio = 0;
        for (ActorVoiceLine line : take.voices) {
            if (line.samples().length > 0) {
                withAudio++;
            }
        }
        Transcriber.State state = Transcriber.state();
        boolean busy = state == Transcriber.State.DOWNLOADING || state == Transcriber.State.WORKING;
        if (state == Transcriber.State.MISSING) {
            if (ImGui.button("Install transcriber (95 MB)##SubInstall")) {
                Transcriber.install();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setItemTooltip("Downloads the whisper engine and speech model once."
                    + " Everything after that works offline.");
            }
        } else if (withAudio > 0 && !busy) {
            if (ImGui.button("Transcribe " + withAudio + " lines##SubTranscribe" + take.id)) {
                List<Transcriber.LineSnapshot> snapshots = new ArrayList<>();
                for (ActorVoiceLine line : take.voices) {
                    if (line.samples().length > 0) {
                        snapshots.add(new Transcriber.LineSnapshot(line.tick(), line.samples()));
                    }
                }
                Transcriber.transcribe(take.id, snapshots);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setItemTooltip("One pass over the whole take, mapped back"
                    + " onto lines by timestamp. Replaces existing captions.");
            }
        }
        String status = Transcriber.status();
        if (busy) {
            ImGui.textDisabled(status + " " + Math.round(Transcriber.progress() * 100.0) + "%");
        } else if (!status.isEmpty()) {
            ImGui.textDisabled(status);
        }
        ImGui.textDisabled("Engine path (blank = automatic)");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        ImGui.inputText("##SubEngine", ENGINE_PATH);
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Custom whisper binary path."
                + " Needed on platforms with no prebuilt engine.");
        }
        if (ImGui.button("Use custom path", PANEL_WIDTH - 40.0f, 0.0f)) {
            String custom = ENGINE_PATH.get() == null ? "" : ENGINE_PATH.get().strip();
            pdx.dev.fbactors.settings.FbSettings.settings().whisperBinary = custom;
            pdx.dev.fbactors.settings.FbSettings.settings().save();
            Transcriber.refresh();
        }
    }

    private static void renderVoices(Actor actor, ActorTake take) {
        ImGui.textDisabled("Voice captions");
        if (take.voices.isEmpty()) {
            ImGui.textDisabled("No voice lines recorded.");
            return;
        }
        for (int i = 0; i < take.voices.size(); i++) {
            ActorVoiceLine line = take.voices.get(i);
            String key = "v:" + take.id + ":" + i;
            ImGui.pushID(key);
            String head = "t+" + line.tick() + " (" + line.durationTicks() + "t)";
            if (line.caption() != null) {
                ImGui.text(head + ": " + preview(line.caption()));
            } else {
                ImGui.textDisabled(head + ": no caption");
            }
            ImGui.sameLine();
            boolean editing = key.equals(captionEdit);
            if (ImGui.button(editing ? "Close" : "Edit")) {
                if (editing) {
                    captionEdit = null;
                } else {
                    captionEdit = key;
                    CAPTIONS.computeIfAbsent(key, k -> new ImString(512))
                        .set(line.caption() == null ? "" : line.caption());
                }
            }
            if (editing) {
                ImString buffer = CAPTIONS.get(key);
                ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
                ImGui.inputTextMultiline("##SubCaption", buffer, PANEL_WIDTH - 40.0f, 48.0f);
                if (ImGui.button("Save")) {
                    String text = buffer == null ? "" : buffer.get().strip();
                    take.voices.set(i, new ActorVoiceLine(line.tick(), line.samples(),
                        text.isEmpty() ? null : text));
                    pendingSave = take;
                    captionEdit = null;
                }
                ImGui.sameLine();
                if (ImGui.button("Clear")) {
                    take.voices.set(i, new ActorVoiceLine(line.tick(), line.samples(), null));
                    pendingSave = take;
                    captionEdit = null;
                }
            }
            ImGui.popID();
        }
    }

    private static String preview(String text) {
        String flat = text.replace('\n', ' ');
        return flat.length() <= 42 ? flat : flat.substring(0, 42) + "...";
    }

    private static void renderManual(Actor actor, ActorTake take) {
        ImGui.textDisabled("Manual subtitles");
        List<ActorSubtitle> sorted = new ArrayList<>(take.subtitles);
        sorted.sort(Comparator.comparingInt(ActorSubtitle::tick));
        for (int i = 0; i < sorted.size(); i++) {
            ActorSubtitle subtitle = sorted.get(i);
            String key = "m:" + take.id + ":" + i;
            ImGui.pushID(key);
            String speaker = subtitle.speaker() == null || subtitle.speaker().isBlank()
                ? actor.displayName() : subtitle.speaker();
            ImGui.text("[" + subtitle.tick() + " +" + subtitle.durationTicks() + "] "
                + preview(speaker + ": " + subtitle.text()));
            ImGui.sameLine();
            if (ImGui.button("Edit")) {
                openForm(take, i, subtitle);
            }
            ImGui.sameLine();
            if (ImGui.button("Delete")) {
                take.subtitles.remove(subtitle);
                pendingSave = take;
                if (key.equals(formKey())) {
                    closeForm();
                }
            }
            ImGui.popID();
        }
        if (ImGui.button("Add subtitle at playhead##SubAdd" + take.id)) {
            int start = playheadTakeTick(actor, take);
            openForm(take, -1, new ActorSubtitle(start, 60, actor.displayName(), "",
                0, 0.0f, ActorSubtitle.POS_DEFAULT));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Starts where the playhead sits, lasts 3 seconds");
        }
        renderForm(actor, take);
    }

    private static String formKey() {
        if (editTake == null || editIndex < -1) {
            return "";
        }
        return "m:" + editTake + ":" + editIndex;
    }

    private static void openForm(ActorTake take, int index, ActorSubtitle subtitle) {
        editTake = take.id.toString();
        editIndex = index;
        EDIT_TEXT.set(subtitle.text());
        EDIT_SPEAKER.set(subtitle.speaker() == null ? "" : subtitle.speaker());
        EDIT_START[0] = Math.max(0, subtitle.tick());
        EDIT_DURATION[0] = Math.max(1, subtitle.durationTicks());
        EDIT_CUSTOM_COLOR[0] = subtitle.colorArgb() != 0;
        if (EDIT_CUSTOM_COLOR[0]) {
            EDIT_COLOR[0] = ((subtitle.colorArgb() >> 16) & 0xFF) / 255.0f;
            EDIT_COLOR[1] = ((subtitle.colorArgb() >> 8) & 0xFF) / 255.0f;
            EDIT_COLOR[2] = (subtitle.colorArgb() & 0xFF) / 255.0f;
        } else {
            EDIT_COLOR[0] = 1.0f;
            EDIT_COLOR[1] = 1.0f;
            EDIT_COLOR[2] = 1.0f;
        }
        EDIT_SIZE[0] = Math.max(0.0f, Math.min(2.0f, subtitle.size()));
        EDIT_POSITION[0] = subtitle.position() >= 0 && subtitle.position() <= 2
            ? subtitle.position() : 0;
        notice = "";
    }

    private static void closeForm() {
        editTake = null;
        editIndex = -2;
    }

    private static void renderForm(Actor actor, ActorTake take) {
        if (!take.id.toString().equals(editTake)) {
            return;
        }
        int takeLen = Math.max(0, take.length() - 1);
        ImGui.text(editIndex < 0 ? "New subtitle" : "Edit subtitle");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        ImGui.inputTextMultiline("Words##SubText", EDIT_TEXT, PANEL_WIDTH - 40.0f, 64.0f);
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Vanilla codes like \u00a7l for bold work here");
        }
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        ImGui.inputText("Speaker##SubSpeaker", EDIT_SPEAKER);
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Blank follows the actor name");
        }
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        EDIT_START[0] = Math.max(0, Math.min(takeLen, EDIT_START[0]));
        ImGui.sliderInt("Start tick##SubStart", EDIT_START, 0, Math.max(0, takeLen), "%d");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        ImGui.sliderInt("Duration##SubDur", EDIT_DURATION, 1, ActorSubtitle.MAX_DURATION_TICKS,
            "%d ticks");
        if (ImGui.checkbox("Own colour##SubColorOn", EDIT_CUSTOM_COLOR[0])) {
            EDIT_CUSTOM_COLOR[0] = !EDIT_CUSTOM_COLOR[0];
        }
        if (EDIT_CUSTOM_COLOR[0]) {
            ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
            ImGui.colorEdit3("Colour##SubColor", EDIT_COLOR);
        }
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        ImGui.sliderFloat("Size##SubSize", EDIT_SIZE, 0.0f, 2.0f, "%.2f");
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("0 follows the global caption scale");
        }
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.beginCombo("Spot##SubPos", positionLabel(EDIT_POSITION[0]))) {
            for (int i = 0; i <= 2; i++) {
                if (ImGui.menuItem(positionLabel(i), EDIT_POSITION[0] == i)) {
                    EDIT_POSITION[0] = i;
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.button("Save subtitle")) {
            saveForm(actor, take);
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            closeForm();
            notice = "";
        }
    }

    private static String positionLabel(int position) {
        return switch (position) {
            case 1 -> "Bottom";
            case 2 -> "Top";
            default -> "Follow global";
        };
    }

    private static void saveForm(Actor actor, ActorTake take) {
        String text = EDIT_TEXT.get() == null ? "" : EDIT_TEXT.get().strip();
        if (text.isEmpty()) {
            notice = "Write some words first.";
            return;
        }
        String speaker = EDIT_SPEAKER.get() == null ? "" : EDIT_SPEAKER.get().strip();
        int color = 0;
        if (EDIT_CUSTOM_COLOR[0]) {
            int r = Math.round(EDIT_COLOR[0] * 255.0f);
            int g = Math.round(EDIT_COLOR[1] * 255.0f);
            int b = Math.round(EDIT_COLOR[2] * 255.0f);
            color = (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }
        ActorSubtitle subtitle = new ActorSubtitle(
            Math.max(0, EDIT_START[0]), Math.max(1, EDIT_DURATION[0]),
            speaker.isEmpty() ? null : speaker, text, color,
            Math.max(0.0f, Math.min(2.0f, EDIT_SIZE[0])),
            (byte) Math.max(0, Math.min(2, EDIT_POSITION[0])));
        if (editIndex < 0) {
            take.subtitles.add(subtitle);
        } else {
            List<ActorSubtitle> sorted = new ArrayList<>(take.subtitles);
            sorted.sort(Comparator.comparingInt(ActorSubtitle::tick));
            if (editIndex < sorted.size()) {
                take.subtitles.remove(sorted.get(editIndex));
            }
            take.subtitles.add(subtitle);
        }
        take.subtitles.sort(Comparator.comparingInt(ActorSubtitle::tick));
        pendingSave = take;
        closeForm();
        notice = "";
    }

    private static int playheadTakeTick(Actor actor, ActorTake take) {
        int takeLen = Math.max(0, take.length() - 1);
        int absolute = FlashbackActors.replayTick();
        if (absolute < 0) {
            return 0;
        }
        return Math.max(0, Math.min(takeLen, absolute - take.startTick - actor.offset()));
    }

    private static void flush() {
        ActorTake save = pendingSave;
        pendingSave = null;
        if (save == null) {
            return;
        }
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        replay.execute(() -> FlashbackActors.save(save));
    }
}

