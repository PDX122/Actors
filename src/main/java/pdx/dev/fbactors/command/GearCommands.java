package pdx.dev.fbactors.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.gear.Gear;
import pdx.dev.fbactors.gear.GearLoadout;
import pdx.dev.fbactors.gear.GearOverride;
import pdx.dev.fbactors.gear.GearPresets;
import pdx.dev.fbactors.gear.GearSlot;
import pdx.dev.fbactors.gear.GearStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public final class GearCommands {


    private static final int MAX_STACK = 64;


    private static final String EMPTY_ID = "empty";


    private static final int SLOT_COLUMN_WIDTH = slotColumnWidth();

    private static int slotColumnWidth() {
        int widest = 0;
        for (GearSlot slot : GearSlot.values()) {
            widest = Math.max(widest, slot.name().length());
        }
        return widest;
    }

    private GearCommands() {
    }


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                               LiteralArgumentBuilder<FabricClientCommandSource> parent) {
        LiteralArgumentBuilder<FabricClientCommandSource> gear = Compat.literal("gear");
        gear.then(presets());
        gear.then(preset());
        gear.then(set());
        gear.then(copy());
        gear.then(clear());
        gear.then(show());
        parent.then(gear);
    }


    private static List<String> slotNames() {
        List<String> names = new ArrayList<>(GearSlot.values().length);
        for (GearSlot slot : GearSlot.values()) {
            names.add(slot.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }


    private static List<String> presetIds() {
        List<String> ids = new ArrayList<>();
        for (GearLoadout preset : GearPresets.all()) {
            ids.add(preset.id());
        }
        return ids;
    }

    private static GearSlot slotFrom(CommandContext<FabricClientCommandSource> ctx, String name) {
        return GearSlot.fromName(ActorArgs.string(ctx, name));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> presets() {
        return Compat.literal("presets")
            .executes(ctx -> {
                FabricClientCommandSource source = ctx.getSource();
                List<GearLoadout> all = GearPresets.all();
                ActorArgs.ok(source, all.size() + " gear preset(s). "
                    + "Apply one with /actor gear preset <actor> <id>:");
                for (GearLoadout preset : all) {
                    ActorArgs.ok(source, "  " + preset.id() + "  \"" + preset.name() + "\"  "
                        + preset.slots().size() + " slot(s)");
                }
                return all.size();
            });
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> preset() {
        return Compat.literal("preset")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("presetId", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest(GearPresets.NONE);
                        for (String id : presetIds()) {
                            builder.suggest(id);
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        FabricClientCommandSource source = ctx.getSource();
                        String presetId = ActorArgs.string(ctx, "presetId").trim();
                        boolean clearing = presetId.equalsIgnoreCase(GearPresets.NONE)
                            || presetId.equalsIgnoreCase("off");
                        GearLoadout preset = clearing ? null : GearPresets.byId(presetId);
                        if (!clearing && preset == null) {
                            ActorArgs.fail(source, "No gear preset \"" + presetId + "\". "
                                + "Run /actor gear presets for the list, or use \"none\" to clear.");
                            return 0;
                        }
                        GearStore store = Gear.store();
                        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                        return ActorArgs.mutate(source, query, (replay, actor) -> {
                            boolean hadTweaks = !store.overridesFor(actor.take.id).isEmpty();
                            String previous = store.presetFor(actor.take.id);
                            store.setPreset(actor.take.id, clearing ? null : preset.id());
                            Gear.save();
                            String from = previous == null ? "nothing" : previous;
                            ActorArgs.ok(source, actor.displayName() + " gear preset " + from
                                + " -> " + (clearing ? "none" : preset.id()) + ".");
                            if (hadTweaks) {
                                ActorArgs.ok(source, "  Their per-slot tweaks were dropped;"
                                    + " a preset change replaces the whole loadout.");
                            }
                        });
                    })));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> set() {
        return Compat.literal("set")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("slot", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        ActorArgs.suggestAll(builder, slotNames());
                        builder.suggest(EMPTY_ID);
                        return builder.buildFuture();
                    })
                    .then(Compat.argument("itemId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest(EMPTY_ID);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> applyItem(ctx, false))
                        .then(Compat.argument("count", IntegerArgumentType.integer())
                            .executes(ctx -> applyItem(ctx, true))))));
    }

    private static int applyItem(CommandContext<FabricClientCommandSource> ctx, boolean hasCount) {
        FabricClientCommandSource source = ctx.getSource();
        GearSlot slot = slotFrom(ctx, "slot");
        if (slot == null) {
            ActorArgs.fail(source, "Unknown gear slot. Valid slots: "
                + String.join(", ", slotNames()) + ".");
            return 0;
        }

        String itemId = ActorArgs.string(ctx, "itemId").trim();
        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
        boolean emptying = itemId.equalsIgnoreCase(EMPTY_ID);

        if (emptying && hasCount) {
            ActorArgs.fail(source, "\"empty\" takes no count - there is nothing to count.");
            return 0;
        }

        String resolvedId = itemId;
        int count = 1;
        if (!emptying) {
            int wanted = hasCount ? ActorArgs.integer(ctx, "count") : 1;
            count = Mth.clamp(wanted, 1, MAX_STACK);
            ItemStack probe = Compat.itemStack(itemId, count);
            if (probe.isEmpty()) {
                ActorArgs.fail(source, "No item with id \"" + itemId + "\". Use a namespaced id"
                    + " such as minecraft:diamond_helmet, or \"empty\" to put nothing in the slot.");
                return 0;
            }
            ActorArgs.clamped(source, "count", wanted, count);


            resolvedId = Compat.itemId(probe);
        }

        final String finalId = resolvedId;
        final int finalCount = count;
        return ActorArgs.mutate(source, query, (replay, actor) -> {
            Gear.store().set(actor.take.id, emptying
                ? GearOverride.empty(slot)
                : GearOverride.of(slot, finalId, finalCount));
            Gear.save();
            ActorArgs.ok(source, actor.displayName() + ": "
                + slot.name().toLowerCase(Locale.ROOT) + " = "
                + (emptying ? "nothing (any recorded item removed)"
                    : finalCount + "x " + finalId) + ".");
        });
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> copy() {
        return Compat.literal("copy")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .then(Compat.argument("slot", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        ActorArgs.suggestAll(builder, slotNames());
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        FabricClientCommandSource source = ctx.getSource();
                        GearSlot slot = slotFrom(ctx, "slot");
                        if (slot == null) {
                            ActorArgs.fail(source, "Unknown gear slot. Valid slots: "
                                + String.join(", ", slotNames()) + ".");
                            return 0;
                        }
                        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                        return ActorArgs.mutate(source, query, (replay, actor) -> {
                            var clone = actor.clone();
                            if (clone == null) {
                                ActorArgs.fail(source, actor.displayName()
                                    + " has no live clone right now; scrub to a tick they cover and try again.");
                                return;
                            }
                            ItemStack stack = clone.getItemBySlot(slot.equipmentSlot());
                            GearOverride override;
                            String detail;
                            if (stack == null || stack.isEmpty()) {
                                override = GearOverride.empty(slot);
                                detail = "nothing (any recorded item removed)";
                            } else {
                                String id = Compat.itemId(stack);
                                String nbt = Compat.itemStackNbt(clone.level(), stack);
                                override = nbt == null || nbt.isEmpty()
                                    ? GearOverride.of(slot, id, stack.getCount())
                                    : GearOverride.ofSnbt(slot, id, stack.getCount(), nbt);
                                detail = stack.getCount() + "x " + id
                                    + (override.nbt() != null ? " with full components" : "");
                            }
                            Gear.store().set(actor.take.id, override);
                            Gear.save();
                            ActorArgs.ok(source, actor.displayName() + ": "
                                + slot.name().toLowerCase(Locale.ROOT) + " = " + detail + ".");
                        });
                    })));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> clear() {
        return Compat.literal("clear")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .executes(ctx -> {
                    FabricClientCommandSource source = ctx.getSource();
                    String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                    return ActorArgs.mutate(source, query, (replay, actor) -> {
                        GearStore store = Gear.store();
                        if (!store.isOverridden(actor.take.id)) {
                            ActorArgs.ok(source, actor.displayName()
                                + " has no gear overrides; nothing to clear.");
                            return;
                        }
                        String preset = store.presetFor(actor.take.id);
                        int tweaks = store.overridesFor(actor.take.id).size();
                        store.clear(actor.take.id);
                        Gear.save();
                        ActorArgs.ok(source, "Cleared all gear for " + actor.displayName()
                            + " (preset " + (preset == null ? "none" : preset) + ", "
                            + tweaks + " slot tweak(s)). They now wear what the take recorded.");
                    });
                })
                .then(Compat.argument("slot", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        ActorArgs.suggestAll(builder, slotNames());
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        FabricClientCommandSource source = ctx.getSource();
                        GearSlot slot = slotFrom(ctx, "slot");
                        if (slot == null) {
                            ActorArgs.fail(source, "Unknown gear slot. Valid slots: "
                                + String.join(", ", slotNames()) + ".");
                            return 0;
                        }
                        String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                        return ActorArgs.mutate(source, query, (replay, actor) -> {
                            GearStore store = Gear.store();
                            if (!store.overridesFor(actor.take.id).containsKey(slot)) {
                                ActorArgs.ok(source, actor.displayName() + " has no override on "
                                    + slot.name().toLowerCase(Locale.ROOT) + "; nothing to clear.");
                                return;
                            }
                            store.clearSlot(actor.take.id, slot);
                            Gear.save();
                            ActorArgs.ok(source, "Dropped the " + slot.name().toLowerCase(Locale.ROOT)
                                + " override for " + actor.displayName() + ". Any preset entry"
                                + " for that slot now shows through again.");
                        });
                    })));
    }


    private static LiteralArgumentBuilder<FabricClientCommandSource> show() {
        return Compat.literal("show")
            .then(ActorArgs.actorArgument(ActorArgs.ACTOR)
                .executes(ctx -> {
                    FabricClientCommandSource source = ctx.getSource();
                    String query = ActorArgs.string(ctx, ActorArgs.ACTOR);
                    return ActorArgs.report(source, query, (replay, actor) -> {
                        GearStore store = Gear.store();
                        GearLoadout loadout = store.loadoutFor(actor.take.id);
                        String preset = store.presetFor(actor.take.id);

                        if (loadout.isEmpty()) {
                            return actor.displayName() + " has no gear overrides and wears"
                                + " exactly what the take recorded. Use /actor gear preset "
                                + ActorArgs.ACTOR + " <presetId> to dress them.";
                        }

                        StringBuilder out = new StringBuilder();
                        out.append(actor.displayName()).append(" gear: ")
                            .append(loadout.name())
                            .append(" (preset ")
                            .append(preset == null ? "none" : preset)
                            .append(", source ")
                            .append(loadout.id().startsWith("actor:") ? "own tweaks" : "preset")
                            .append(")");
                        for (GearSlot slot : GearSlot.sorted()) {
                            GearOverride override = loadout.overrideFor(slot);
                            out.append('\n').append("    ")
                                .append(pad(slot.name().toLowerCase(Locale.ROOT))).append(' ');
                            if (override == null) {
                                out.append("(as recorded)");
                            } else if (override.removesItem()) {
                                out.append("(empty)");
                            } else {
                                out.append(override.count()).append("x ").append(override.itemId());
                                if (!override.enchantments().isEmpty()) {
                                    out.append(' ').append(override.enchantments());
                                }
                            }
                        }
                        return out.toString();
                    });
                }));
    }


    private static String pad(String slotName) {
        StringBuilder out = new StringBuilder(slotName);
        while (out.length() < SLOT_COLUMN_WIDTH) {
            out.append(' ');
        }
        return out.toString();
    }
}
