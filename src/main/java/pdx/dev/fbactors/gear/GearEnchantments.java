package pdx.dev.fbactors.gear;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import pdx.dev.fbactors.FlashbackActors;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


public final class GearEnchantments {


    public record Enchantment(String id, String displayName, int maxLevel,
                              boolean armourOnly) {


        public String shortId() {
            int colon = this.id.indexOf(':');
            return colon < 0 ? this.id : this.id.substring(colon + 1);
        }


        public boolean matches(String query) {
            return this.shortId().contains(query)
                || this.displayName().toLowerCase(Locale.ROOT).contains(query)
                || this.id.contains(query);
        }
    }


    private static final List<Enchantment> CATALOGUE = List.of(
        new Enchantment("minecraft:protection", "Protection", 4, true),
        new Enchantment("minecraft:fire_protection", "Fire Protection", 4, true),
        new Enchantment("minecraft:blast_protection", "Blast Protection", 4, true),
        new Enchantment("minecraft:projectile_protection", "Projectile Protection", 4, true),
        new Enchantment("minecraft:thorns", "Thorns", 3, true),
        new Enchantment("minecraft:unbreaking", "Unbreaking", 3, false),
        new Enchantment("minecraft:mending", "Mending", 1, false),
        new Enchantment("minecraft:binding_curse", "Curse of Binding", 1, true),
        new Enchantment("minecraft:soul_speed", "Soul Speed", 1, true),
        new Enchantment("minecraft:feather_falling", "Feather Falling", 4, true),
        new Enchantment("minecraft:depth_strider", "Depth Strider", 3, true),
        new Enchantment("minecraft:aqua_affinity", "Aqua Affinity", 1, true),
        new Enchantment("minecraft:respiration", "Respiration", 3, true),
        new Enchantment("minecraft:piercing", "Piercing", 3, false),
        new Enchantment("minecraft:swift_sneak", "Swift Sneak", 3, true),
        new Enchantment("minecraft:silk_touch", "Silk Touch", 1, false));

    private static final Map<String, Enchantment> BY_ID = index(CATALOGUE);

    private static volatile EnchantBridge bridge;

    private GearEnchantments() {
    }


    public static List<Enchantment> all() {
        return CATALOGUE;
    }


    public static List<Enchantment> available(boolean armourOnly) {
        if (!armourOnly) {
            return CATALOGUE;
        }
        List<Enchantment> filtered = new ArrayList<>(CATALOGUE.size());
        for (Enchantment enchantment : CATALOGUE) {
            if (enchantment.armourOnly()) {
                filtered.add(enchantment);
            }
        }
        return List.copyOf(filtered);
    }

    public static List<Enchantment> search(@Nullable String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return CATALOGUE;
        }

