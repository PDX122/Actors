package pdx.dev.fbactors.gear;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class GearPresets {


public static final String NONE = "none";


public static final String LEATHER_SET = "leather_set";
public static final String IRON_SET = "iron_set";
public static final String GOLD_SET = "gold_set";
public static final String DIAMOND_SET = "diamond_set";
public static final String NETHERITE_SET = "netherite_set";
public static final String CHAINMAIL_SET = "chainmail_set";
public static final String TURTLE_SET = "turtle_set";
public static final String FIGHTER = "fighter";
public static final String GUARDSMAN = "guardsman";
public static final String ARCHER = "archer";
public static final String PYROMANCER = "pyromancer";
public static final String BLAZER = "blazer";
public static final String ENCHANTER = "enchanter";
public static final String MEDIC = "medic";
public static final String DIVER = "diver";
public static final String ELF = "elf";
public static final String FFA = "ffa";


private static final Map<String, String> FALLBACKS = buildFallbacks();


private static final List<GearLoadout> ALL = buildAll();


private static final Map<String, GearLoadout> BY_ID = index(ALL);

private GearPresets() {
}


@Nullable
public static String fallbackFor(@Nullable String itemId) {
    return itemId == null ? null : FALLBACKS.get(itemId);
}


@Nullable
public static GearLoadout byId(@Nullable String id) {
    return id == null ? null : BY_ID.get(id.trim());
}


public static List<GearLoadout> all() {
    return ALL;
}


    public static List<String> names() {
        return List.copyOf(BY_ID.keySet());
    }


    private static Map<String, String> buildFallbacks() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String colour : new String[]{"white", "light_blue", "red"}) {
            for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
                map.put("minecraft:" + colour + "_dyed_leather_" + piece,
                        "minecraft:leather_" + piece);
            }
        }
        return Map.copyOf(map);
    }


    private static Map<String, GearLoadout> index(List<GearLoadout> presets) {
        Map<String, GearLoadout> map = new LinkedHashMap<>();
        for (GearLoadout preset : presets) {
            map.put(preset.id(), preset);
        }
        return Map.copyOf(map);
    }


    private static Map<GearSlot, GearOverride> armour(String prefix) {
        Map<GearSlot, GearOverride> map = new EnumMap<>(GearSlot.class);
        map.put(GearSlot.HEAD, GearOverride.of(
            GearSlot.HEAD, "minecraft:" + prefix + "_helmet", 1));
        map.put(GearSlot.CHEST, GearOverride.of(
            GearSlot.CHEST, "minecraft:" + prefix + "_chestplate", 1));
        map.put(GearSlot.LEGS, GearOverride.of(
            GearSlot.LEGS, "minecraft:" + prefix + "_leggings", 1));
        map.put(GearSlot.FEET, GearOverride.of(
            GearSlot.FEET, "minecraft:" + prefix + "_boots", 1));
        return map;
    }


    private static Map<GearSlot, GearOverride> dyedArmour(String colour) {
        Map<GearSlot, GearOverride> map = new EnumMap<>(GearSlot.class);
        map.put(GearSlot.HEAD, GearOverride.of(
            GearSlot.HEAD, "minecraft:" + colour + "_dyed_leather_helmet", 1));
        map.put(GearSlot.CHEST, GearOverride.of(
            GearSlot.CHEST, "minecraft:" + colour + "_dyed_leather_chestplate", 1));
        map.put(GearSlot.LEGS, GearOverride.of(
            GearSlot.LEGS, "minecraft:" + colour + "_dyed_leather_leggings", 1));
        map.put(GearSlot.FEET, GearOverride.of(
            GearSlot.FEET, "minecraft:" + colour + "_dyed_leather_boots", 1));
        return map;
    }


    private static Map<String, Integer> ench(int level, String... ids) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String id : ids) {
            map.put("minecraft:" + id, level);
        }
        return map;
    }


    @SafeVarargs
    private static Map<String, Integer> ench(Map<String, Integer>... maps) {
        Map<String, Integer> combined = new LinkedHashMap<>();
        for (Map<String, Integer> each : maps) {
            combined.putAll(each);
        }
        return combined;
    }

    private static List<GearLoadout> buildAll() {
        List<GearLoadout> presets = new ArrayList<>(18);


        presets.add(new GearLoadout(NONE, "None", Map.of()));


        presets.add(new GearLoadout(LEATHER_SET, "Leather Set", armour("leather")));
        presets.add(new GearLoadout(IRON_SET, "Iron Set", armour("iron")));
        presets.add(new GearLoadout(GOLD_SET, "Gold Set", armour("golden")));
        presets.add(new GearLoadout(DIAMOND_SET, "Diamond Set", armour("diamond")));
        presets.add(new GearLoadout(NETHERITE_SET, "Netherite Set", armour("netherite")));
        presets.add(new GearLoadout(CHAINMAIL_SET, "Chainmail Set", armour("chainmail")));


        {
            Map<GearSlot, GearOverride> turtle = armour("leather");
            turtle.put(GearSlot.HEAD, GearOverride.of(
                GearSlot.HEAD, "minecraft:turtle_helmet", 1));
            presets.add(new GearLoadout(TURTLE_SET, "Turtle Set", turtle));
        }


        {
            Map<GearSlot, GearOverride> fighter = armour("leather");
            fighter.put(GearSlot.MAINHAND, GearOverride.of(
                GearSlot.MAINHAND, "minecraft:wooden_sword", 1));
            presets.add(new GearLoadout(FIGHTER, "Fighter", fighter));
        }


        {
            Map<GearSlot, GearOverride> guard = armour("iron");
            guard.put(GearSlot.MAINHAND, GearOverride.of(
                GearSlot.MAINHAND, "minecraft:iron_sword", 1));
            guard.put(GearSlot.OFFHAND, GearOverride.of(
                GearSlot.OFFHAND, "minecraft:shield", 1));
            presets.add(new GearLoadout(GUARDSMAN, "Guardsman", guard));
        }


        {
            Map<GearSlot, GearOverride> archer = armour("leather");
            archer.put(GearSlot.CHEST, GearOverride.of(
                GearSlot.CHEST, "minecraft:chainmail_chestplate", 1));
            archer.put(GearSlot.MAINHAND, GearOverride.of(
                GearSlot.MAINHAND, "minecraft:bow", 1));
            archer.put(GearSlot.OFFHAND, GearOverride.of(
                GearSlot.OFFHAND, "minecraft:arrow", 64));
            presets.add(new GearLoadout(ARCHER, "Archer", archer));
        }


        {
            Map<GearSlot, GearOverride> pyro = dyedArmour("red");
            addEnchants(pyro, ench(4, "fire_protection", "protection"));
            pyro.put(GearSlot.MAINHAND, GearOverride.of(
                GearSlot.MAINHAND, "minecraft:blaze_rod", 1));
            pyro.put(GearSlot.OFFHAND, GearOverride.of(
                GearSlot.OFFHAND, "minecraft:fire_charge", 3));
            presets.add(new GearLoadout(PYROMANCER, "Pyromancer", pyro));
        }


        {
            Map<GearSlot, GearOverride> blazer = new EnumMap<>(GearSlot.class);
            blazer.put(GearSlot.HEAD, GearOverride.of(
                GearSlot.HEAD, "minecraft:netherite_helmet", 1));
            blazer.put(GearSlot.CHEST, GearOverride.of(
                GearSlot.CHEST, "minecraft:golden_chestplate", 1));
            blazer.put(GearSlot.LEGS, GearOverride.of(
                GearSlot.LEGS, "minecraft:netherite_leggings", 1));
            blazer.put(GearSlot.FEET, GearOverride.of(
                GearSlot.FEET, "minecraft:netherite_boots", 1));
            blazer.put(GearSlot.MAINHAND, GearOverride.of(
                GearSlot.MAINHAND, "minecraft:blaze_rod", 1));
            presets.add(new GearLoadout(BLAZER, "Blazer", blazer));
        }


        {
            Map<GearSlot, GearOverride> caster = armour("leather");
            caster.put(GearSlot.HEAD, GearOverride.of(
                GearSlot.HEAD, "minecraft:leather_helmet", 1,
                ench(3, "respiration", "aqua_affinity")));
            caster.put(GearSlot.CHEST, GearOverride.of(
                GearSlot.CHEST, "minecraft:leather_chestplate", 1,
                ench(3, "thorns")));
            caster.put(GearSlot.LEGS, GearOverride.of(
                GearSlot.LEGS, "minecraft:leather_leggings", 1,
                ench(4, "feather_falling")));
            caster.put(GearSlot.FEET, GearOverride.of(
                GearSlot.FEET, "minecraft:leather_boots", 1,
                ench(3, "depth_strider", "feather_falling")));
            addEnchants(caster, ench(4, "protection"), ench(3, "unbreaking"),
                ench(1, "mending"));
            caster.put(GearSlot.MAINHAND, GearOverride.of(
                GearSlot.MAINHAND, "minecraft:golden_sword", 1,
                ench(ench(3, "unbreaking"), ench(1, "mending"))));
            presets.add(new GearLoadout(ENCHANTER, "Enchanter", caster));
        }


        presets.add(new GearLoadout(MEDIC, "Medic", dyedArmour("white")));


        {
            Map<GearSlot, GearOverride> diver = armour("leather");
            diver.put(GearSlot.HEAD, GearOverride.of(
                GearSlot.HEAD, "minecraft:turtle_helmet", 1,
                ench(3, "respiration")));
            presets.add(new GearLoadout(DIVER, "Diver", diver));
        }


        {
            Map<GearSlot, GearOverride> elf = dyedArmour("light_blue");
            elf.put(GearSlot.MAINHAND, GearOverride.of(
                GearSlot.MAINHAND, "minecraft:bow", 1));
            presets.add(new GearLoadout(ELF, "Elf", elf));
        }


        {
            Map<GearSlot, GearOverride> ffa = armour("golden");
            ffa.remove(GearSlot.HEAD);
            presets.add(new GearLoadout(FFA, "Full Gold (no helmet)", ffa));
        }

        return List.copyOf(presets);
    }


    @SafeVarargs
    private static void addEnchants(Map<GearSlot, GearOverride> map,
                                    Map<String, Integer>... enchantments) {
        for (GearSlot slot : GearSlot.armourSlots()) {
            GearOverride existing = map.get(slot);
            if (existing == null || existing.removesItem()) {
                continue;
            }
            Map<String, Integer> merged = new LinkedHashMap<>(existing.enchantments());
            for (Map<String, Integer> extra : enchantments) {
                merged.putAll(extra);
            }
            map.put(slot, GearOverride.of(
                slot, existing.itemId(), existing.count(), merged));
        }
    }
}

