package pdx.dev.fbactors.compat;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.moulberry.flashback.FilePlayerSkin;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.PacketSendListener;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.resources.PlayerSkin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import pdx.dev.fbactors.actor.ActorClonePlayer;
import net.minecraft.world.entity.EquipmentSlot;
import pdx.dev.fbactors.morph.Morph;
import pdx.dev.fbactors.morph.MorphDefinition;
import pdx.dev.fbactors.morph.MorphRegistry;
import pdx.dev.fbactors.morph.MorphRenderState;
import pdx.dev.fbactors.morph.MorphRenderInfo;
import pdx.dev.fbactors.swing.InvulnerabilityAccess;
import pdx.dev.fbactors.swing.JumpAccess;
import pdx.dev.fbactors.swing.SwingAccess;
import net.minecraft.world.entity.LivingEntity;

import net.minecraft.world.InteractionHand;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.components.Button;
import com.moulberry.flashback.screen.FlashbackButton;
import net.minecraft.network.chat.Component;

public final class Compat {

    private static final org.slf4j.Logger LOGGER =
        org.slf4j.LoggerFactory.getLogger("fbactors/drone");


    private static final Map<UUID, PlayerInfo> OVERRIDE_PROFILES = new HashMap<>();



    private Compat() {
    }

    public static long headTexture(UUID id) {
        ResourceLocation texture = skinTexture(id);
        if (texture == null) {
            return 0L;
        }
        AbstractTexture abstractTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (abstractTexture == null) {
            return 0L;
        }
        return abstractTexture.getId();
    }

    public static void sampleNearest(long handle) {
    }

