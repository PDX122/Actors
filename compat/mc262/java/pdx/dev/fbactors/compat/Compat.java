package pdx.dev.fbactors.compat;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.moulberry.flashback.FilePlayerSkin;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelFutureListener;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.PlayerSkin;
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
import pdx.dev.fbactors.morph.MorphRenderInfo;
import pdx.dev.fbactors.morph.MorphRenderState;
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

    private static Category actorsCategory;

    public static long headTexture(UUID id) {
        Identifier texture = skinTexture(id);
        if (texture == null) {
            return 0L;
        }
        AbstractTexture abstractTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (abstractTexture == null) {
            return 0L;
        }
        GpuTextureView view = abstractTexture.getTextureView();
        if (view == null) {
            return 0L;
        }
        return ReplayUI.imguiRenderer.getTextureId(view);
    }

    public static long cameraPreviewTexture(String id) {
        Identifier texture = boatTextureId(id);
        if (texture == null) {
            return 0L;
        }
        AbstractTexture abstractTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (abstractTexture == null) {
            return 0L;
        }
        GpuTextureView view = abstractTexture.getTextureView();
        if (view == null) {
            return 0L;
        }
        return ReplayUI.imguiRenderer.getTextureId(view);
    }

    public static boolean cameraPreviewSlim(UUID id) {
        var client=Minecraft.getInstance();
        var player=client.level==null?null:client.level.getPlayerByUUID(id);
        var downloaded=SKIN_PREVIEW_APPEARANCES.get(id);
        var skin=downloaded!=null?downloaded:player instanceof AbstractClientPlayer actual?actual.getSkin():DefaultPlayerSkin.get(id);
        return skin.model().toString().equalsIgnoreCase("slim");
    }

    public static void sampleNearest(long handle) {
        ReplayUI.imguiRenderer.setSampleNearest(handle);
    }

    @Nullable
    private static Identifier skinTexture(UUID id) {

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            FilePlayerSkin fromFile = editorState.skinOverrideFromFile.get(id);
            if (fromFile != null) {
                return fromFile.getSkin().body().texturePath();
            }

        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            Player player = level.getPlayerByUUID(id);
            if (player instanceof AbstractClientPlayer clientPlayer) {
                return clientPlayer.getSkin().body().texturePath();
            }
        }
            GameProfile profile = editorState.skinOverride.get(id);
            if (profile != null) {
                PlayerInfo info = OVERRIDE_PROFILES.get(id);
                if (info == null || info.getProfile() != profile) {
                    info = new PlayerInfo(profile, false);
                    OVERRIDE_PROFILES.put(id, info);
                }
                return info.getSkin().body().texturePath();
            }
        }

        Object head = pdx.dev.fbactors.skin.SkinTextures.headLocation(id);
        if (head != null) {
            return (Identifier) head;
        }
        return DefaultPlayerSkin.get(id).body().texturePath();
    }

    public static FilePlayerSkin fileSkinWithCape(String skinPath, @Nullable String capePath) {
        return fileSkinWithLook(skinPath, capePath, null);
    }

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

                        if (slim == null) {
                            return body;
                        }
                        PlayerSkin modelled = PlayerSkin.insecure(body.body(), body.cape(),
                            body.elytra(), wantedModel(body, slim));
                        withCape = modelled;
                        return modelled;
                    }
                    Identifier id = Identifier.fromNamespaceAndPath("fbactors",
                        "cape/" + sha1Hex(capePath));
                    Minecraft.getInstance().getTextureManager().release(id);
                    Minecraft.getInstance().getTextureManager().register(id,
                        new net.minecraft.client.renderer.texture.DynamicTexture(
                            () -> "fbactors-cape", image));

                    done = PlayerSkin.insecure(body.body(),
                        new net.minecraft.core.ClientAsset.ResourceTexture(id, id),
                        body.elytra(), wantedModel(body, slim));
                    withCape = done;
                    return done;
                } catch (Exception e) {
                    LOGGER.warn("Could not load cape from {}", capePath, e);
                    return body;
                }
            }
        };
    }

    private static net.minecraft.world.entity.player.PlayerModelType wantedModel(
        PlayerSkin body, @Nullable Boolean slim) {
        if (slim == null) {
            return body.model();
        }
        return slim ? net.minecraft.world.entity.player.PlayerModelType.SLIM
            : net.minecraft.world.entity.player.PlayerModelType.WIDE;
    }

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
        Identifier texId = Identifier.fromNamespaceAndPath("fbactors",
            "cape/" + sha1Hex(capePath));
        Minecraft.getInstance().getTextureManager().release(texId);
        Minecraft.getInstance().getTextureManager().register(texId,
            new net.minecraft.client.renderer.texture.DynamicTexture(
                () -> "fbactors-cape", image));
        PlayerSkin caped = PlayerSkin.insecure(plain.body(),
            new net.minecraft.core.ClientAsset.ResourceTexture(texId, texId),
            plain.elytra(), plain.model());
        return new FilePlayerSkin("__cape_only__") {
            @Override
            public PlayerSkin getSkin() {
                return caped;
            }
        };
    }

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
        if (actorsCategory == null) {
            actorsCategory = Category.register(Identifier.fromNamespaceAndPath(modId, "actors"));
        }
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(translationKey, Type.KEYSYM,
            InputConstants.getKey(keyName).getValue(), actorsCategory));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return ClientCommands.literal(name);
    }

    public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(
            String name, ArgumentType<T> type) {
        return ClientCommands.argument(name, type);
    }

    public static EntityType<?> playerEntityType() {
        return EntityTypes.PLAYER;
    }

    public static String profileName(GameProfile profile) {
        return profile.name();
    }

    public static UUID profileId(GameProfile profile) {
        return profile.id();
    }

    public static boolean profileHasTextures(GameProfile profile) {
        if (profile == null) {
            return false;
        }
        java.util.Collection<?> textures = profile.properties().get("textures");
        return textures != null && !textures.isEmpty();
    }

    @Nullable

    public static java.util.concurrent.CompletableFuture<String> openSkinFile(
            String defaultDir) {
        return com.moulberry.flashback.exporting.AsyncFileDialogs
            .openFileDialog(defaultDir, "Skin PNG", "png");
    }

    public static GameProfile fetchTexturedProfile(UUID id) {
        try {
            var result = Minecraft.getInstance().services().sessionService()
                .fetchProfile(id, false);
            GameProfile fetched = result == null ? null : result.profile();
            return fetched != null && profileHasTextures(fetched) ? fetched : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static final java.util.Map<UUID, net.minecraft.world.entity.player.PlayerSkin> SKIN_PREVIEW_APPEARANCES = new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    public static Object loadHead(GameProfile profile) {
        try {
            java.util.Optional<net.minecraft.world.entity.player.PlayerSkin> skin =
                Minecraft.getInstance().getSkinManager().get(profile)
                    .get(20, java.util.concurrent.TimeUnit.SECONDS);
            skin.ifPresent(value -> SKIN_PREVIEW_APPEARANCES.put(profileId(profile), value));
            return skin.map(value -> value.body().texturePath()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static Entity entityByUuid(ServerLevel level, UUID id) {
        return level.getEntityInAnyDimension(id);
    }

    public static void snapTo(Entity entity, double x, double y, double z, float yaw, float pitch) {
        entity.snapTo(x, y, z, yaw, pitch);
    }

    public static void cancelInterpolation(Entity entity) {
        InterpolationHandler interpolation = entity.getInterpolation();
        if (interpolation != null) {
            interpolation.cancel();
        }
    }

    public static Connection silentConnection() {
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
            }
        };
        new EmbeddedChannel(connection);
        return connection;
    }

    public static ResourceKey<Level> dimensionKey(String dimension) {
        Identifier id = Identifier.tryParse(dimension == null ? "minecraft:overworld" : dimension);
        if (id == null) {
            id = Identifier.parse("minecraft:overworld");
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public static String dimensionId(@Nullable ServerLevel level) {
        return level == null ? "minecraft:overworld" : level.dimension().identifier().toString();
    }

    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    public static ItemStack itemStack(String itemId, int count) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(itemId);
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
        var tag = ItemStack.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, stack).result().orElse(null);
        return tag == null ? "" : tag.toString();
    }

    public static ItemStack itemStackFromNbt(Level level, String itemId, int count, String nbt) {
        ItemStack stack = itemStack(itemId, count);
        if (stack.isEmpty() || nbt == null || nbt.isEmpty()) {
            return stack;
        }
        try {
            var tag = net.minecraft.nbt.TagParser.parseCompoundFully(nbt);
            var parsed = ItemStack.CODEC.decode(net.minecraft.nbt.NbtOps.INSTANCE, tag);
            return parsed.result().map(r -> r.getFirst() != null ? r.getFirst() : stack).orElse(stack);
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
            var tag = net.minecraft.nbt.TagParser.parseCompoundFully(nbt);
            var input = net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag);
            be.loadWithComponents(input);
        } catch (Exception e) {

        }
    }

    private static final Map<UUID, BlockPos> OPEN_CONTAINER_POS = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerLevel> OPEN_CONTAINER_LEVEL = new ConcurrentHashMap<>();

    public static void openContainer(ReplayServer server, ServerLevel level, ActorClonePlayer clone, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof net.minecraft.world.Container)
            && !(be instanceof net.minecraft.world.MenuProvider)) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);

        if (isLidContainer(be)) {
            level.blockEvent(pos, state.getBlock(), 1, 1);
        }
        playContainerSound(level, pos, be, true);
        UUID id = clone.getUUID();
        OPEN_CONTAINER_POS.put(id, new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        OPEN_CONTAINER_LEVEL.put(id, level);
    }

    public static void closeContainer(ReplayServer server, ActorClonePlayer clone) {
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

            if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.CopperChestBlock) {
                return open ? net.minecraft.sounds.SoundEvents.COPPER_CHEST_OPEN : net.minecraft.sounds.SoundEvents.COPPER_CHEST_CLOSE;
            }
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
        Identifier id = Identifier.tryParse(effectId);
        if (id == null) {
            return null;
        }
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(id).orElse(null);

        return effect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    public static String effectId(Holder<MobEffect> holder) {
        if (holder == null) {
            return "";
        }
        Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(holder.value());
        return id == null ? "" : id.toString();
    }

    public static void spawnLightning(ServerLevel level, double x, double y, double z) {
        net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(
            net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT, level);
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
            case WITHER -> net.minecraft.core.particles.PowerParticleOption.create(
                net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, 0.0f);
            case HIT -> net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT;
            case BUBBLE -> net.minecraft.core.particles.ParticleTypes.BUBBLE;
            case INSTANT -> net.minecraft.core.particles.SpellParticleOption.create(
                net.minecraft.core.particles.ParticleTypes.INSTANT_EFFECT, 0, 1.0f);
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

    public static boolean keyDown(int key) {
        return keyDown(currentWindowHandle(), key);
    }

    public static void knockback(LivingEntity victim, double strength, double x, double z) {
        victim.knockback(strength, x, z, victim.damageSources().generic(), 1.0F);
    }

    public static void swing(LivingEntity entity, InteractionHand hand) {
        entity.swing(hand, true);
    }

    public static void previewSwing(LivingEntity entity, InteractionHand hand) {
        entity.swing(hand, false);
    }

    public static boolean isSwinging(LivingEntity entity) {
        return SwingAccess.isSwinging(entity);
    }

    public static boolean jumpHeld(LivingEntity entity) {
        return JumpAccess.jumpHeld(entity);
    }

    public static int selectedSlot(Player player) {
        return player.getInventory().getSelectedSlot();
    }

    public static void replaySwing(LivingEntity entity, InteractionHand hand, pdx.dev.fbactors.take.ActorEvent event) {
        swing(entity, hand);
    }

    public static void interactEntity(Player player, Entity target, InteractionHand hand) {
        player.interactOn(target, hand, target.position());
    }

    public static net.minecraft.world.entity.projectile.Projectile projectileFromRecord(ServerLevel level, String entityId, String nbt) {
        try {
            var id = net.minecraft.resources.Identifier.tryParse(entityId);
            if (id == null) return null;
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(id);
            if (type == null || !(type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND) instanceof net.minecraft.world.entity.projectile.Projectile projectile)) return null;
            if (nbt != null && !nbt.isEmpty()) {
                var tag = net.minecraft.nbt.TagParser.parseCompoundFully(nbt);
                tag.remove("UUID"); tag.remove("Owner"); tag.remove("Passengers");
                projectile.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
            }
            return projectile;
        } catch (Exception failure) { return null; }
    }

    public static float eventCloudHeight() {var mc=Minecraft.getInstance();return mc.level==null || mc.player==null?Float.NaN:mc.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT,mc.player.position());}

    public static boolean isMainCamera(Object camera) {return camera==Minecraft.getInstance().gameRenderer.mainCamera();}

    public static pdx.dev.fbactors.events.EventCamera eventCamera() {
        var camera=Minecraft.getInstance().gameRenderer.mainCamera();
        return new pdx.dev.fbactors.events.EventCamera(camera.position(),new org.joml.Quaternionf(camera.rotation()),camera.getViewRotationProjectionMatrix(new org.joml.Matrix4f()).mul(new org.joml.Matrix4f().rotation(camera.rotation())));
    }

    public static float[] projectActorPoint(net.minecraft.world.phys.Vec3 world) {
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var position = camera.position();
        var relative = new org.joml.Vector3f((float)(world.x-position.x), (float)(world.y-position.y), (float)(world.z-position.z));
        new org.joml.Quaternionf(camera.rotation()).conjugate().transform(relative);
        if (relative.z >= -0.05f) return null;
        float h = com.moulberry.flashback.editor.ui.ReplayUI.frameHeight;
        float w = com.moulberry.flashback.editor.ui.ReplayUI.frameWidth;
        double fov=Minecraft.getInstance().options.fov().get();
        var editor=com.moulberry.flashback.state.EditorStateManager.getCurrent();
        if(editor!=null && editor.replayVisuals.overrideFov)fov=editor.replayVisuals.overrideFovAmount;
        float focal = h / (2.0f * (float)Math.tan(Math.toRadians(fov) / 2.0));
        return new float[] {com.moulberry.flashback.editor.ui.ReplayUI.frameX + w/2 + relative.x*focal/-relative.z,
            com.moulberry.flashback.editor.ui.ReplayUI.frameY + h/2 - relative.y*focal/-relative.z};
    }

    public static boolean withinAttackRange(Player attacker, Entity target) {
        return attacker.isWithinAttackRange(attacker.getMainHandItem(), target.getBoundingBox(), 0.0);
    }

    public static double fallDistance(LivingEntity entity) {
        return entity.fallDistance;
    }

    public static void setFallDistance(Entity entity, float distance) {
        entity.fallDistance = distance;
    }

    public static void setSelectedSlot(Player player, int slot) {
        player.getInventory().setSelectedSlot(slot);
    }

    public static void clearInvulnerability(Entity entity) {
        ((InvulnerabilityAccess) entity).fbactors$setInvulnerableTime(0);
    }

    public static void critAnimation(Entity target, boolean magic) {
        if (!(target instanceof Player player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ClientboundAnimatePacket packet =
            new ClientboundAnimatePacket(target, magic ? 5 : 4);
        level.getServer().getPlayerList().broadcast(player,
            target.getX(), target.getY(), target.getZ(), target.getEyeY(),
            level.dimension(), packet);
    }

    public static void syncMotion(Entity target) {
        if (!(target instanceof Player player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ClientboundSetEntityMotionPacket packet =
            new ClientboundSetEntityMotionPacket(target.getId(), target.getDeltaMovement());
        level.getServer().getPlayerList().broadcast(player,
            target.getX(), target.getY(), target.getZ(), target.getEyeY(),
            level.dimension(), packet);
    }

    public static void setJumpHeld(LivingEntity entity, boolean jumping) {
        JumpAccess.setJumpHeld(entity, jumping);
    }
    public static void tickSwing(LivingEntity entity) {
        SwingAccess.tickSwing(entity);
    }

    public static void setWalkDist(LivingEntity entity, float walkDist, float walkDistO) {
    }

    public static void setWalkAnimation(LivingEntity entity, float speedOld, float speed,
                                        float position) {
        pdx.dev.fbactors.morph.WalkDriver.driveTo(entity, speedOld, speed, position);
    }

    public static void closeScreen() {
        Minecraft.getInstance().setScreenAndShow(null);
    }

    public static Button spriteButton(int x, int y, int size, String tooltip, Runnable onPress) {
        return new FlashbackButton(x, y, size, size,
            Component.literal(tooltip),
            b -> onPress.run(),
            Identifier.fromNamespaceAndPath("fbactors", "textures/gui/timeline.png"))
            .flashbackWithTooltip();
    }
    public static void openScreen(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    public static int containerSlot(net.minecraft.network.protocol.game.ServerboundContainerClickPacket packet) {
        return packet.slotNum();
    }

    public static void setCamera(Player player, Entity camera) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.setCamera(camera);
        }
    }

    private static int nextPuppetId = -979_000_000;

    private static final Map<UUID, Puppet> PUPPETS = new ConcurrentHashMap<>();

    private static final Map<UUID, Entity> SEEN = new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> UNSEEN_TICKS = new ConcurrentHashMap<>();

    @Nullable
    private static ClientLevel puppetLevel;

    private record Puppet(Entity entity, String morphId) {
    }

    public static void noteActor(@Nullable Entity actor) {
        if (actor == null || actor.getUUID() == null) {
            return;
        }
        if (actor.isRemoved() || !(actor.level() instanceof ClientLevel)) {
            return;
        }
        SEEN.put(actor.getUUID(), actor);
    }

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

    private static void dropPuppet(UUID id) {
        Puppet puppet = PUPPETS.remove(id);
        UNSEEN_TICKS.remove(id);
        LAST_STRIDE_POS.remove(id);
        STRIDES.remove(id);
        SEEN.remove(id);
        if (puppet == null || puppet.entity() == null) {
            return;
        }
        pdx.dev.fbactors.morph.CustomMobs.release(puppet.entity());
        pdx.dev.fbactors.filters.ActorLooks.unalias(puppet.entity().getUUID());
        try {
            if (puppet.entity().level() instanceof ClientLevel at) {
                at.removeEntity(puppet.entity().getId(), Entity.RemovalReason.DISCARDED);
            }
        } catch (Exception ignored) {
        }
    }

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
            pdx.dev.fbactors.morph.CustomMobs.bind(fresh,info.morphId(),id);
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

    public static net.minecraft.world.entity.LivingEntity createMorphPreview(String id) {
        var client = Minecraft.getInstance();
        if (client.level == null) return null;
        var type = resolveType(id);
        if (type == null) return null;
        var entity = type.create(client.level, EntitySpawnReason.COMMAND);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return null;

        pdx.dev.fbactors.morph.PreviewIdentity.assign(living);
        living.setUUID(UUID.randomUUID());
        living.setNoGravity(true);
        living.setSilent(true);
        pdx.dev.fbactors.morph.CustomMobs.bind(living,id);
        if (living instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
        return living;
    }

    @Nullable
    private static EntityType<?> resolveType(String morphId) {
        String raw = pdx.dev.fbactors.morph.CustomMobs.asset(morphId)!=null?"minecraft:armor_stand":MorphRegistry.entityIdFor(morphId);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Identifier id;
        try {
            id = raw.indexOf(':') >= 0
                ? Identifier.tryParse(raw)
                : Identifier.tryParse("minecraft:" + raw);
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

    private static final EquipmentSlot[] PUPPET_SLOTS = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private static void syncBody(UUID id, Entity puppet, Entity actor, MorphRenderInfo info) {

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

            float yaw = actor.getYRot();
            float pitch = actor.getXRot();
            float lastYaw = yaw;
            float lastPitch = pitch;
            double[] lastPose = LAST_STRIDE_POS.get(id);
            if (lastPose != null && lastPose.length >= 4) {
                lastYaw = (float) lastPose[2];
                lastPitch = (float) lastPose[3];

                lastYaw = yaw + net.minecraft.util.Mth.wrapDegrees(lastYaw - yaw);
                lastPitch = pitch + net.minecraft.util.Mth.wrapDegrees(lastPitch - pitch);
            }

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

            try {
                mob.setHealth(living.getHealth() / living.getMaxHealth() * mob.getMaxHealth());
            } catch (Exception ignored) {
            }
            try {
                mob.setAbsorptionAmount(living.getAbsorptionAmount());
            } catch (Exception ignored) {
            }
            copyHurtState(living, mob);

            pdx.dev.fbactors.morph.MorphPoseApplier.apply(mob, info.pose(), actor.isShiftKeyDown());
            applyDrawScale(mob, info);
            if (puppet.tickCount % 10 == 0) {
                syncEquipment(mob, living, info);
            }
        }
    }

    private static final Map<UUID, pdx.dev.fbactors.morph.MorphStride> STRIDES = new ConcurrentHashMap<>();
    private static final Map<UUID, double[]> LAST_STRIDE_POS = new ConcurrentHashMap<>();

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
    public static java.util.UUID interactTarget(net.minecraft.network.protocol.game.ServerboundInteractPacket packet, net.minecraft.server.level.ServerLevel level) { try { net.minecraft.world.entity.Entity e = level.getEntity(packet.entityId()); return e == null ? null : e.getUUID(); } catch (Exception ex) { return null; } }
    public static boolean interactOffHand(net.minecraft.network.protocol.game.ServerboundInteractPacket packet) { try { return packet.hand() == net.minecraft.world.InteractionHand.OFF_HAND; } catch (Exception ex) { return false; } }
    public static boolean interactIsAttack(net.minecraft.network.protocol.game.ServerboundInteractPacket packet) { return false; }
    public static String entityTypeId(net.minecraft.world.entity.Entity entity) { try { var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()); return key == null ? null : key.toString(); } catch (Exception ex) { return null; } }
    public static String entityNbt(net.minecraft.world.entity.Entity entity) {
        try {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, entity.level().registryAccess());
            entity.saveWithoutId(output);
            var tag = output.buildResult();
            tag.remove("Passengers");
            return tag.toString();
        } catch (RuntimeException failure) {
            return pdx.dev.fbactors.actor.ActorEntityIdentity.write(entity.getUUID());
        }
    }
    public static net.minecraft.world.entity.Entity spawnEntityFromRecord(net.minecraft.server.level.ServerLevel level, pdx.dev.fbactors.take.ActorSpawn spawn) { if (spawn == null || spawn.entityId() == null) return null; try { net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(spawn.entityId()); if (id == null) return null; net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(id); if (type == null) return null; net.minecraft.world.entity.Entity e = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND); if (e == null) return null;
            if (spawn.nbt() != null && !spawn.nbt().isEmpty()) {
                var tag = net.minecraft.nbt.TagParser.parseCompoundFully(spawn.nbt());
                tag.remove("Passengers");
                e.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
            }
            java.util.UUID recordedId = pdx.dev.fbactors.actor.ActorEntityIdentity.read(spawn.nbt()); if (recordedId != null) { net.minecraft.world.entity.Entity existing = entityByUuid(level, recordedId); if (existing != null && !existing.isRemoved()) return existing; e.setUUID(recordedId); } e.snapTo(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()); pdx.dev.fbactors.actor.BlockEdits.beginApply(); try { if (!level.addFreshEntity(e)) return null; } finally { pdx.dev.fbactors.actor.BlockEdits.endApply(); } return e; } catch (Exception ex) { return null; } }

    public static void openSkinBrowser(String url) {
        net.minecraft.util.Util.getPlatform().openUri(url);
    }

    public static net.minecraft.world.entity.LivingEntity createSkinPreview(GameProfile profile) {
        var client = Minecraft.getInstance();
        var skin = SKIN_PREVIEW_APPEARANCES.getOrDefault(profileId(profile), client.player != null && profileId(profile).equals(profileId(client.player.getGameProfile())) ? client.player.getSkin() : null);
        if (client.level == null || skin == null) return null;
        var previewTeam = new net.minecraft.world.scores.PlayerTeam(new net.minecraft.world.scores.Scoreboard(), "skin_preview");
        previewTeam.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
        var player = new net.minecraft.client.player.RemotePlayer(client.level, profile) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
            public net.minecraft.world.level.GameType gameMode() { return net.minecraft.world.level.GameType.SURVIVAL; }
            @Override public net.minecraft.world.entity.player.PlayerSkin getSkin() { return skin; }
            @Override public net.minecraft.world.scores.PlayerTeam getTeam() { return previewTeam; }
            @Override public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part) { return true; }
        };
        pdx.dev.fbactors.morph.PreviewIdentity.assign(player);
        player.setUUID(UUID.randomUUID());
        player.setSilent(true); player.setNoGravity(true);
        return player;
    }

    public static java.util.concurrent.CompletableFuture<String> openShaderFile(String directory) {
        return com.moulberry.flashback.exporting.AsyncFileDialogs.openFileDialog(directory,"GLSL fragment shader","fsh","frag","glsl");
    }

    public static java.util.concurrent.CompletableFuture<String> openModelFile(String directory,String extension) {
        return com.moulberry.flashback.exporting.AsyncFileDialogs.openFileDialog(directory,"Custom model",extension);
    }
    public static void discoverModdedMobs() {
        var client=Minecraft.getInstance();if(client.level==null)return;
        for(var type:net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            var key=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if(key==null||key.getNamespace().equals("minecraft")||Morph.registry().has(key.toString()))continue;
            try {
                var entity=createMorphPreview(key.toString());
                if(entity==null||client.getEntityRenderDispatcher().getRenderer(entity)==null)continue;
                Morph.registry().register(pdx.dev.fbactors.morph.ModdedMobs.definition(key.toString(),entity.getType().getDescription().getString(),entity.getBbWidth(),entity.getBbHeight()));
            }catch(RuntimeException|LinkageError error){pdx.dev.fbactors.FlashbackActors.LOGGER.debug("Modded morph unavailable: {}",key,error);}
        }
    }

    public static boolean hasOpenScreen(Minecraft client) { return client.gui.screen() != null; }

    public static net.minecraft.resources.Identifier boatTextureId(String id) {
        return net.minecraft.resources.Identifier.parse(id);
    }
    public static net.minecraft.client.renderer.texture.DynamicTexture registerBoatTexture(String id, com.mojang.blaze3d.platform.NativeImage image) {
        var texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Actor boat fallback", image);
        Minecraft.getInstance().getTextureManager().register(boatTextureId(id), texture);
        return texture;
    }

    public static net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream> vanillaBoatTexture(String id) {
        return Minecraft.getInstance().getVanillaPackResources().getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, boatTextureId(id));
    }

    public static int clientboundLoginPacketId() {
        int[] result={-1};
        net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type,id) -> {
            if(type.equals(net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_LOGIN)) result[0]=id;
        });
        if(result[0]<0) throw new IllegalStateException("No clientbound Login packet in this game protocol");
        return result[0];
    }

    public static java.util.Map<Integer,String> conversionPacketNames(boolean configuration) {
        var result=new java.util.HashMap<Integer,String>();
        if(configuration) net.minecraft.network.protocol.configuration.ConfigurationProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type,id) -> result.put(id,type.id().toString()));
        else net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type,id) -> result.put(id,type.id().toString()));
        return result;
    }

    public static int[] conversionProtocolIds() {
        int[] result={-1,-1,-1,-1};
        net.minecraft.network.protocol.login.LoginProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type,id) -> {
            if(type.equals(net.minecraft.network.protocol.login.LoginPacketTypes.CLIENTBOUND_LOGIN_FINISHED)) result[0]=id;
        });
        net.minecraft.network.protocol.configuration.ConfigurationProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type,id) -> {
            if(type.equals(net.minecraft.network.protocol.configuration.ConfigurationPacketTypes.CLIENTBOUND_FINISH_CONFIGURATION)) result[1]=id;
        });
        net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type,id) -> {
            if(type.equals(net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_LOGIN)) result[2]=id;
            if(type.equals(net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_START_CONFIGURATION)) result[3]=id;
        });
        for(int value:result) if(value<0) throw new IllegalStateException("Incomplete conversion protocol table");
        return result;
    }

    public static boolean hasMorphEntityType(String id) {
        var key=net.minecraft.resources.Identifier.tryParse(pdx.dev.fbactors.morph.MorphRegistry.entityIdFor(id));
        return key!=null && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(key);
    }
    public static void previewGlide(net.minecraft.world.entity.LivingEntity mob) { mob.elytraAnimationState.tick(); }

    public static net.minecraft.world.entity.Entity cameraPreviewBoatEntity() {
        var client=Minecraft.getInstance();if(client.level==null)return null;
        var type=resolveType("oak_boat");if(type==null)type=resolveType("boat");if(type==null)return null;
        var entity=type.create(client.level, EntitySpawnReason.COMMAND);
        return entity;
    }
    public static net.minecraft.client.model.geom.ModelPart cameraPreviewBoatRoot() {
        return Minecraft.getInstance().getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.OAK_BOAT);
    }

}
