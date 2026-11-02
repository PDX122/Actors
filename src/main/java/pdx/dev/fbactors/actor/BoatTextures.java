package pdx.dev.fbactors.actor;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;
import java.util.*;

public final class BoatTextures {
    private record Cached(Object pack, String id, DynamicTexture texture) {}
    private static final Map<String, Cached> textures = new HashMap<>();
    private static final Map<Object, Boolean> states = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<UUID> authored = new HashSet<>();
    private static Object replay;
    private static final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
    private BoatTextures() {}

    private static synchronized void session() {
        Object current = Flashback.getReplayServer();
        if (current != replay) { authored.clear(); replay = current; }
    }
    public static synchronized void track(net.minecraft.world.entity.Entity boat) {
        session();
        if (boat instanceof BoatPhysics.Boat && replay != null) authored.add(boat.getUUID());
    }
    public static synchronized void note(Object state, net.minecraft.world.entity.Entity boat) {
        session();
        states.put(state, replay != null && authored.contains(boat.getUUID()));
    }
    public static boolean begin(Object state) {
        boolean previous = active.get();
        active.set(Boolean.TRUE.equals(states.get(state)));
        return previous;
    }
    public static void end(boolean previous) { active.set(previous); }
    public static boolean active() { return active.get(); }

    public static String texture(String original) {
        if (!active() || !original.startsWith("minecraft:textures/entity/")
            || !(original.contains("/boat/") || original.contains("/chest_boat/"))) return original;
        var mc = Minecraft.getInstance();
        var id = Compat.boatTextureId(original);
        var resource = mc.getResourceManager().getResource(id).orElse(null);
        if (resource == null) return original;
        Cached cached = textures.get(original);
        if (cached != null && cached.pack() == resource.source()
            && (cached.texture() == null || mc.getTextureManager().getTexture(Compat.boatTextureId(cached.id())) == cached.texture()))
            return cached.id();
        if (cached != null && cached.texture() != null)
            mc.getTextureManager().release(Compat.boatTextureId(cached.id()));
        try (var input = resource.open(); var image = NativeImage.read(input)) {
            boolean transparent = BoatTextureVisibility.fullyTransparent(image.getWidth(), image.getHeight(),
                (x, y) -> image.getLuminanceOrAlpha(x, y) & 255);
            if (transparent) {
                var vanilla = Compat.vanillaBoatTexture(original);
                if (vanilla != null) {
                    try (var stream = vanilla.get()) {
                        String replacement = "fbactors:boat_fallback/" + id.getPath();
                        var texture = Compat.registerBoatTexture(replacement, NativeImage.read(stream));
                        textures.put(original, new Cached(resource.source(), replacement, texture));
                        FlashbackActors.LOGGER.info("Restored actor boat texture {} hidden by pack {}", original, resource.sourcePackId());
                        return replacement;
                    }
                }
            }
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn("Could not inspect actor boat texture {}", original, e);
        }
        textures.put(original, new Cached(resource.source(), original, null));
        return original;
    }
}