    @Nullable
    private static ResourceLocation skinTexture(UUID id) {
        // Overrides first: a stored profile may have gained textures since the
        // client entity spawned with the bare one, so the level copy can be stale.
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            FilePlayerSkin fromFile = editorState.skinOverrideFromFile.get(id);
            if (fromFile != null) {
                return fromFile.getSkin().texture();
            }

        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            Player player = level.getPlayerByUUID(id);
            if (player instanceof AbstractClientPlayer clientPlayer) {
                return clientPlayer.getSkin().texture();
            }
        }
            GameProfile profile = editorState.skinOverride.get(id);
            if (profile != null) {
                PlayerInfo info = OVERRIDE_PROFILES.get(id);
                if (info == null || info.getProfile() != profile) {
                    info = new PlayerInfo(profile, false);
                    OVERRIDE_PROFILES.put(id, info);
                }
                return info.getSkin().texture();
            }
        }

        Object head = pdx.dev.fbactors.skin.SkinTextures.headLocation(id);
        if (head != null) {
            return (ResourceLocation) head;
        }
        return DefaultPlayerSkin.get(id).texture();
    }

    /**
     * A file skin with a cape laid over it.
     *
     * <p>Ported from the 1.21.10 implementation: the surrounding code is
     * identical, only the texture and skin types move. On this target the
     * cape is a plain texture id (no client-asset wrapper yet) and the skin
     * is rebuilt through the six-argument constructor. Safe to call anywhere:
     * it never throws for a missing cape.
     */
    public static FilePlayerSkin fileSkinWithCape(String skinPath, @Nullable String capePath) {
        return fileSkinWithLook(skinPath, capePath, null);
    }

    /**
     * A file skin with a cape laid over it and an optional arm-model override.
     *
     * <p>Same cape behaviour as the 1.21.10 build, plus {@code slim}: null
     * keeps the auto-detected model, TRUE forces slim arms, FALSE forces
     * classic. Model-only picks skip the cape load entirely.
     */
    public static FilePlayerSkin fileSkinWithLook(String skinPath, @Nullable String capePath,
        @Nullable Boolean slim) {
        if ((capePath == null || capePath.isBlank()) && slim == null) {
            return new FilePlayerSkin(skinPath);
        }
        return new FilePlayerSkin(skinPath) {
            private volatile PlayerSkin withCape;
            private volatile boolean capeTried;

            @Override
            public PlayerSkin getSkin() {
                PlayerSkin body = super.getSkin();
                PlayerSkin done = withCape;
                if (capeTried) {
                    return done != null ? done : body;
                }
                capeTried = true;
                try {
                    com.mojang.blaze3d.platform.NativeImage image = readCape(capePath);
                    if (image == null) {
                        // No cape to lay: a model-only pick still rebuilds.
                        if (slim == null) {
                            return body;
                        }
                        PlayerSkin modelled = new PlayerSkin(body.texture(), body.textureUrl(),
                            body.capeTexture(), body.elytraTexture(),
                            wantedModel(body, slim), body.secure());
                        withCape = modelled;
                        return modelled;
                    }
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("fbactors",
                        "cape/" + sha1Hex(capePath));
                    Minecraft.getInstance().getTextureManager().release(id);
                    Minecraft.getInstance().getTextureManager().register(id,
                        new net.minecraft.client.renderer.texture.DynamicTexture(image));
                    // The cape id goes straight into the rebuilt skin: it is
                    // the id the pixels were just registered under, so unlike
                    // the single-arg path there is nothing to rewrite.
                    done = new PlayerSkin(body.texture(), body.textureUrl(), id,
                        body.elytraTexture(), wantedModel(body, slim), body.secure());
                    withCape = done;
                    return done;
                } catch (Exception e) {
                    LOGGER.warn("Could not load cape from {}", capePath, e);
                    return body;
                }
            }
        };
    }

    /**
     * The arm model for a look pick: the body's own when auto, else forced.
     */
    private static PlayerSkin.Model wantedModel(PlayerSkin body, @Nullable Boolean slim) {
        if (slim == null) {
            return body.model();
        }
        return slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
    }

    /** Hex sha1, so repeated picks of one cape reuse one texture id. */
    private static String sha1Hex(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(40);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * A cape over the default look, with no skin file involved.
     *
     * <p>Same body the game itself draws for a player with no skin, plus the
     * cape file. Exists so a cape never needs a skin file first. Returns null
     * when the cape cannot be built, and the caller then stores nothing
     * rather than half a look.
     */
    @Nullable
    public static FilePlayerSkin defaultSkinWithCape(java.util.UUID id, String capePath) {
        if (capePath == null || capePath.isBlank()) {
            return null;
        }
        com.mojang.blaze3d.platform.NativeImage image = readCape(capePath);
        if (image == null) {
            return null;
        }
        PlayerSkin plain = DefaultPlayerSkin.get(id);
        ResourceLocation texId = ResourceLocation.fromNamespaceAndPath("fbactors",
            "cape/" + sha1Hex(capePath));
        Minecraft.getInstance().getTextureManager().release(texId);
        Minecraft.getInstance().getTextureManager().register(texId,
            new net.minecraft.client.renderer.texture.DynamicTexture(image));
        PlayerSkin caped = new PlayerSkin(plain.texture(), plain.textureUrl(), texId,
            plain.elytraTexture(), plain.model(), plain.secure());
        return new FilePlayerSkin("__cape_only__") {
            @Override
            public PlayerSkin getSkin() {
                return caped;
            }
        };
    }

    /**
     * Reads and validates a cape PNG, or null.
     *
     * <p>Shared by both cape builders so a bad file fails one way: missing,
     * oversized, undecodable, or not on a 2:1 canvas. The caller falls back
     * to the plain body, so a bad cape costs its cape and never the skin.
     */
    @Nullable
    private static com.mojang.blaze3d.platform.NativeImage readCape(String capePath) {
        if (capePath == null || capePath.isBlank()) {
            return null;
        }
        try {
            java.nio.file.Path capeFile = java.nio.file.Path.of(capePath);
            if (!java.nio.file.Files.isRegularFile(capeFile)
                || java.nio.file.Files.size(capeFile) > 8 * 1024 * 1024) {
                return null;
            }
            com.mojang.blaze3d.platform.NativeImage image;
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(capeFile)) {
                image = com.mojang.blaze3d.platform.NativeImage.read(in);
            }
            if (image.getWidth() <= 0 || image.getHeight() <= 0
                || image.getWidth() % 64 != 0
                || image.getHeight() * 2 != image.getWidth()) {
                image.close();
                return null;
            }
            return image;
        } catch (Exception e) {
            LOGGER.warn("Could not load cape from {}", capePath, e);
            return null;
        }
    }

    public static KeyMapping registerKey(String modId, String translationKey, String keyName) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
            translationKey, InputConstants.Type.KEYSYM, InputConstants.getKey(keyName).getValue(), "key.categories." + modId + ".actors"));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return ClientCommandManager.literal(name);
    }

    public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(
            String name, ArgumentType<T> type) {
        return ClientCommandManager.argument(name, type);
    }

    public static EntityType<?> playerEntityType() {
        return EntityType.PLAYER;
    }

    public static String profileName(GameProfile profile) {
        return profile.getName();
    }

    public static UUID profileId(GameProfile profile) {
        return profile.getId();
    }

    public static boolean profileHasTextures(GameProfile profile) {
        if (profile == null) {
            return false;
        }
        java.util.Collection<?> textures = profile.getProperties().get("textures");
        return textures != null && !textures.isEmpty();
    }

    /**
     * Fetches a textured profile for an account UUID. Blocking: call off-thread.
     *
     * <p>Null when the fetch fails or the account has no textures. The session
     * service is the only source of texture properties for a bare UUID.
     */
    @Nullable
    /**
     * Opens Flashback's native skin-file dialog. Async, never blocks.
     *
     * <p>Flashback ships the dialog itself (NFD on these targets), so this is
     * the same picker every other Flashback screen uses: a real OS dialog, no
     * path typing, no subprocess, nothing on the render thread. The returned
     * future completes with the picked path, or null when the user cancels.
     * Poll it (it is done within a frame or two of the user choosing) rather
     * than joining it: joining on the render thread would freeze the game
     * while the dialog is open.
     */
    public static java.util.concurrent.CompletableFuture<String> openSkinFile(
            String defaultDir) {
        return com.moulberry.flashback.exporting.AsyncFileDialogs
            .openFileDialog(defaultDir, "Skin PNG", "png");
    }

    public static GameProfile fetchTexturedProfile(UUID id) {
        try {
            var result = Minecraft.getInstance().getMinecraftSessionService()
                .fetchProfile(id, false);
            GameProfile fetched = result == null ? null : result.profile();
            return fetched != null && profileHasTextures(fetched) ? fetched : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves an account's head texture, blocking. Background threads only.
     *
     * <p>NEVER call on the render thread: the first call per account performs
     * session and download I/O inline, which freezes the game on a slow
     * network. The background chain in SkinTextures is the only caller; the
     * render thread reads SkinTextures.headLocation, which never blocks.
     *
     * @return the texture location, or null when it cannot be resolved
     */
    @Nullable
    public static Object loadHead(GameProfile profile) {
        try {
            java.util.Optional<net.minecraft.client.resources.PlayerSkin> skin =
                Minecraft.getInstance().getSkinManager().getOrLoad(profile)
                    .get(20, java.util.concurrent.TimeUnit.SECONDS);
            return skin.map(value -> value.texture()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static Entity entityByUuid(ServerLevel level, UUID id) {
        Entity found = level.getEntity(id);
        if (found != null) {
            return found;
        }
        MinecraftServer server = level.getServer();
        if (server != null) {
            for (ServerLevel other : server.getAllLevels()) {
                found = other.getEntity(id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static void snapTo(Entity entity, double x, double y, double z, float yaw, float pitch) {
        entity.moveTo(x, y, z, yaw, pitch);
    }

    public static void cancelInterpolation(Entity entity) {

    }


    public static Connection silentConnection() {
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
            }
        };
        new EmbeddedChannel(connection);
        return connection;
    }

    public static ResourceKey<Level> dimensionKey(String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension == null ? "minecraft:overworld" : dimension);
        if (id == null) {
            id = ResourceLocation.parse("minecraft:overworld");
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public static String dimensionId(@Nullable ServerLevel level) {
        return level == null ? "minecraft:overworld" : level.dimension().location().toString();
    }

    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    public static ItemStack itemStack(String itemId, int count) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
return new ItemStack(item, Math.max(1, count));
    }
    public static String itemStackNbt(Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        var tag = stack.save(level.registryAccess());
        return tag.toString();
    }

    public static ItemStack itemStackFromNbt(Level level, String itemId, int count, String nbt) {
        ItemStack stack = itemStack(itemId, count);
        if (stack.isEmpty() || nbt == null || nbt.isEmpty()) {
            return stack;
        }
        try {
            var tag = net.minecraft.nbt.TagParser.parseTag(nbt);
            var parsed = ItemStack.parse(level.registryAccess(), tag);
            return parsed.orElse(stack);
        } catch (Exception e) {
            return stack;
        }
    }

    public static boolean hasBlockEntity(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) != null;
    }

    public static String blockEntityNbt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return "";
        }
        var tag = be.saveWithFullMetadata(level.registryAccess());
        return tag.toString();
    }

    public static void applyBlockEntityNbt(ServerLevel level, BlockPos pos, String type, String nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        try {
            var tag = net.minecraft.nbt.TagParser.parseTag(nbt);
            be.loadWithComponents(tag, level.registryAccess());
        } catch (Exception e) {
            // Log error
        }
    }

    /**
     * Container id carried by every replay container GUI packet. Viewers track
     * a single open screen at a time, so one constant keeps open and close in
     * agreement (the old close sent id 0, which never matched the open).
     */
    private static final int REPLAY_CONTAINER_ID = 1;

    /**
     * Containers each clone currently has open. Close only carries the clone,
     * so the open side has to remember where the lid and its sounds live in
     * order to shut them on close.
     */
    private static final Map<UUID, BlockPos> OPEN_CONTAINER_POS = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerLevel> OPEN_CONTAINER_LEVEL = new ConcurrentHashMap<>();

    public static void openContainer(ReplayServer server, ServerLevel level, ActorClonePlayer clone, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof net.minecraft.world.Container)
            && !(be instanceof net.minecraft.world.MenuProvider)) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        Component title = be instanceof net.minecraft.world.MenuProvider provider
            ? provider.getDisplayName()
            : Component.literal("Container");
        for (ServerPlayer viewer : server.getReplayViewers()) {
            viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(
                REPLAY_CONTAINER_ID, containerMenuType(state, be), title));
        }
        // Lids and open sounds go through the level, which is the same
        // block-event and broadcast path vanilla's own container code uses, so
        // every viewer tracking the chunk sees and hears the genuine effect.
        if (isLidContainer(be)) {
            level.blockEvent(pos, state.getBlock(), 1, 1);
        }
        playContainerSound(level, pos, be, true);
        UUID id = clone.getUUID();
        OPEN_CONTAINER_POS.put(id, new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        OPEN_CONTAINER_LEVEL.put(id, level);
    }

    public static void closeContainer(ReplayServer server, ActorClonePlayer clone) {
        for (ServerPlayer viewer : server.getReplayViewers()) {
            viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerClosePacket(REPLAY_CONTAINER_ID));
        }
        UUID id = clone.getUUID();
        BlockPos pos = OPEN_CONTAINER_POS.remove(id);
        ServerLevel level = OPEN_CONTAINER_LEVEL.remove(id);
        if (pos == null || level == null) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        if (isLidContainer(be)) {
            level.blockEvent(pos, level.getBlockState(pos).getBlock(), 1, 0);
        }
        playContainerSound(level, pos, be, false);
    }

    /**
     * Picks the client GUI for the block entity at the container position.
     * Every name referenced here was checked against all nine supported
     * targets: chest (single 27 slots, double 54), ender chest, barrel,
     * shulker box, hopper, brewing stand, the three furnaces, dispenser and
     * dropper (one 3x3 for both: the dropper entity extends the dispenser
     * one), crafter, beacon and lectern, with 9x3 as the fallback.
     */
    private static net.minecraft.world.inventory.MenuType<?> containerMenuType(
        net.minecraft.world.level.block.state.BlockState state, BlockEntity be) {
        if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
            // Trapped chests reuse the chest block entity, so the TYPE property
            // of the block state is what tells single and double chests apart.
            try {
                Object chestType = state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
                if (chestType != null && "SINGLE".equals(chestType.toString())) {
                    return net.minecraft.world.inventory.MenuType.GENERIC_9x3;
                }
                return net.minecraft.world.inventory.MenuType.GENERIC_9x6;
            } catch (IllegalArgumentException e) {
                return net.minecraft.world.inventory.MenuType.GENERIC_9x3;
            }
        }
        if (be instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity) {
            return net.minecraft.world.inventory.MenuType.GENERIC_9x3;
        }
        if (be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity) {
            return net.minecraft.world.inventory.MenuType.GENERIC_9x3;
        }
        if (be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity) {
            return net.minecraft.world.inventory.MenuType.SHULKER_BOX;
        }
        if (be instanceof net.minecraft.world.level.block.entity.HopperBlockEntity) {
            return net.minecraft.world.inventory.MenuType.HOPPER;
        }
        if (be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity) {
            return net.minecraft.world.inventory.MenuType.BREWING_STAND;
        }
        if (be instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity) {
            return net.minecraft.world.inventory.MenuType.BLAST_FURNACE;
        }
        if (be instanceof net.minecraft.world.level.block.entity.SmokerBlockEntity) {
            return net.minecraft.world.inventory.MenuType.SMOKER;
        }
        if (be instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity) {
            return net.minecraft.world.inventory.MenuType.FURNACE;
        }
        if (be instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity) {
            return net.minecraft.world.inventory.MenuType.GENERIC_3x3;
        }
        if (be instanceof net.minecraft.world.level.block.entity.CrafterBlockEntity) {
            return net.minecraft.world.inventory.MenuType.CRAFTER_3x3;
        }
        if (be instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity) {
            return net.minecraft.world.inventory.MenuType.BEACON;
        }
        if (be instanceof net.minecraft.world.level.block.entity.LecternBlockEntity) {
            return net.minecraft.world.inventory.MenuType.LECTERN;
        }
        return net.minecraft.world.inventory.MenuType.GENERIC_9x3;
    }

    /**
     * Whether the block entity has an animated lid driven by block event 1,
     * the same event vanilla's container openers fire. Barrel and shulker box
     * entities animate this way without implementing LidBlockEntity, hence the
     * explicit list instead of the interface.
     */
    private static boolean isLidContainer(BlockEntity be) {
        return be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
            || be instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity
            || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
            || be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
    }

    private static void playContainerSound(ServerLevel level, BlockPos pos, BlockEntity be, boolean open) {
        net.minecraft.sounds.SoundEvent sound = containerSound(level, pos, be, open);
        if (sound == null) {
            return;
        }
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            sound, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
    }

    private static net.minecraft.sounds.SoundEvent containerSound(ServerLevel level, BlockPos pos, BlockEntity be, boolean open) {
        if (be instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity) {
            return open ? net.minecraft.sounds.SoundEvents.ENDER_CHEST_OPEN : net.minecraft.sounds.SoundEvents.ENDER_CHEST_CLOSE;
        }
        if (be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity) {
            return open ? net.minecraft.sounds.SoundEvents.BARREL_OPEN : net.minecraft.sounds.SoundEvents.BARREL_CLOSE;
        }
        if (be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity) {
            return open ? net.minecraft.sounds.SoundEvents.SHULKER_BOX_OPEN : net.minecraft.sounds.SoundEvents.SHULKER_BOX_CLOSE;
        }
        if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
            return open ? net.minecraft.sounds.SoundEvents.CHEST_OPEN : net.minecraft.sounds.SoundEvents.CHEST_CLOSE;
        }
        return null;
    }

    public static void updateContainerSlot(ServerLevel level, BlockPos pos, int slot, ItemStack stack) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof net.minecraft.world.Container container) {
            if (slot >= 0 && slot < container.getContainerSize()) {
                container.setItem(slot, stack);
            }
        }
    }


    @Nullable
    public static Holder<MobEffect> effectHolder(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(effectId);
        if (id == null) {
            return null;
        }
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(id).orElse(null);
        // wrapAsHolder hands back the registry's own holder, and that is the
        // only kind that compares equal to the ones LivingEntity files away in
        // its effect map, which is what removing an effect looks up.
        return effect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    public static String effectId(Holder<MobEffect> holder) {
        if (holder == null) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(holder.value());
        return id == null ? "" : id.toString();
    }

    public static void spawnLightning(ServerLevel level, double x, double y, double z) {
        net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(
            net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, level);
        bolt.setPos(x, y, z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    public static void spawnAnvil(ServerLevel level, double x, double y, double z) {
        level.addFreshEntity(net.minecraft.world.entity.item.FallingBlockEntity.fall(
            level, net.minecraft.core.BlockPos.containing(x, y, z),
            net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState()));
    }

    public static void burst(ServerLevel level, pdx.dev.fbactors.fx.FxKinds.Burst burst,
                             double x, double y, double z, int count, double speed) {
        net.minecraft.core.particles.ParticleOptions particle = switch (burst) {
            case POOF -> net.minecraft.core.particles.ParticleTypes.POOF;
            case EXPLODE -> net.minecraft.core.particles.ParticleTypes.EXPLOSION;
            case LAVA -> net.minecraft.core.particles.ParticleTypes.LAVA;
            case WITHER -> net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH;
            case HIT -> net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT;
            case BUBBLE -> net.minecraft.core.particles.ParticleTypes.BUBBLE;
            case INSTANT -> net.minecraft.core.particles.ParticleTypes.INSTANT_EFFECT;
            case ENDROD -> net.minecraft.core.particles.ParticleTypes.END_ROD;
        };
        level.sendParticles(particle, x, y, z, count, 0.0, 0.3, 0.0, speed);
    }

    public static void sound(ServerLevel level, pdx.dev.fbactors.fx.FxKinds.Ambient ambient,
                             double x, double y, double z) {
        level.playSound(null, x, y, z, switch (ambient) {
            case THUNDER -> net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER;
            case EXPLODE -> net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value();
            case FIRE -> net.minecraft.sounds.SoundEvents.FIRE_AMBIENT;
            case WITHER -> net.minecraft.sounds.SoundEvents.WITHER_AMBIENT;
            case HURT -> net.minecraft.sounds.SoundEvents.PLAYER_HURT;
            case DROWN -> net.minecraft.sounds.SoundEvents.PLAYER_HURT_DROWN;
            case POISON -> net.minecraft.sounds.SoundEvents.PLAYER_BURP;
            case TELEPORT -> net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT;
        }, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static int key(String name) {
        return InputConstants.getKey(name).getValue();
    }

    public static long currentWindowHandle() {
        return GLFW.glfwGetCurrentContext();
    }

    public static boolean keyDown(long window, int key) {
        return window != 0L && GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    /** Window-independent key poll for per-frame checks. */
    public static boolean keyDown(int key) {
        return keyDown(currentWindowHandle(), key);
    }

    /**
     * Delivers a knockback exactly as vanilla's own damage pipeline would.
     *
     * <p>One call on every target this project builds against up to and
     * including 1.21.11: {@code LivingEntity.knockback(double, double, double)}.
     * Behind Compat rather than called directly because the 26.x line replaced
     * it, and the two spellings have to stay one call site - see the 26.x copies
     * of this file.
     */
    public static void knockback(LivingEntity victim, double strength, double x, double z) {
        victim.knockback(strength, x, z);
    }

    public static void swing(LivingEntity entity, InteractionHand hand) {
        entity.swing(hand, true);
    }

    /**
     * Starts a swing on a morph-preview mob without touching world state.
     *
     * <p>Preview-only: the world puppet path ({@code mirrorSwing}) keeps using
     * {@link #swing}, which broadcasts to viewers on a server level. The
     * preview mob is never added to a level and always lives on the client
     * {@code ClientLevel}, where vanilla {@code LivingEntity.swing} takes no
     * broadcast branch (verified: the packet send is gated on
     * {@code level() instanceof ServerLevel}) and fires no game event - the
     * call only writes the entity's own {@code swinging}/{@code swingTime}/
     * {@code swingingArm} fields. Passing {@code false} (updateSelf) selects
     * the tracker-only broadcast over broadcast-and-send, so even a future
     * level swap could not echo the swing back to a self player.
     */
    public static void previewSwing(LivingEntity entity, InteractionHand hand) {
        entity.swing(hand, false);
    }

    public static boolean isSwinging(LivingEntity entity) {
        return SwingAccess.isSwinging(entity);
    }

    /**
     * Whether a player is holding jump.
     *
     * <p>{@code LivingEntity.jumping} is protected, so neither the recorder nor
     * the simulation can read it off an arbitrary player. This is reached
     * through the {@code SwingAccess} duck interface, which the project's
     * {@code MixinLivingEntity} already implements, rather than adding a second
     * accessor interface and a second mixin for one boolean.
     */
    public static boolean jumpHeld(LivingEntity entity) {
        return JumpAccess.jumpHeld(entity);
    }
    /**
     * Which hotbar slot the player has selected.
     *
     * <p>1.21.1 through 1.21.8 expose this as a public field, but 1.21.10
     * made it private behind a getter, so the read has to be version specific
     * rather than picked by a cast.
     */
    public static int selectedSlot(Player player) {
        return player.getInventory().selected;
    }
    /**
     * Restores the fall distance an entity had recorded.
     *
     * <p>The field was a {@code float} on 1.21.1 and 1.21.4 and became a
     * {@code double} in 1.21.5, so the one write has to mean two different
     * things on either side of that line. Vanilla offers no setter to absorb
     * the difference either: {@code resetFallDistance} only ever zeroes the
     * field, so putting a recorded value back means writing the field itself.
     * The parameter stays a {@code float} on all nine targets because it
     * widens to {@code double} implicitly, which keeps the caller clear of a
     * cast even though the field behind it changed type.
     *
     * <p>The number is not really a distance in metres. What is being restored
     * is a "was descending" flag the recorder sampled off an entity and now has
     * to put back, so the actor either keeps falling or stops falling on the
     * frame the performer was actually on. Restoring a real fall distance here
     * would be the wrong shape of code for a value nobody ever uses as one.
     */
    public static void setFallDistance(Entity entity, float distance) {
        entity.fallDistance = distance;
    }
    /**
     * Restores the hotbar slot a performer had selected.
     *
     * <p>Playback has to put the actor's selection back on the frame the
     * performer changed it, or every clone keeps whatever slot it was handed at
     * spawn time and a punch that was thrown with an item in hand lands empty on
     * replay. {@code selectedSlot} is the read half of this and is split the
     * same way: {@code Inventory.selected} is a public field on 1.21.1 and
     * 1.21.4, and has been private since 1.21.5, where the write has to go
     * through the {@code setSelectedSlot} accessor vanilla published alongside
     * the getter that replaced the field.
     */
    public static void setSelectedSlot(Player player, int slot) {
        player.getInventory().selected = slot;
    }
    /**
     * Zeros an entity's invulnerability i-frames.
     *
     * <p>An actor restored while its i-frame window is still running swallows
     * the punch it was supposed to block: the arm swings, no damage number
     * appears, and the recording reads as a desync rather than as the frame that
     * was deliberately dropped. Clearing the window is what lets a replay show
     * the hit the performer actually threw instead of whichever hit a teleport
     * boundary happened to absorb.
     *
     * <p>Vanilla gives mod code no way to shorten the window, and the field is
     * not reachable the same way on every target either:
     * {@code Entity.invulnerableTime} is public on eight of the nine and went
     * private in 26.3. So the write goes through the {@code InvulnerabilityAccess}
     * duck interface, which the project's {@code MixinEntity} already implements
     * on all nine, rather than a direct field write that would compile on eight
     * of them and break the ninth.
     */
    public static void clearInvulnerability(Entity entity) {
        ((InvulnerabilityAccess) entity).fbactors$setInvulnerableTime(0);
    }
    /**
     * Plays a critical-hit animation on an entity.
     *
     * <p>{@code Player.crit} is empty on the server, because the real client
     * draws the particles when its own swing produces the packet. A clone has
     * no client, so a critical strike against one would deal the bonus damage
     * with nothing on screen to show for it. {@code Player.attack} calls
     * {@code crit} and {@code magicCrit}, so this is the only place the effect
     * can be put back.
     */
    public static void critAnimation(Entity target, boolean magic) {
        if (target == null || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        level.getChunkSource().broadcastAndSend(target,
            new ClientboundAnimatePacket(target, magic ? 5 : 4));
    }

    /**
     * Pushes the entity's current velocity to tracking viewers.
     *
     * <p>Vanilla raises a sync flag on every knockback (via {@code markHurt}),
     * so the tracker sends a motion packet with the shove instead of letting
     * the client interpolate across its background cadence. A replay applies
     * its shove outside vanilla's knockback call, so without this the slide
     * interpolates over multi-tick gaps and reads as steppy. Goes through the
     * same broadcast as {@link #critAnimation}.
     */
    public static void syncMotion(Entity target) {
        if (target == null || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        level.getChunkSource().broadcastAndSend(target,
            new ClientboundSetEntityMotionPacket(target.getId(), target.getDeltaMovement()));
    }

    public static void setJumpHeld(LivingEntity entity, boolean jumping) {
        JumpAccess.setJumpHeld(entity, jumping);
    }
    public static void tickSwing(LivingEntity entity) {
        SwingAccess.tickSwing(entity);
    }

    /**
     * Writes a recorded limb stride onto an entity, from the take rather than the tick history.
     *
     * <p>{@code Entity.walkDist} accumulates {@code horizontalDistance * 0.6} inside
     * {@code Entity.move} on every move, and {@code Entity.baseTick} copies it into
     * {@code walkDistO} at the start of the next tick. Both are live simulation state:
     * the viewport and the export reach the same take tick through different tick
     * histories (scrubs, pre-roll, respawns), so the accumulated values disagree while
     * the snapped position agrees exactly. Writing the take's own prefix sums here -
     * after the tick ran and after the baseline snapped the position back - makes the
     * stride a pure function of the take tick on both paths.
     *
     * <p>No-op on this target: {@code Entity.walkDist} existed only on 1.21.1
     * (verified against the loom mappings jars - present on 1.21.1, absent
     * from 1.21.4 on) and was removed upstream afterwards. The limb pose the
     * renderer reads comes from {@code walkAnimation} on every target, which
     * {@link #setWalkAnimation} drives from the same recorded prefix sums, so
     * viewport and export agree without this write. Kept as a method rather
     * than deleted so the playback phases stay version-blind: the 1.21.1 copy
     * of this shim performs the direct write, and no call site branches on
     * the version.
     *
     * @param entity the clone, untouched on this target
     * @param walkDist the cumulative recorded stride for this tick, ignored here
     * @param walkDistO the cumulative recorded stride for the previous tick, ignored here
     */
    public static void setWalkDist(LivingEntity entity, float walkDist, float walkDistO) {
    }

    /**
     * Writes a recorded walk-animation state onto an entity, from the take rather than
     * the tick history.
     *
     * <p>The player renderer does not read {@code walkDist} for the limbs. It reads
     * {@code LivingEntity.walkAnimation}: {@code LivingEntityRenderer} feeds
     * {@code walkAnimation.position} (the phase) and {@code walkAnimation.speed} (the
     * amount) into the model, and {@code HumanoidModel.setupAnim} swings every leg
     * and arm off {@code cos(phase * 0.6662) * amount}. Vanilla drives that state
     * from the displacement the entity's own tick measured
     * ({@code LivingEntity.calculateEntityAnimation} into
     * {@code updateWalkAnimation}, {@code min(step * 4, 1)} smoothed by
     * {@code WalkAnimationState.update(target, 0.4)}), which is the same
     * history-dependent accumulation as {@code walkDist}: same frame, different
     * phase, legs split wide on one path and mid-stride together on the other.
     *
     * <p>The state object itself is public under the same name on every target, but
     * its speed and position fields are private with a version-varying shape (three
     * floats before 1.21.4, four after), so this delegates to
     * {@code WalkDriver.driveTo}, which resolves them reflectively once and then
     * <em>sets</em> the recorded values rather than integrating toward them. Setting
     * rather than advancing is the whole fix: the same tick always yields the same
     * phase no matter which ticks ran before it.
     *
     * @param entity the clone whose walk state is overwritten with recorded values
     * @param speedOld the recorded smoothed speed of the previous tick
     * @param speed the recorded smoothed speed of this tick
     * @param position the recorded integrated phase of this tick
     */
    public static void setWalkAnimation(LivingEntity entity, float speedOld, float speed,
                                        float position) {
        pdx.dev.fbactors.morph.WalkDriver.driveTo(entity, speedOld, speed, position);
    }

    public static void closeScreen() {
        Minecraft.getInstance().setScreen(null);
    }

    public static Button spriteButton(int x, int y, int size, String tooltip, Runnable onPress) {
        return new FlashbackButton(x, y, size, size,
            Component.literal(tooltip),
            b -> onPress.run(),
            ResourceLocation.fromNamespaceAndPath("fbactors", "textures/gui/timeline.png"))
            .flashbackWithTooltip();
    }
    public static void openScreen(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }


    /**
     * Reads the slot number from a container click packet.
     *
     * <p>The accessor was renamed across the targets - {@code getSlotNum()} on
     * 1.21.1-1.21.4 and {@code slotNum()} from 1.21.5 - so the mixin calls this
     * instead of the packet directly and each version's implementation uses the
     * name its target ships.
     */
    public static int containerSlot(net.minecraft.network.protocol.game.ServerboundContainerClickPacket packet) {
        return packet.getSlotNum();
    }

    public static void setCamera(Player player, Entity camera) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.setCamera(camera);
        }
    }

    /**
     * Puppet ids, handed out downward from a block no real entity can
     * reach.
     *
     * <p>A puppet is added to the client level like any tracked entity, so
     * its id must not collide with one vanilla hands out - and the mob's own
     * renderer reads it during extraction (a wolf does, which is how this
     * was found). Render thread only, so a plain counter is enough.
     */
    private static int nextPuppetId = -979_000_000;

    /**
     * The client-side mob bodies, one per morphed actor.
     *
     * <p>An earlier revision intercepted state creation and returned the
     * mob's state in place of the player's. That made every frame depend on
     * which code path created the state: any path that built it another way
     * drew the player (or nothing) for that frame. A puppet is a real entity
     * in the client level, so it renders through the ordinary pipeline with
     * the right type, hitbox, shadow, name tag and culling, and no
     * interception point exists to miss - there is nothing left to swap.
     *
     * <p>Confined to the client tick and the render loop, which run on the
     * same thread, so a plain map is enough. The map is still a
     * {@code ConcurrentHashMap} because a stray call from anywhere else must
     * fail safe rather than corrupt iteration, not because concurrency is
     * expected.
     */
    private static final Map<UUID, Puppet> PUPPETS = new ConcurrentHashMap<>();

    /**
     * Actor bodies seen by the render veto, by UUID.
     *
     * <p>The client level offers no UUID lookup, so the veto - which sees
     * every morphed actor every frame - records the object for the tick sync
     * to follow. Entries are validated on every use (level, removal, UUID),
     * so a stale object can only ever freeze a puppet, never steer one.
     */
    private static final Map<UUID, Entity> SEEN = new ConcurrentHashMap<>();

    /** Consecutive ticks each morphed actor went unseen. */
    private static final Map<UUID, Integer> UNSEEN_TICKS = new ConcurrentHashMap<>();

    /** The level puppets currently live in, for change detection. */
    @Nullable
    private static ClientLevel puppetLevel;

    /** A client-side mob body and the morph id it was built for. */
    private record Puppet(Entity entity, String morphId) {
    }

    /**
     * Records a client actor body for the puppet sync.
     *
     * <p>Called from the render veto, which is the one place that sees every
     * morphed actor every frame. Invalid sightings are refused outright, so
     * the map only ever holds bodies usable as-is.
     *
     * @param actor the client actor entity; null and unusable bodies are ignored
     */
    public static void noteActor(@Nullable Entity actor) {
        if (actor == null || actor.getUUID() == null) {
            return;
        }
        if (actor.isRemoved() || !(actor.level() instanceof ClientLevel)) {
            return;
        }
        SEEN.put(actor.getUUID(), actor);
    }

    /**
     * Syncs every puppet to its actor. Runs once per client tick.
     *
     * <p>Puppets whose morph entry is gone are removed: an un-morphed actor
     * has no body to draw. Everything else is refreshed in
     * {@link #syncOne}, which never throws - a failure there must leave last
     * frame's puppet standing, not take the tick down with it.
     */
    public static void syncPuppets() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level)) {
            dropAllPuppets();
            return;
        }
        if (puppetLevel != null && puppetLevel != level) {
            dropAllPuppets();
        }
        puppetLevel = level;
        Map<UUID, MorphRenderInfo> entries;
        try {
            entries = MorphRenderState.snapshot();
        } catch (Exception e) {
            return;
        }
        for (UUID id : new ArrayList<>(PUPPETS.keySet())) {
            if (!entries.containsKey(id)) {
                dropPuppet(id);
            }
        }
        for (Map.Entry<UUID, MorphRenderInfo> entry : entries.entrySet()) {
            try {
                syncOne(level, entry.getKey(), entry.getValue());
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Drops one puppet, removing it from the level first.
     *
     * @param id the actor's UUID
     */
    private static void dropPuppet(UUID id) {
        Puppet puppet = PUPPETS.remove(id);
        UNSEEN_TICKS.remove(id);
        LAST_STRIDE_POS.remove(id);
        STRIDES.remove(id);
        SEEN.remove(id);
        if (puppet == null || puppet.entity() == null) {
            return;
        }
        pdx.dev.fbactors.filters.ActorLooks.unalias(puppet.entity().getUUID());
        try {
            if (puppet.entity().level() instanceof ClientLevel at) {
                at.removeEntity(puppet.entity().getId(), Entity.RemovalReason.DISCARDED);
            }
        } catch (Exception ignored) {
        }
    }

    /** Drops every puppet, for level changes and disconnects. */
    private static void dropAllPuppets() {
        for (UUID id : new ArrayList<>(PUPPETS.keySet())) {
            dropPuppet(id);
        }
        SEEN.clear();
        UNSEEN_TICKS.clear();
        LAST_STRIDE_POS.clear();
        STRIDES.clear();
        puppetLevel = null;
    }

    /**
     * Syncs one actor's puppet for one tick.
     *
     * <p>A fully transparent morph draws nothing, and an unknown morph id
     * draws nothing either: both drop the puppet and leave the vetoed player
     * hidden, which is an invisible actor rather than a wrong one. An actor
     * object that turns over on respawn leaves the puppet frozen for up to
     * half a second rather than blinking it: the old body is already gone
     * while the new one has not rendered yet, and a frozen frame beats a
     * missing one.
     */
    private static void syncOne(ClientLevel level, UUID id, MorphRenderInfo info) {
        if (info.alpha() <= 0.0f) {
            dropPuppet(id);
            return;
        }
        EntityType<?> type = resolveType(info.morphId());
        if (type == null) {
            dropPuppet(id);
            return;
        }
        Entity actor = SEEN.get(id);
        if (actor == null || !id.equals(actor.getUUID()) || actor.level() != level
            || actor.isRemoved()) {
            int missed = UNSEEN_TICKS.getOrDefault(id, 0) + 1;
            UNSEEN_TICKS.put(id, missed);
            if (missed > 10) {
                dropPuppet(id);
            }
            return;
        }
        UNSEEN_TICKS.remove(id);
        Puppet puppet = PUPPETS.get(id);
        if (puppet == null || puppet.entity() == null
            || !info.morphId().equals(puppet.morphId())
            || puppet.entity().getType() != type
            || puppet.entity().level() != level
            || puppet.entity().isRemoved()) {
            dropPuppet(id);
            Entity fresh;
            try {
                fresh = type.create(level, EntitySpawnReason.COMMAND);
            } catch (Exception e) {
                return;
            }
            if (fresh == null) {
                return;
            }
            fresh.setId(nextPuppetId--);
            pdx.dev.fbactors.filters.ActorLooks.alias(fresh.getUUID(), id);
            fresh.setNoGravity(true);
            fresh.noPhysics = true;
            fresh.setSilent(true);
            try {
                level.addEntity(fresh);
            } catch (Exception e) {
                return;
            }
            PUPPETS.put(id, new Puppet(fresh, info.morphId()));
            // Seeds the stride trail at the actor so the first sync after a
            // (re)spawn measures zero, not the whole gap since the drop.
            STRIDES.remove(id);
            LAST_STRIDE_POS.put(id, new double[]{actor.getX(), actor.getZ(),
                actor.getYRot(), actor.getXRot(), actor.getY()});
            puppet = PUPPETS.get(id);
            if (puppet == null) {
                return;
            }
        }
        syncBody(id, puppet.entity(), actor, info);
    }

    /**
     * The entity type for a morph id, or null.
     *
     * <p>Morph ids are bare ({@code "chicken"}, not
     * {@code "minecraft:chicken"}), so a missing namespace defaults to
     * {@code minecraft}. The entity registry is defaulted and never returns
     * null, so a miss is detected by comparing the looked-up key instead of a
     * null check. A miss drops the puppet, and the veto keeps the player
     * hidden, which is an invisible actor rather a pig where a chicken should
     * be.
     *
     * <p>In 26.2+, {@code EntityType} was renamed {@code EntityTypes} for the
     * holder class; the generic type itself is still {@code EntityType}.
     */
    public static net.minecraft.world.entity.LivingEntity createMorphPreview(String id) {
        var client = Minecraft.getInstance();
        if (client.level == null) return null;
        var type = resolveType(id);
        if (type == null) return null;
        var entity = type.create(client.level, EntitySpawnReason.COMMAND);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return null;
        // Fresh random identity: type.create already assigns one, but the preview
        // mob shares the world ClientLevel object, so its UUID must never collide
        // with a world actor's. Re-rolling here guarantees disjoint identity even
        // if a future entity factory reuses ids. Field-only write, never added.
        living.setUUID(UUID.randomUUID());
        living.setNoGravity(true);
        living.setSilent(true);
        if (living instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
        return living;
    }

    @Nullable
    private static EntityType<?> resolveType(String morphId) {
        String raw = MorphRegistry.entityIdFor(morphId);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id;
        try {
            id = raw.indexOf(':') >= 0
                ? ResourceLocation.tryParse(raw)
                : ResourceLocation.tryParse("minecraft:" + raw);
        } catch (Exception e) {
            return null;
        }
        if (id == null) {
            return null;
        }
        EntityType<?> type;
        try {
            type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        } catch (Exception e) {
            return null;
        }
        if (type == null || !EntityType.getKey(type).equals(id)) {
            return null;
        }
        return type;
    }

    /** The equipment slots a mob body can render. */
    private static final EquipmentSlot[] PUPPET_SLOTS = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    /**
     * Copies the actor's visible state onto its puppet.
     *
     * <p>Deliberately copies only what the renderer reads: position and
     * orientation, pose, flags, name, visibility, health and vitals,
     * stride, equipment and scale. Everything vanilla derives - walk animation from
     * the position trail its own tick leaves behind, body yaw from travel,
     * shadows from the box - is left for vanilla to derive, which is exactly
     * how a real mob animates.
     */
    private static void syncBody(UUID id, Entity puppet, Entity actor, MorphRenderInfo info) {
        // Hardens the render lerp chain: xOld must be the pre-teleport pos so the
        // renderer glides old -> new within the frame instead of strobing.
        puppet.setOldPosAndRot();
        puppet.setPos(actor.getX(), actor.getY(), actor.getZ());
        puppet.setYRot(actor.getYRot());
        puppet.setXRot(actor.getXRot());
        puppet.setPose(actor.getPose());
        puppet.setOnGround(actor.onGround());
        puppet.setShiftKeyDown(actor.isShiftKeyDown());
        puppet.setSprinting(actor.isSprinting());
        puppet.setSwimming(actor.isSwimming());
        puppet.setInvisible(actor.isInvisible());
        if (actor instanceof LivingEntity living && puppet instanceof LivingEntity mob) {
            // Head and torso follow the actor's live yaw, never its head fields: a
            // replay client only receives position packets (which carry yaw), never
            // head-look packets, so the actor's own yHeadRot/yBodyRot freeze at
            // spawn - copying them locked the head and kept the torso from turning.
            // Previous-tick values come from last sync so turns interpolate.
            float yaw = actor.getYRot();
            float pitch = actor.getXRot();
            float lastYaw = yaw;
            float lastPitch = pitch;
            double[] lastPose = LAST_STRIDE_POS.get(id);
            if (lastPose != null && lastPose.length >= 4) {
                lastYaw = (float) lastPose[2];
                lastPitch = (float) lastPose[3];
                // Unwrap across the +-180 seam so one packet yaw jump does not
                // spin the long way for a tick.
                lastYaw = yaw + net.minecraft.util.Mth.wrapDegrees(lastYaw - yaw);
                lastPitch = pitch + net.minecraft.util.Mth.wrapDegrees(lastPitch - pitch);
            }
            // Ease the torso toward the heading like vanilla tickHeadTurn (*0.3)
            // instead of snapping: the body drags through turns while the head
            // stays live. Snaps when the gap is huge (spawn/teleport), eases
            // otherwise, so sharp turns lean instead of popping.
            float curBody = mob.yBodyRot;
            float bodyDelta = net.minecraft.util.Mth.wrapDegrees(yaw - curBody);
            float easedBody = Math.abs(bodyDelta) > 90.0F ? yaw : curBody + bodyDelta * 0.3F;
            mob.setYHeadRot(yaw);
            mob.yHeadRotO = lastYaw;
            mob.yBodyRot = easedBody;
            mob.yBodyRotO = curBody;
            mob.yRotO = lastYaw;
            mob.xRotO = lastPitch;
            mirrorSwing(living, mob);
            driveWalkAnimation(id, actor, mob, yaw, pitch);
            // Fractional, not absolute: the actor's 20hp on a 100hp golem reads as
            // near-death and shows full crack stages. Scale by max health so the
            // puppet shows the actor's condition, not its hit points.
            try {
                mob.setHealth(living.getHealth() / living.getMaxHealth() * mob.getMaxHealth());
            } catch (Exception ignored) {
            }
            try {
                mob.setAbsorptionAmount(living.getAbsorptionAmount());
            } catch (Exception ignored) {
            }
            copyHurtState(living, mob);
            // No nametag on puppets, ever: the applier writes the morph's display
            // name onto the clone, and copying it here forced a floating label
            // onto every morph. A morphed actor is anonymous by default; the
            // vetoed player body underneath renders nothing, so nothing shows.
            applyDrawScale(mob, info);
            if (puppet.tickCount % 10 == 0) {
                syncEquipment(mob, living, info);
            }
        }
    }

    /** Last synced actor state per puppet: x, z, yaw, pitch, y. Stride and turn interpolation read it. */
    /** Smoothed per-tick displacement per puppet, for buzz-free stride. */
    private static final Map<UUID, pdx.dev.fbactors.morph.MorphStride> STRIDES = new ConcurrentHashMap<>();
    private static final Map<UUID, double[]> LAST_STRIDE_POS = new ConcurrentHashMap<>();

    /**
     * Drives the puppet's stride from its actor's measured displacement.
     *
     * <p>Vanilla advances {@code walkAnimation} from the displacement the entity's
     * own tick measures, but a puppet is teleported by this sync outside its tick -
     * so its own tick always measures zero and the limbs never leave rest pose.
     * Measuring here, across syncs, with the same formula vanilla uses
     * ({@code min(dist * 4, 1)}, smoothed by {@code update} itself) makes the puppet
     * walk exactly as far as its actor does, whether or not either of them ticks
     * normally. The {@code update} shape is identical on every target, so this is a
     * direct call rather than reflection.
     */
        /**
     * Mirrors the actor's arm swing onto the puppet.
     *
     * <p>The clone's swing broadcasts to viewers, but the broadcast animates the
     * vetoed player body, not the puppet - so a morphed hit never shows an arm.
     * The client actor's swing state is live (it is what unmorphed actors render),
     * so when it swings and the puppet does not, the puppet starts the same
     * main-hand swing. Checked every tick rather than on edges, so a missed start
     * heals next tick instead of dropping the hit; the start-if-able gate inside
     * swing() makes the re-check free while a swing is already playing.
     */
    private static void mirrorSwing(LivingEntity actor, LivingEntity mob) {
        try {
            if (SwingAccess.isSwinging(actor) && !SwingAccess.isSwinging(mob)) {
                swing(mob, InteractionHand.MAIN_HAND);
            }
        } catch (Exception ignored) {
        }
    }


private static void driveWalkAnimation(UUID id, Entity actor, LivingEntity mob,
            float yaw, float pitch) {
        var stride = STRIDES.computeIfAbsent(id, key -> new pdx.dev.fbactors.morph.MorphStride());
        var frame = stride.sample(actor.getX(), actor.getY(), actor.getZ(), !actor.onGround());
        LAST_STRIDE_POS.put(id, new double[]{actor.getX(), actor.getZ(), yaw, pitch, actor.getY()});
        pdx.dev.fbactors.morph.WalkDriver.driveTo(mob, frame.previousSpeed(), frame.speed(), frame.position());
    }
    /**
     * Sizes the puppet so its drawn height matches the catalogue's player-relative
     * scale.
     *
     * <p>{@code info.scale()} is player-relative (a zombie at 1.0 draws at player
     * height), but the SCALE attribute multiplies the mob's <em>natural</em> size,
     * so the attribute needs the ratio, not the catalogue value: a spider authored
     * at 0.5 is a natural-size spider, not a half-size one. Writing the catalogue
     * value straight through is what made small mobs microscopic and large ones
     * gigantic. Dimensions are refreshed so the shadow and culling box follow the
     * model rather than trailing a tick behind it.
     */
    private static void applyDrawScale(LivingEntity mob, MorphRenderInfo info) {
        float want = info.scale();
        try {
            MorphDefinition def = Morph.registry().byId(info.morphId());
            if (def != null && def.hitboxHeight() > 0.0F) {
                want = info.scale() * 1.8F / def.hitboxHeight();
            }
        } catch (Exception ignored) {
        }
        try {
            net.minecraft.world.entity.ai.attributes.AttributeInstance scale =
                mob.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
            if (scale != null && scale.getBaseValue() != want) {
                scale.setBaseValue(want);
                try {
                    mob.refreshDimensions();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Mirrors what a viewer should see the mob carrying.
     *
     * <p>A quadruped's silhouette must not carry the take's equipment: the
     * player renderer draws every slot it has, and a spider holding a sword
     * with a leg is the single most recognisable sign of a morph applied
     * badly. A humanoid keeps the actor's own gear, copied across so the
     * zombie villager keeps its shovel.
     */
    private static void syncEquipment(LivingEntity mob, LivingEntity actor,
            MorphRenderInfo info) {
        MorphDefinition def = Morph.registry().byId(info.morphId());
        boolean clear = def != null && def.quadruped();
        for (EquipmentSlot slot : PUPPET_SLOTS) {
            try {
                if (clear) {
                    if (!mob.getItemBySlot(slot).isEmpty()) {
                        mob.setItemSlot(slot, ItemStack.EMPTY);
                    }
                } else {
                    ItemStack want = actor.getItemBySlot(slot);
                    mob.setItemSlot(slot, want.isEmpty() ? ItemStack.EMPTY : want.copy());
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Carries the hurt tilt and death fall-over across, reflectively.
     *
     * <p>Field names as strings so this compiles on every target even where
     * the fields moved: 26.3 reworked swing state, and a direct reference
     * would compile on eight targets and break the ninth.
     */
    private static void copyHurtState(LivingEntity from, LivingEntity to) {
        copyLivingField(from, to, "hurtTime");
        copyLivingField(from, to, "deathTime");
    }

    private static void copyLivingField(LivingEntity from, LivingEntity to, String name) {
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(to, field.get(from));
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException ignored) {
        }
    }

    public static net.minecraft.core.BlockPos useOnPos(net.minecraft.network.protocol.game.ServerboundUseItemOnPacket packet) { return packet.getHitResult().getBlockPos(); }
    public static boolean useOnOffHand(net.minecraft.network.protocol.game.ServerboundUseItemOnPacket packet) { return packet.getHand() == net.minecraft.world.InteractionHand.OFF_HAND; }
    public static boolean useItemOffHand(net.minecraft.network.protocol.game.ServerboundUseItemPacket packet) { return packet.getHand() == net.minecraft.world.InteractionHand.OFF_HAND; }
    public static java.util.UUID interactTarget(net.minecraft.network.protocol.game.ServerboundInteractPacket packet, net.minecraft.server.level.ServerLevel level) { try { net.minecraft.world.entity.Entity e = packet.getTarget(level); return e == null ? null : e.getUUID(); } catch (Exception ex) { return null; } }
    public static boolean interactOffHand(net.minecraft.network.protocol.game.ServerboundInteractPacket packet) { final boolean[] off = new boolean[1]; try { packet.dispatch(new net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler() { public void onInteraction(net.minecraft.world.InteractionHand hand) { off[0] = hand == net.minecraft.world.InteractionHand.OFF_HAND; } public void onInteraction(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 pos) { off[0] = hand == net.minecraft.world.InteractionHand.OFF_HAND; } public void onAttack() { } }); } catch (Exception ex) { } return off[0]; }
    public static boolean interactIsAttack(net.minecraft.network.protocol.game.ServerboundInteractPacket packet) { final boolean[] atk = new boolean[1]; try { packet.dispatch(new net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler() { public void onInteraction(net.minecraft.world.InteractionHand hand) { } public void onInteraction(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 pos) { } public void onAttack() { atk[0] = true; } }); } catch (Exception ex) { return false; } return atk[0]; }
    public static String entityTypeId(net.minecraft.world.entity.Entity entity) { try { var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()); return key == null ? null : key.toString(); } catch (Exception ex) { return null; } }
    public static String entityNbt(net.minecraft.world.entity.Entity entity) { return ""; }
    public static net.minecraft.world.entity.Entity spawnEntityFromRecord(net.minecraft.server.level.ServerLevel level, pdx.dev.fbactors.take.ActorSpawn spawn) { if (spawn == null || spawn.entityId() == null) return null; try { net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(spawn.entityId()); if (id == null) return null; net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(id); if (type == null) return null; net.minecraft.world.entity.Entity e = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND); if (e == null) return null; e.moveTo(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()); pdx.dev.fbactors.actor.BlockEdits.beginApply(); try { level.addFreshEntity(e); } finally { pdx.dev.fbactors.actor.BlockEdits.endApply(); } return e; } catch (Exception ex) { return null; } }

}
