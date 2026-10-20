package pdx.dev.fbactors.morph;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import pdx.dev.fbactors.FlashbackActors;


public final class MorphCompatibility {


    private static final Set<Requirements> WARNED = ConcurrentHashMap.newKeySet();

    private MorphCompatibility() {
    }

    public enum Requirements {


        NONE,


        RENDERER_ACCESSOR
    }


    public static Requirements requirementsFor(@Nullable MorphDefinition morph) {
        if (morph == null) {


            return Requirements.RENDERER_ACCESSOR;
        }
        if (hasRendererAccessor()) {


            return Requirements.NONE;
        }
        if (Float.isFinite(morph.scale()) && morph.scale() == 1.0f) {


            return Requirements.NONE;
        }
        return Requirements.RENDERER_ACCESSOR;
    }

    public static boolean canApply(@Nullable MorphDefinition morph, boolean cloneAlive) {
        return cloneAlive && requirementsFor(morph) == Requirements.NONE;
    }


    public static List<MorphDefinition> available() {
        return available(Morph.registry());
    }


    public static List<MorphDefinition> available(@Nullable MorphRegistry registry) {
        if (registry == null) {
            return List.of();
        }
        List<MorphDefinition> result = new ArrayList<>();
        for (MorphDefinition morph : registry.all()) {
            if (requirementsFor(morph) == Requirements.NONE) {
                result.add(morph);
            }
        }
        return List.copyOf(result);
    }


    public static Set<Requirements> allRequirements() {
        return EnumSet.allOf(Requirements.class);
    }


    public static boolean hasRendererAccessor() {
        return true;
    }


    public static boolean report(@Nullable MorphDefinition morph) {
        Requirements needs = requirementsFor(morph);
        if (needs == Requirements.NONE || !WARNED.add(needs)) {
            return false;
        }
        FlashbackActors.LOGGER.warn(
            "Morphs other than player-sized ones need {} - they are applied at player "
                + "size until the renderer accessor is added (see MorphApplier)", needs);
        return true;
    }
}
