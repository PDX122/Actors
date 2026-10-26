package pdx.dev.fbactors.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.emote.Emotes;
import pdx.dev.fbactors.morph.Morph;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.TakeBundle;
import pdx.dev.fbactors.undo.UndoStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public final class TakeCommands {


    public static final int MAX_OFFSET_TICKS = 30 * 60 * 20;


    private static final int MIN_TRIMMED_LENGTH = 1;

    private TakeCommands() {
    }


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                               LiteralArgumentBuilder<FabricClientCommandSource> parent) {
        parent.then(list());
        parent.then(rename());
        parent.then(enable());
        parent.then(delete());
        parent.then(duplicate());
        parent.then(offset());
        parent.then(trim());
        parent.then(trimReset());
        parent.then(bundle());
        parent.then(speakerColor());
        parent.then(MotionCommands.command());
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> bundle() {
        return Compat.literal("bundle")
            .then(Compat.literal("export").executes(ctx -> {
                FabricClientCommandSource source = ctx.getSource();
                com.moulberry.flashback.playback.ReplayServer replay =
                    ActorArgs.requireReplay(source);
                if (replay == null) {
                    return 0;
                }
                UUID replayId = FlashbackActors.loadedReplayIdOrNull();
                if (replayId == null) {
                    ActorArgs.fail(source, "No replay is loaded.");
                    return 0;
                }
                List<Actor> actors = FlashbackActors.ACTORS.actors();
                if (actors.isEmpty()) {
                    ActorArgs.fail(source, "No actors in this replay yet.");
                    return 0;
                }
                String replayName = replay.getMetadata().name;
                if (replayName == null || replayName.isBlank()) {
                    replayName = "replay";
                }
                java.nio.file.Path folder =
                    com.moulberry.flashback.Flashback.getReplayFolder();
                if (folder == null) {
                    ActorArgs.fail(source, "Flashback has no replay folder to write into.");
                    return 0;
                }
                java.nio.file.Path out = uniqueBundle(folder, replayName);
                List<ActorTake> takes = new ArrayList<>();
                for (Actor actor : actors) {
                    if (actor.take != null) {
                        takes.add(actor.take);
                    }
                }
                try {
                    com.google.gson.JsonObject gearActors = new com.google.gson.JsonObject();
                    com.google.gson.JsonObject emoteTracks = new com.google.gson.JsonObject();
                    com.google.gson.JsonObject morphActors = new com.google.gson.JsonObject();
                    pdx.dev.fbactors.gear.GearStore gear =
                        pdx.dev.fbactors.gear.Gear.store();
                    for (Actor actor : actors) {
                        if (actor.take == null) {
                            continue;
                        }
                        com.google.gson.JsonObject slice = gear.exportActor(actor.take.id);
                        if (slice != null) {
                            gearActors.add(actor.take.id.toString(), slice);
                        }
                        com.google.gson.JsonArray clips =
                            pdx.dev.fbactors.emote.Emotes.exportClips(actor.take.id);
                        if (clips != null) {
                            emoteTracks.add(actor.take.id.toString(), clips);
                        }
                        com.google.gson.JsonObject morph =
                            pdx.dev.fbactors.morph.Morph.store().exportSelection(actor.take.id);
                        if (morph != null) {
                            morphActors.add(actor.take.id.toString(), morph);
                        }
                    }
                    TakeBundle.Exported exported = TakeBundle.exportBundle(
                        out, replayId, replayName, takes, gearActors, emoteTracks, morphActors);
                    ActorArgs.ok(source, "Bundled " + exported.takes() + " take(s) into "
                        + out.getFileName() + ". Send it with the replay file.");
                    return exported.takes();
                } catch (java.io.IOException e) {
                    FlashbackActors.LOGGER.error("Failed to export take bundle", e);
                    ActorArgs.fail(source, "Bundle export failed; see the log.");
                    return 0;
                }
            }))
            .then(Compat.literal("import")
                .executes(ctx -> importBundle(ctx.getSource(), null))
                .then(Compat.argument("file", StringArgumentType.greedyString())
                    .executes(ctx -> importBundle(ctx.getSource(),
                        ActorArgs.string(ctx, "file").trim()))));
    }

    private static int importBundle(FabricClientCommandSource source, String rawFile) {
        com.moulberry.flashback.playback.ReplayServer replay =
            ActorArgs.requireReplay(source);
        if (replay == null) {
            return 0;
        }
        UUID replayId = FlashbackActors.loadedReplayIdOrNull();
        if (replayId == null) {
            ActorArgs.fail(source, "No replay is loaded.");
            return 0;
        }
        java.nio.file.Path bundle;
        if (rawFile != null && !rawFile.isEmpty()) {
            bundle = java.nio.file.Path.of(rawFile);
            if (!java.nio.file.Files.isRegularFile(bundle)) {
                ActorArgs.fail(source, "No bundle at \"" + rawFile + "\".");
                return 0;
            }
        } else {
            java.nio.file.Path origin = TakeBundle.findReplaySource(replayId);
            bundle = origin == null ? null : TakeBundle.findBundleFor(origin);
            if (bundle == null) {
                ActorArgs.fail(source, "No bundle beside this replay. Pass the file: "
                    + "/actor bundle import <file>.");
                return 0;
            }
        }
        final java.nio.file.Path found = bundle;
        final UUID target = replayId;
        replay.execute(() -> {
            try {
                TakeBundle.Bundle parsed = TakeBundle.readBundle(found);
                TakeBundle.Summary summary = TakeBundle.importInto(parsed, target);
                TakeBundle.Summary done = summary;
                ActorArgs.ok(source, "Imported " + done.takes() + " take(s)"
                    + (done.gearActors() > 0 ? ", gear for " + done.gearActors() : "")
                    + (done.emoteClips() > 0 ? ", " + done.emoteClips() + " emote clip(s)" : "")
                    + (done.morphActors() > 0 ? ", morphs for " + done.morphActors() : "")
                    + ".");
            } catch (java.io.IOException e) {
                FlashbackActors.LOGGER.error("Failed to import take bundle {}", found, e);
                ActorArgs.fail(source, "Bundle import failed; see the log.");
            }
        });
        return 1;
    }

    private static java.nio.file.Path uniqueBundle(java.nio.file.Path folder, String name) {
        String stamp = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        java.nio.file.Path candidate = folder.resolve(stamp + TakeBundle.EXTENSION);
        int n = 2;
        while (java.nio.file.Files.exists(candidate)) {
            candidate = folder.resolve(stamp + " " + n + TakeBundle.EXTENSION);
            n++;
        }
        return candidate;
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> speakerColor() {
        return Compat.literal("speakercolor")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("color", StringArgumentType.word())
                    .executes(ctx -> {
                        FabricClientCommandSource source = ctx.getSource();
                        String raw = ActorArgs.string(ctx, "color").trim();
                        int argb;
                        if (raw.equalsIgnoreCase("default")) {
                            argb = 0;
                        } else {
                            Integer parsed = parseColor(raw);
                            if (parsed == null) {
                                ActorArgs.fail(source, "Give a colour like #FFAA33 or #80FFAA33,"
                                    + " or \"default\" for the global subtitle colour.");
                                return 0;
                            }
                            argb = parsed;
                        }
                        final int color = argb;
                        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                        return ActorArgs.mutate(source, query, (replay, actor) -> {
                            int before = actor.take.speakerColorArgb;
                            actor.take.speakerColorArgb = color;
                            UndoStack.pushSpeakerColor(actor.take, before, color);
                            ActorArgs.save(actor.take);
                            ActorArgs.ok(source, actor.displayName() + ": dialogue colour = "
                                + (color == 0 ? "default" : String.format("#%08X", color)) + ".");
                        });
                    })));
    }

    @Nullable
    private static Integer parseColor(String raw) {
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() != 6 && hex.length() != 8) {
            return null;
        }
        try {
            long value = Long.parseLong(hex, 16);
            if (hex.length() == 6) {
                value |= 0xFF000000L;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> list() {
        return Compat.literal("list")
            .executes(ctx -> {
                FabricClientCommandSource source = ctx.getSource();
                if (ActorArgs.requireReplay(source) == null) {
                    return 0;
                }
                List<Actor> actors = FlashbackActors.ACTORS.actors();
                if (actors.isEmpty()) {
                    ActorArgs.ok(source, "No actors in this replay yet.");
                    return 1;
                }
                int enabled = 0;
                for (Actor actor : actors) {
                    if (actor.enabled()) {
                        enabled++;
                    }
                }
                ActorArgs.ok(source, actors.size() + " actor(s), " + enabled + " enabled:");
                for (Actor actor : actors) {
                    ActorArgs.ok(source, "  " + ActorArgs.describe(actor));
                }
                return actors.size();
            });
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> rename() {
        return Compat.literal("rename")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("name", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> builder.buildFuture())
                    .executes(ctx -> {
                        FabricClientCommandSource source = ctx.getSource();
                        String name = ActorArgs.string(ctx, "name").trim();
                        if (name.isEmpty()) {
                            ActorArgs.fail(source, "A new name cannot be blank. "
                                + "Use /actor name <actor> <name> to set only the floating label.");
                            return 0;
                        }
                        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                        return ActorArgs.mutate(source, query, (replay, actor) -> {
                            String previous = actor.displayName();
                            String before = actor.take.name;
                            actor.take.name = name;
                            UndoStack.pushRename(actor.take, before, name);
                            ActorArgs.save(actor.take);
                            ActorArgs.ok(source, "Renamed \"" + previous + "\" to \"" + name + "\".");
                        });
                    })));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> enable() {
        return Compat.literal("enable")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("state", BoolArgumentType.bool())
                    .executes(ctx -> {
                        FabricClientCommandSource source = ctx.getSource();
                        boolean state = ActorArgs.bool(ctx, "state");
                        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                        return ActorArgs.mutate(source, query, (replay, actor) -> {
                            if (actor.enabled() == state) {
                                ActorArgs.ok(source, actor.displayName() + " is already "
                                    + (state ? "enabled" : "disabled") + ".");
                                return;
                            }
                            boolean before = actor.enabled();
                            actor.setEnabled(state);
                            UndoStack.pushEnabled(actor.take, before, state);
                            ActorArgs.save(actor.take);
                            ActorArgs.ok(source, actor.displayName() + " "
                                + (state ? "enabled" : "disabled") + ".");
                        });
                    })));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> delete() {
        return Compat.literal("delete")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .executes(ctx -> remove(ctx.getSource(),
                    ActorArgs.string(ctx, ActorArgs.ACTOR)))
                .then(Compat.literal("confirm")
                    .executes(ctx -> remove(ctx.getSource(),
                        ActorArgs.string(ctx, ActorArgs.ACTOR)))));
    }


    private static void pushAdditionUndo(
        com.moulberry.flashback.playback.ReplayServer replay, ActorTake added) {
        UndoStack.pushEdit(
            () -> replay.execute(() -> {
                Actor found = FlashbackActors.ACTORS.byTakeId(added.id);
                if (found != null) {
                    FlashbackActors.removeActor(found);
                }
            }),
            () -> replay.execute(() -> {
                if (FlashbackActors.ACTORS.byTakeId(added.id) == null) {
                    FlashbackActors.ACTORS.add(added);
                    ActorArgs.save(added);
                }
            }));
    }


    private static int remove(FabricClientCommandSource source, String query) {
        if (ActorMode.phase() == ActorMode.Phase.RECORDING) {
            ActorArgs.fail(source, "Cannot delete an actor while a take is recording. "
                + "Run /actor stop first.");
            return 0;
        }
        return ActorArgs.mutate(source, query, (replay, actor) -> {
            String name = actor.displayName();
            UndoStack.pushRemoval(actor.take);


            Emotes.forgetTake(actor.take.id);
            Morph.store().clear(actor.take.id);
            FlashbackActors.removeActor(actor);
            ActorArgs.ok(source, "Deleted \"" + name + "\" (take " + actor.take.id
                + "). Its .take file has been unlinked; re-record to restore it.");
        });
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> duplicate() {
        return Compat.literal("duplicate")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .executes(ctx -> {
                    FabricClientCommandSource source = ctx.getSource();
                    String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                    return ActorArgs.mutate(source, query, (replay, actor) -> {
                        Actor copy = FlashbackActors.duplicateActor(actor);
                        pushAdditionUndo(replay, copy.take);
                        ActorArgs.ok(source, "Duplicated \"" + actor.displayName()
                            + "\" as \"" + copy.displayName() + "\" (take " + copy.take.id
                            + "). Emotes, gear and morph are per take id and were not copied.");
                    });
                })
                .then(Compat.argument("newName", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        FabricClientCommandSource source = ctx.getSource();
                        String name = ActorArgs.string(ctx, "newName").trim();
                        if (name.isEmpty()) {
                            ActorArgs.fail(source, "The new name cannot be blank. "
                                + "Run /actor duplicate <actor> with no name to get \""
                                + ActorArgs.resolve(ActorArgs.string(ctx, ActorArgs.ACTOR))
                                    .detail() + "\" copied instead.");
                            return 0;
                        }
                        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                        return ActorArgs.mutate(source, query, (replay, actor) -> {
                            ActorTake copy = actor.take.copyAs(ActorArgs.newTakeId(), name);
                            Actor created = FlashbackActors.ACTORS.add(copy);
                            ActorArgs.save(copy);
                            pushAdditionUndo(replay, copy);
                            ActorArgs.ok(source, "Duplicated \"" + actor.displayName()
                                + "\" as \"" + created.displayName() + "\" (take " + copy.id
                                + "). Emotes, gear and morph are per take id and were not copied.");
                        });
                    })));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> offset() {
        return Compat.literal("offset")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("ticks", IntegerArgumentType.integer())
                    .executes(ctx -> {
                        FabricClientCommandSource source = ctx.getSource();
                        int wanted = ActorArgs.integer(ctx, "ticks");
                        int applied = Mth.clamp(wanted, -MAX_OFFSET_TICKS, MAX_OFFSET_TICKS);
                        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);


                        return ActorArgs.mutate(source, query, (replay, actor) -> {
                            ActorArgs.clamped(source, "offset", wanted, applied);
                            int previous = actor.offset();
                            actor.setOffset(applied);
                            UndoStack.pushOffset(actor.take, previous, applied);
                            ActorArgs.save(actor.take);
                            ActorArgs.ok(source, actor.displayName() + " offset "
                                + previous + " -> " + applied + " ticks ("
                                + seconds(applied) + ").");
                        });
                    })));
    }


    private static String seconds(int ticks) {
        if (ticks % 20 == 0) {
            return (ticks / 20) + "s";
        }
        return String.format(java.util.Locale.ROOT, "%.2fs", ticks / 20.0f);
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> trim() {
        return Compat.literal("trim")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("in", IntegerArgumentType.integer())
                    .then(Compat.argument("out", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            FabricClientCommandSource source = ctx.getSource();
                            int trimIn = ActorArgs.integer(ctx, "in");
                            int trimOut = ActorArgs.integer(ctx, "out");
                            if (trimIn < 0 || trimOut < 0) {
                                ActorArgs.fail(source, "Trim counts count inwards from each end and"
                                    + " cannot be negative: got in=" + trimIn + ", out=" + trimOut
                                    + ". Use /actor trimreset to undo a trim.");
                                return 0;
                            }
                            String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                            return ActorArgs.mutate(source, query, (replay, actor) -> applyTrim(
                                source, actor, trimIn, trimOut));
                        }))));
    }


    private static void applyTrim(FabricClientCommandSource source, Actor actor,
                                  int trimIn, int trimOut) {
        ActorTake take = actor.take;
        int length = take.length();
        if (length <= 0) {
            ActorArgs.fail(source, "\"" + actor.displayName()
                + "\" has no frames, so there is nothing to trim.");
            return;
        }
        long removed = (long) trimIn + trimOut;
        if (removed > length - MIN_TRIMMED_LENGTH) {
            ActorArgs.fail(source, "Trimming " + trimIn + " in and " + trimOut
                + " out would leave " + (length - removed) + " of " + length + " ticks. At least "
                + MIN_TRIMMED_LENGTH + " tick must remain, so the two counts may total "
                + (length - MIN_TRIMMED_LENGTH) + " at most. Nothing was changed.");
            return;
        }

        int beforeIn = take.trimIn;
        int beforeOut = take.trimOut;
        take.trimIn = trimIn;
        take.trimOut = trimOut;
        UndoStack.pushTrim(take, beforeIn, beforeOut, trimIn, trimOut);
        ActorArgs.save(take);
        ActorArgs.ok(source, actor.displayName() + " trimmed -" + trimIn + " in / -" + trimOut
            + " out, now replay ticks " + take.firstTick() + ".." + take.lastTick()
            + " of " + length + ".");


        int lastInputTakeTick = take.lastInputTick() - take.offsetTicks;
        if (take.simulated() && lastInputTakeTick > take.length() - trimOut) {
            ActorArgs.ok(source, "  Note: this take is input-driven and its inputs run to take"
                + " tick " + lastInputTakeTick + ", past the trimmed end. The body stops there"
                + " but recorded inputs may still apply.");
        }
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> trimReset() {
        return Compat.literal("trimreset")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .executes(ctx -> {
                    FabricClientCommandSource source = ctx.getSource();
                    String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                    return ActorArgs.mutate(source, query, (replay, actor) -> {
                        ActorTake take = actor.take;
                        if (take.trimIn == 0 && take.trimOut == 0) {
                            ActorArgs.ok(source, actor.displayName() + " was not trimmed.");
                            return;
                        }
                        int in = take.trimIn;
                        int out = take.trimOut;
                        take.resetTrim();
                        UndoStack.pushTrim(take, in, out, 0, 0);
                        ActorArgs.save(take);
                        ActorArgs.ok(source, actor.displayName() + " trim cleared (was -" + in
                            + " in / -" + out + " out), now replay ticks "
                            + take.firstTick() + ".." + take.lastTick() + ".");
                    });
                }));
    }
}
