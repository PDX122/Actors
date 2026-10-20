package pdx.dev.fbactors.subtitle;

import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.settings.FbSettings;
import pdx.dev.fbactors.take.ActorSubtitle;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.ActorVoiceLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;


public final class SubtitleTrack {


    public record Entry(String speaker, String text, int colorArgb, float size, byte position,
                        float alpha, int startAbs) {
    }

    private SubtitleTrack() {
    }


    public static List<Entry> collect(int replayTick) {
        FbSettings settings = FbSettings.settings();
        List<Entry> out = new ArrayList<>();
        if (!FbSettings.subtitlesEnabled() || replayTick < 0) {
            return out;
        }
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        for (int index = 0; index < actors.size(); index++) {
            Actor actor = actors.get(index);
            ActorTake take = actor.take;
            if (take == null || !actor.enabled()) {
                continue;
            }
            int offset = actor.offset();
            if (!take.coversTick(replayTick - offset)) {
                continue;
            }
            String fallbackSpeaker = speakerOf(actor);
            for (ActorSubtitle subtitle : take.subtitles) {
                int absolute = subtitle.tick() + take.startTick + offset;
                int end = absolute + subtitle.durationTicks();
                if (replayTick < absolute || replayTick >= end) {
                    continue;
                }


                int color = subtitle.colorArgb() != 0
                    ? subtitle.colorArgb() : take.speakerColorArgb;
                out.add(new Entry(
                    blankToNull(subtitle.speaker(), fallbackSpeaker),
                    subtitle.text(),
                    color,
                    subtitle.size(),
                    subtitle.position(),
                    alpha(replayTick, absolute, end, settings.subFadeTicks),
                    absolute * 64 + index));
            }
            if (take.voiceEnabled) {
                for (ActorVoiceLine line : take.voices) {
                    if (line.caption() == null || line.samples().length == 0) {
                        continue;
                    }
                    int absolute = line.tick() + take.startTick + offset;
                    int end = absolute + Math.max(1, line.durationTicks());
                    if (replayTick < absolute || replayTick >= end) {
                        continue;
                    }
                    out.add(new Entry(
                        fallbackSpeaker,
                        line.caption(),
                        take.speakerColorArgb,
                        0.0f,
                        ActorSubtitle.POS_DEFAULT,
                        alpha(replayTick, absolute, end, settings.subFadeTicks),
                        absolute * 64 + index));
                }
            }
        }
        out.sort(Comparator.comparingInt(Entry::startAbs));
        int max = Math.max(1, Math.min(4, settings.subMaxLines));
        if (out.size() > max) {
            return new ArrayList<>(out.subList(0, max));
        }
        return out;
    }

    private static float alpha(int replayTick, int absolute, int end, int fadeTicks) {
        if (fadeTicks <= 0) {
            return 1.0f;
        }
        float fade = Math.max(1, fadeTicks);
        float in = (replayTick - absolute + 1) / fade;
        float left = (end - replayTick) / fade;
        return Math.max(0.0f, Math.min(1.0f, Math.min(in, left)));
    }

    private static String speakerOf(Actor actor) {
        String name = actor.displayName();
        return name == null || name.isBlank() ? "Actor" : name;
    }

    private static String blankToNull(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }


    public static List<String> wrap(String text, ToIntFunction<String> measure, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        int width = Math.max(40, maxWidth);
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (measure.applyAsInt(candidate) <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                while (measure.applyAsInt(word) > width && word.length() > 1) {
                    int cut = hardCut(word, measure, width);
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                line.append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }


    private static int hardCut(String word, ToIntFunction<String> measure, int maxWidth) {
        int cut = word.length();
        while (cut > 1 && measure.applyAsInt(word.substring(0, cut)) > maxWidth) {
            cut--;
        }
        if (cut < word.length() && word.charAt(cut - 1) == 167) {
            cut--;
        }
        return Math.max(1, cut);
    }
}