        List<Enchantment> prefix = new ArrayList<>();
        List<Enchantment> contains = new ArrayList<>();
        for (Enchantment enchantment : CATALOGUE) {
            String shortId = enchantment.shortId();
            String name = enchantment.displayName().toLowerCase(Locale.ROOT);
            if (shortId.startsWith(needle) || name.startsWith(needle)) {
                prefix.add(enchantment);
            } else if (shortId.contains(needle) || name.contains(needle)
                || enchantment.id().contains(needle)) {
                contains.add(enchantment);
            }
        }
        prefix.addAll(contains);
        return List.copyOf(prefix);
    }


    public static int maxLevel(@Nullable String enchantmentId) {
        Enchantment known = enchantmentId == null ? null : BY_ID.get(enchantmentId.trim());
        return known == null ? 1 : known.maxLevel();
    }


    @Nullable
    public static Enchantment byId(@Nullable String enchantmentId) {
        return enchantmentId == null ? null : BY_ID.get(enchantmentId.trim());
    }

    private static Map<String, Enchantment> index(List<Enchantment> entries) {
        Map<String, Enchantment> map = new LinkedHashMap<>();
        for (Enchantment entry : entries) {
            map.put(entry.id(), entry);
        }
        return Map.copyOf(map);
    }


    public static boolean apply(ItemStack stack, Map<String, Integer> enchantments) {
        if (stack == null || stack.isEmpty() || enchantments == null
            || enchantments.isEmpty()) {
            return false;
        }
        EnchantBridge active = activeBridge();
        if (active == null) {
            return false;
        }
        return active.apply(stack, enchantments);
    }


    public static boolean isSupported() {
        return activeBridge() != null;
    }


    @Nullable
    private static EnchantBridge activeBridge() {
        EnchantBridge local = bridge;
        if (local != null) {
            return local.isUsable() ? local : null;
        }
        EnchantBridge resolved = EnchantBridge.resolve();
        if (!resolved.isUsable()) {
            return null;
        }
        bridge = resolved;
        return resolved;
    }


    public static boolean replacesRecordedEnchantments() {
        return true;
    }

    private static final class EnchantBridge {

        private final Method parseId;
        private final Object enchantmentRegistry;
        private final Method getOptional;
        private final Method wrapAsHolder;
        private final Object emptyComponent;
        private final Constructor<?> mutableCtor;
        private final Method mutableSet;
        private final Method toImmutable;
        private final Method setEnchantments;

        private EnchantBridge(Method parseId, Object enchantmentRegistry,
                              Method getOptional, Method wrapAsHolder,
                              Object emptyComponent, Constructor<?> mutableCtor,
                              Method mutableSet, Method toImmutable,
                              Method setEnchantments) {
            this.parseId = parseId;
            this.enchantmentRegistry = enchantmentRegistry;
            this.getOptional = getOptional;
            this.wrapAsHolder = wrapAsHolder;
            this.emptyComponent = emptyComponent;
            this.mutableCtor = mutableCtor;
            this.mutableSet = mutableSet;
            this.toImmutable = toImmutable;
            this.setEnchantments = setEnchantments;
        }


        private boolean isUsable() {
            return this.parseId != null && this.enchantmentRegistry != null
                && this.getOptional != null && this.emptyComponent != null
                && this.mutableCtor != null && this.mutableSet != null
                && this.toImmutable != null && this.setEnchantments != null;
        }


        private static EnchantBridge unusable() {
            return new EnchantBridge(null, null, null, null, null, null, null, null, null);
        }

        private static EnchantBridge resolve() {
            try {

                Method parseId = null;
                for (String className : new String[]{
                    "net.minecraft.resources.Identifier",
                    "net.minecraft.resources.ResourceLocation"}) {
                    try {
                        parseId = Class.forName(className)
                            .getMethod("tryParse", String.class);
                        break;
                    } catch (ReflectiveOperationException | LinkageError ignored) {

                    }
                }
                if (parseId == null) {
                    FlashbackActors.LOGGER.warn(
                        "Gear enchantments unavailable: no resource-location class found");
                    return unusable();
                }


                Object bootstrap = Class.forName("net.minecraft.core.registries.BuiltInRegistries")
                    .getField("REGISTRY").get(null);
                Object enchantKey = Class.forName("net.minecraft.core.registries.Registries")
                    .getField("ENCHANTMENT").get(null);
                Class<?> resourceKeyClass =
                    Class.forName("net.minecraft.resources.ResourceKey");
                Object registry = null;
                java.lang.reflect.Method hop = null;
                for (String hopName : new String[]{"getOrThrow", "get"}) {
                    try {
                        hop = bootstrap.getClass()
                            .getMethod(hopName, resourceKeyClass);
                        registry = hop.invoke(bootstrap, enchantKey);
                    } catch (NoSuchMethodException ignored) {

                    }
                    if (registry != null) {
                        break;
                    }
                }
                if (registry == null) {
                    FlashbackActors.LOGGER.warn(
                        "Gear enchantments unavailable: root registry {} returned null for {}",
                        bootstrap.getClass().getName(), enchantKey);
                    return unusable();
                }


                Method getOptional = registry.getClass()
                    .getMethod("getOptional", parseId.getReturnType());


                Method wrapAsHolder = null;
                try {
                    wrapAsHolder = registry.getClass().getMethod("wrapAsHolder", Object.class);
                } catch (ReflectiveOperationException ignored) {

                }


                Class<?> enchantmentsClass =
                    Class.forName("net.minecraft.world.item.enchantment.ItemEnchantments");
                Object empty = enchantmentsClass.getField("EMPTY").get(null);
                Class<?> mutableClass =
                    Class.forName("net.minecraft.world.item.enchantment.ItemEnchantments$Mutable");
                Constructor<?> ctor = mutableClass.getConstructor(enchantmentsClass);
                Method toImmutable = mutableClass.getMethod("toImmutable");
                Method setEnchantments = Class.forName(
                    "net.minecraft.world.item.enchantment.EnchantmentHelper")
                    .getMethod("setEnchantments", ItemStack.class, enchantmentsClass);


                Method mutableSet = null;
                for (Method candidate : mutableClass.getMethods()) {
                    if ("set".equals(candidate.getName())
                        && candidate.getParameterCount() == 2
                        && candidate.getParameterTypes()[1] == int.class) {
                        mutableSet = candidate;
                        break;
                    }
                }
                if (mutableSet == null) {
                    FlashbackActors.LOGGER.warn("Gear enchantments unavailable: "
                        + "no two-argument set() on ItemEnchantments.Mutable");
                    return unusable();
                }

                return new EnchantBridge(parseId, registry, getOptional, wrapAsHolder,
                    empty, ctor, mutableSet, toImmutable, setEnchantments);
            } catch (Throwable t) {

                FlashbackActors.LOGGER.warn("Gear enchantments unavailable on this "
                    + "Minecraft version; overrides will apply items unenchanted", t);
                return unusable();
            }
        }

        @Nullable
        private Object holder(String enchantmentId) {
            Object parsed;
            try {
                parsed = this.parseId.invoke(null, enchantmentId);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            }
            if (parsed == null) {

                return null;
            }

            Object value;
            try {
                Object optional = this.getOptional.invoke(this.enchantmentRegistry, parsed);
                if (!(optional instanceof Optional<?> found)) {
                    return null;
                }
                value = found.orElse(null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            }
            if (value == null) {
                return null;
            }
            return this.wrapAsHolder == null ? value : asHolder(value);
        }


        private Object asHolder(Object value) {
            try {
                return this.wrapAsHolder.invoke(this.enchantmentRegistry, value);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return value;
            }
        }


        private boolean apply(ItemStack stack, Map<String, Integer> enchantments) {
            boolean complete = true;
            try {
                Object mutable = this.mutableCtor.newInstance(this.emptyComponent);
                for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                    String id = entry.getKey();
                    Integer level = entry.getValue();
                    if (id == null || level == null || level <= 0) {
                        continue;
                    }
                    Object holder = this.holder(id);
                    if (holder == null) {

                        complete = false;
                        continue;
                    }

                    this.mutableSet.invoke(mutable, holder, Math.min(level, maxLevel(id)));
                }
                Object component = this.toImmutable.invoke(mutable);
                this.setEnchantments.invoke(null, stack, component);
                return complete;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {

                return false;
            }
        }
    }
}
