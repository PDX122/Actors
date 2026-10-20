package pdx.dev.fbactors.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Documentation for new Compat methods required by the actor playback system.
 *
 * <p>This file is a specification, not a compiled class. It documents every new
 * method that must be added to each of the nine per-target {@code Compat}
 * implementations under {@code compat/<name>/java/pdx/dev/fbactors/compat/Compat.java}.
 * The shared source in {@code src/main/java} already calls these methods; the
 * per-target implementations do not yet exist.
 *
 * <h2>Why these methods exist</h2>
 *
 * <p>The actor system records a performer's interactions with the world - items
 * held, blocks placed, containers opened, blocks mined - and replays them on a
 * clone entity for viewers. The recording side ({@code TakeRecorder}) captures
 * the performer's state; the playback side ({@code ActorManager}) restores it.
 * Both sides need to serialise and deserialise Minecraft's internal NBT format,
 * look up block entities and containers by position, and broadcast packets to
 * viewers. None of that is possible from shared source because the underlying
 * Minecraft APIs differ across all nine targets.
 *
 * <h2>Conventions</h2>
 *
 * <ul>
 *   <li>All methods are {@code public static} - the same pattern as every
 *       existing Compat method.</li>
 *   <li>All methods accept {@code ServerLevel} (not {@code Level}) where the
 *       operation is server-side, because the replay server is always a
 *       {@code ServerLevel} and the compat layer needs to access server-only
 *       APIs like {@code getBlockEntity} and {@code getChunkSource}.</li>
 *   <li>NBT is passed as {@code String} (SNBT format) rather than
 *       {@code CompoundTag} because {@code CompoundTag} is not part of the
 *       safe-to-use vanilla surface - its class name and package are stable,
 *       but the serialisation methods ({@code NbtIo.writeCompressed},
 *       {@code NbtIo.readCompressed}) are not, and the take format already
 *       stores NBT as strings.</li>
 *   <li>All methods return {@code null} or {@code false} on failure rather than
 *       throwing, because the recording and playback paths must not crash the
 *       game if a block entity is missing or a packet cannot be sent.</li>
 * </ul>
 *
 * <h2>Version differences at a glance</h2>
 *
 * <table border="1">
 *   <tr><th>Target</th><th>Key differences</th></tr>
 *   <tr><td>1.21.1–1.21.4</td><td>{@code ResourceLocation} for IDs;
 *       {@code ServerChunkCache.broadcastAndSend} for entity broadcasts;
 *       {@code Inventory.selected} is a public field.</td></tr>
 *   <tr><td>1.21.5–1.21.8</td><td>{@code moveTo} renamed {@code snapTo};
 *       {@code Inventory.selected} now private, use
 *       {@code setSelectedSlot}; {@code GpuTexture} /
 *       {@code GlTexture} split.</td></tr>
 *   <tr><td>1.21.10</td><td>{@code ServerChunkCache.broadcastAndSend}
 *       renamed; use {@code ServerLevel.broadcastEntityEvent} or
 *       {@code PlayerList.broadcast} instead.</td></tr>
 *   <tr><td>1.21.11</td><td>{@code ResourceLocation} renamed
 *       {@code Identifier}; {@code BuiltInRegistries.ITEM.get()}
 *       renamed {@code getValue}; {@code PlayerSkin.texture()} moved.</td></tr>
 *   <tr><td>26.1</td><td>Same as 1.21.11 for the methods here.</td></tr>
 *   <tr><td>26.2</td><td>{@code EntityType} renamed {@code EntityTypes};
 *       {@code setScreen} renamed {@code setScreenAndShow}.</td></tr>
 *   <tr><td>26.3</td><td>{@code Entity.invulnerableTime} now private (use
 *       {@code InvulnerabilityAccess}); {@code GpuTexture} renamed
 *       {@code GpuTextureView}.</td></tr>
 * </table>
 *
 * <h2>Implementation strategy</h2>
 *
 * <p>Each method below lists the exact signature, a description of what it does,
 * which callers need it, and per-target implementation notes. The notes are
 * organised by version range because the differences cluster: 1.21.1–1.21.4
 * share one set of APIs, 1.21.5–1.21.8 share another, 1.21.10 is a transition
 * point, 1.21.11–26.1 share the {@code Identifier} rename, and 26.2–26.3 add
 * further renames.
 *
 * <p>Where a method's implementation is identical across all nine targets, the
 * notes say so explicitly. Where they differ, the notes explain what changes
 * and why.
 */
public final class CompatAdditions {

    private CompatAdditions() {
    }

    // ========================================================================
    // Item NBT
    // ========================================================================

    /**
     * Serialises an {@link ItemStack}'s components to an SNBT string.
     *
     * <p>The existing {@code Compat.itemId} and {@code Compat.itemStack} pair
     * handles the common case: an item with no components (no enchantments, no
     * custom name, no attribute modifiers). But a performer's gear is rarely
     * that simple - a diamond sword with Sharpness V and a custom name needs
     * its components preserved or the replay shows a vanilla sword instead.
     *
     * <p>This method captures the full component NBT so the take can reproduce
     * the exact item the performer held. The companion
     * {@link #itemStackFromNbt} reconstructs it.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>{@code TakeRecorder.captureContainerSlot} - serialises the item
     *       stack in a container slot after a change, so the take can replay
     *       the exact item the performer placed or removed.</li>
     *   <li>{@code TakeRecorder.sampleEquipment} - serialises each equipment
     *       slot during the initial baseline sample, so the take starts with
     *       the performer's actual gear.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static String itemStackNbt(Level level, ItemStack stack)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the level whose registries serialise the
     *       stack's components. Registries must come from the world being
     *       recorded, never from {@code Minecraft.getInstance()
     *       .getSingleplayerServer()}: the client accessor is null without a
     *       singleplayer server and wrong on every other thread.</li>
     *   <li>{@code stack} - the item stack to serialise. May be
     *       {@code null} or empty.</li>
     * </ul>
     *
     * <p><b>Returns:</b> An SNBT string representing the item's components,
     * or {@code null} if the stack is {@code null} or empty. The returned
     * string is suitable for storage in the take file and later passing to
     * {@link #itemStackFromNbt}.
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation uses {@code ItemStack.save(RegistryAccess)} to
     * produce a {@code CompoundTag}, then converts it to a string via
     * {@code Tag.toString()}. The key difference across targets is how to obtain
     * the {@code RegistryAccess}:
     *
     * <p><b>1.21.1–1.21.10:</b>
     * <pre>{@code
     * public static String itemStackNbt(Level level, ItemStack stack) {
     *     if (stack == null || stack.isEmpty()) {
     *         return null;
     *     }
     *     CompoundTag tag = stack.save(level.registryAccess());
     *     return tag.toString();
     * }
     * }</pre>
     *
     * <p><b>1.21.11–26.3:</b>
     * <pre>{@code
     * public static String itemStackNbt(Level level, ItemStack stack) {
     *     if (stack == null || stack.isEmpty()) {
     *         return null;
     *     }
     *     CompoundTag tag = stack.save(level.registryAccess());
     *     return tag.toString();
     * }
     * }</pre>
     *
     * <p>The {@code level.registryAccess()} call is stable across all nine
     * targets. The {@code ItemStack.save(RegistryAccess)} method is also
     * stable - it was introduced in 1.20.5 and has not changed signature.
     * The {@code CompoundTag.toString()} method produces SNBT and is stable.
     *
     * <p><b>Alternative rejected:</b> Using {@code NbtIo.writeCompressed}
     * produces a binary format, not a string. The take format stores NBT as
     * strings (see {@code TakeIO}), so {@code Tag.toString()} is the correct
     * choice.
     */
    public static String itemStackNbt(Level level, ItemStack stack) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    /**
     * Reconstructs an {@link ItemStack} from its item ID, count, and NBT string.
     *
     * <p>This is the inverse of {@link #itemStackNbt}. The existing
     * {@code Compat.itemStack(String, int)} creates a vanilla item with no
     * components; this method restores the full item including enchantments,
     * custom names, attribute modifiers, and any other components the
     * performer's gear had.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>{@code ActorManager.applyContainers} - reconstructs the item
     *       stack from a recorded container slot change, so the container
     *       shows the exact item the performer placed.</li>
     *   <li>{@code ActorManager.applyEquipment} - reconstructs each
     *       equipment slot from the recorded baseline, so the clone wears
     *       the performer's actual gear.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static ItemStack itemStackFromNbt(Level level, String itemId, int count, String nbt)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the level whose registries decode the
     *       components, same source as {@link #itemStackNbt}.</li>
     *   <li>{@code itemId} - the item's registry ID as a string (e.g.
     *       {@code "minecraft:diamond_sword"}). May be {@code null} or empty
     *       for an empty slot.</li>
     *   <li>{@code count} - the stack size. Must be at least 1 for a
     *       non-empty stack.</li>
     *   <li>{@code nbt} - the SNBT string from {@link #itemStackNbt}, or
     *       {@code null} if the item has no components.</li>
     * </ul>
     *
     * <p><b>Returns:</b> The reconstructed {@link ItemStack}, or
     * {@link ItemStack#EMPTY} if {@code itemId} is {@code null} or empty.
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation first creates a vanilla item via the existing
     * {@code Compat.itemStack(itemId, count)} pattern, then applies the NBT
     * components if present:
     *
     * <p><b>1.21.1–1.21.10:</b>
     * <pre>{@code
     * public static ItemStack itemStackFromNbt(Level level, String itemId, int count, String nbt) {
     *     ItemStack stack = itemStack(itemId, count);
     *     if (stack.isEmpty() || nbt == null || nbt.isEmpty()) {
     *         return stack;
     *     }
     *     CompoundTag tag = StringTagParser.parseTag(nbt);
     *     return ItemStack.parse(level.registryAccess(), tag).orElse(ItemStack.EMPTY);
     * }
     * }</pre>
     *
     * <p><b>1.21.11–26.3:</b>
     * <pre>{@code
     * public static ItemStack itemStackFromNbt(Level level, String itemId, int count, String nbt) {
     *     ItemStack stack = itemStack(itemId, count);
     *     if (stack.isEmpty() || nbt == null || nbt.isEmpty()) {
     *         return stack;
     *     }
     *     CompoundTag tag = StringTagParser.parseTag(nbt);
     *     return ItemStack.parse(level.registryAccess(), tag).orElse(ItemStack.EMPTY);
     * }
     * }</pre>
     *
     * <p>The {@code StringTagParser.parseTag(String)} method is stable across
     * all nine targets. The {@code ItemStack.parse(RegistryAccess, CompoundTag)}
     * method is stable. The only difference is the type used for the registry
     * ID lookup ({@code ResourceLocation} vs {@code Identifier}), which is
     * already handled by the existing {@code itemStack(String, int)} method.
     *
     * <p><b>Alternative rejected:</b> Using {@code NbtIo.readCompressed}
     * would require a {@code byte[]} instead of a {@code String}, which does
     * not match the take format.
     */
    public static ItemStack itemStackFromNbt(Level level, String itemId, int count, String nbt) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    // ========================================================================
    // Block Entity
    // ========================================================================

    /**
     * Checks whether the block at the given position has a block entity.
     *
     * <p>Not every block has a block entity - dirt, stone, and most plain
     * blocks do not. Signs, chests, furnaces, and other tile-entity blocks do.
     * This method is a cheap existence check that lets callers avoid the cost
     * of serialising NBT for blocks that have no block entity.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>{@code MixinServerLevelBlockEntity} - checks whether a block
     *       about to be broken or just placed has a block entity before
     *       capturing its NBT. Without this check, the mixin would try to
     *       capture NBT for every block change, including plain blocks that
     *       have no block entity.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static boolean hasBlockEntity(Level level, BlockPos pos)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the level to check. Must be a {@code ServerLevel}
     *       for the block-entity lookup to work, but the signature uses
     *       {@code Level} because the mixin has a {@code Level} reference at
     *       the injection point and the cast to {@code ServerLevel} is done
     *       inside the method.</li>
     *   <li>{@code pos} - the block position to check.</li>
     * </ul>
     *
     * <p><b>Returns:</b> {@code true} if the block at {@code pos} has a block
     * entity, {@code false} otherwise.
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation checks the block state's
     * {@code BlockState.hasBlockEntity()} method, which is stable across all
     * nine targets:
     *
     * <p><b>All targets (1.21.1–26.3):</b>
     * <pre>{@code
     * public static boolean hasBlockEntity(Level level, BlockPos pos) {
     *     if (level == null || pos == null) {
     *         return false;
     *     }
     *     BlockState state = level.getBlockState(pos);
     *     return state.hasBlockEntity();
     * }
     * }</pre>
     *
     * <p>The {@code BlockState.hasBlockEntity()} method is a vanilla method
     * that returns {@code true} if the block state's block has a corresponding
     * {@code BlockEntityType}. It is stable across all nine targets and does
     * not require any Compat indirection.
     *
     * <p><b>Alternative rejected:</b> Checking
     * {@code level.getBlockEntity(pos) != null} would also work, but it
     * actually instantiates the block entity if it does not exist yet, which
     * is more expensive than the {@code hasBlockEntity()} check. The
     * {@code hasBlockEntity()} check is a pure metadata lookup that does not
     * create any objects.
     */
    public static boolean hasBlockEntity(Level level, BlockPos pos) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    /**
     * Serialises the block entity at the given position to an SNBT string.
     *
     * <p>Block entities store data that is not part of the block state -
     * sign text, chest contents, furnace progress, banner patterns, and so on.
     * This method captures that data so the take can reproduce the exact
     * state the performer's world was in.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>{@code TakeRecorder.captureBlockEntity} - serialises the
     *       block entity's NBT when a block is placed or broken, so the
     *       take can restore the sign text, chest contents, etc. on
     *       replay.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static String blockEntityNbt(Level level, BlockPos pos)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the level containing the block entity. Must be
     *       a {@code ServerLevel} for the block-entity lookup to work.</li>
     *   <li>{@code pos} - the block position of the block entity.</li>
     * </ul>
     *
     * <p><b>Returns:</b> An SNBT string representing the block entity's NBT,
     * or {@code null} if there is no block entity at the position or if the
     * NBT cannot be serialised.
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation looks up the block entity, calls its
     * {@code saveWithoutId(RegistryAccess)} method to produce a
     * {@code CompoundTag}, then converts it to a string:
     *
     * <p><b>1.21.1–1.21.10:</b>
     * <pre>{@code
     * public static String blockEntityNbt(Level level, BlockPos pos) {
     *     if (!(level instanceof ServerLevel serverLevel)) {
     *         return null;
     *     }
     *     BlockEntity be = serverLevel.getBlockEntity(pos);
     *     if (be == null) {
     *         return null;
     *     }
     *     CompoundTag tag = be.saveWithoutId(serverLevel.registryAccess());
     *     return tag.toString();
     * }
     * }</pre>
     *
     * <p><b>1.21.11–26.3:</b>
     * <pre>{@code
     * public static String blockEntityNbt(Level level, BlockPos pos) {
     *     if (!(level instanceof ServerLevel serverLevel)) {
     *         return null;
     *     }
     *     BlockEntity be = serverLevel.getBlockEntity(pos);
     *     if (be == null) {
     *         return null;
     *     }
     *     CompoundTag tag = be.saveWithoutId(serverLevel.registryAccess());
     *     return tag.toString();
     * }
     * }</pre>
     *
     * <p>The {@code BlockEntity.saveWithoutId(RegistryAccess)} method is
     * stable across all nine targets. The {@code ServerLevel.getBlockEntity}
     * method is stable. The {@code ServerLevel.registryAccess()} method is
     * stable. The {@code CompoundTag.toString()} method is stable.
     *
     * <p><b>Important:</b> The block entity may not exist yet at the moment
     * {@code setBlock} returns - vanilla creates it in the same tick but
     * potentially after the block state change. The caller
     * ({@code TakeRecorder.captureBlockEntity}) handles this by returning
     * early if the NBT is {@code null} or empty, and the next block-entity
     * edit the performer makes will trigger a fresh capture.
     *
     * <p><b>Alternative rejected:</b> Using {@code be.saveWithMetadata()} or
     * {@code be.saveAdditional()} would include the block entity's position
     * and type ID, which is redundant because the take already stores the
     * position and type separately. Using {@code NbtIo.writeCompressed}
     * would produce a binary format, not a string.
     */
    public static String blockEntityNbt(Level level, BlockPos pos) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    /**
     * Applies a recorded NBT snapshot to the block entity at the given position.
     *
     * <p>This is the inverse of {@link #blockEntityNbt}. During playback, the
     * take restores the block entity's state - sign text, chest contents,
     * banner patterns - so viewers see the world as the performer left it.
     *
     * <p>The {@code type} discriminator lets the compat layer decide whether
     * to bother decoding the NBT at all. A playback path that only cares
     * about chests can skip every sign edit without parsing a single NBT
     * string.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>{@code ActorManager.applyBlockEntities} - applies the recorded
     *       NBT snapshot to the block entity at the given position during
     *       playback. Called once per take tick for each block-entity edit
     *       recorded at that tick.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static void applyBlockEntityNbt(ServerLevel level, BlockPos pos,
     *                                        String type, String nbt)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the server level containing the block entity.</li>
     *   <li>{@code pos} - the block position of the block entity.</li>
     *   <li>{@code type} - the type discriminator from
     *       {@code ActorBlockEntityEdit.type()} (e.g. {@code "oak_sign"},
     *       {@code "chest"}). Used to skip NBT decoding for block entity
     *       types the playback path does not care about.</li>
     *   <li>{@code nbt} - the SNBT string from {@link #blockEntityNbt}, or
     *       {@code null} if the block entity had no NBT.</li>
     * </ul>
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation looks up the block entity, parses the SNBT
     * string, and applies it:
     *
     * <p><b>1.21.1–1.21.10:</b>
     * <pre>{@code
     * public static void applyBlockEntityNbt(ServerLevel level, BlockPos pos,
     *                                        String type, String nbt) {
     *     if (nbt == null || nbt.isEmpty()) {
     *         return;
     *     }
     *     BlockEntity be = level.getBlockEntity(pos);
     *     if (be == null) {
     *         return;
     *     }
     *     CompoundTag tag = StringTagParser.parseTag(nbt);
     *     be.loadWithComponents(tag, level.registryAccess());
     *     be.setChanged();
     * }
     * }</pre>
     *
     * <p><b>1.21.11–26.3:</b>
     * <pre>{@code
     * public static void applyBlockEntityNbt(ServerLevel level, BlockPos pos,
     *                                        String type, String nbt) {
     *     if (nbt == null || nbt.isEmpty()) {
     *         return;
     *     }
     *     BlockEntity be = level.getBlockEntity(pos);
     *     if (be == null) {
     *         return;
     *     }
     *     CompoundTag tag = StringTagParser.parseTag(nbt);
     *     be.loadWithComponents(tag, level.registryAccess());
     *     be.setChanged();
     * }
     * }</pre>
     *
     * <p>The {@code BlockEntity.loadWithComponents(CompoundTag, RegistryAccess)}
     * method is stable across all nine targets. It was introduced in 1.20.5
     * as part of the component system rewrite and has not changed signature.
     * The {@code StringTagParser.parseTag(String)} method is stable. The
     * {@code BlockEntity.setChanged()} method is stable.
     *
     * <p><b>Note on the {@code type} parameter:</b> The {@code type} parameter
     * is not used in the current implementation because
     * {@code loadWithComponents} handles all block entity types uniformly.
     * However, it is part of the signature so that future implementations
     * can skip NBT decoding for block entity types that do not need it
     * (e.g. a playback path that only cares about chests can skip sign
     * edits). The {@code type} value is the path component of the block's
     * registry name, as returned by
     * {@code MixinServerLevelBlockEntity.blockEntityType}.
     *
     * <p><b>Alternative rejected:</b> Using {@code be.load(CompoundTag)}
     * (the pre-1.20.5 method) would not restore components correctly because
     * it does not accept a {@code RegistryAccess}. Using
     * {@code be.loadWithComponents} is the correct choice because it handles
     * both the legacy NBT fields and the new component system.
     */
    public static void applyBlockEntityNbt(ServerLevel level, BlockPos pos,
                                           String type, String nbt) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    // ========================================================================
    // Container
    // ========================================================================

    /**
     * Gets the {@link Container} at the given position, if any.
     *
     * <p>Containers - chests, shulker boxes, furnaces, brewing stands, hoppers,
     * dispensers - are block entities that implement the {@code Container}
     * interface. This method looks up the block entity at the given position
     * and returns it as a {@code Container}, or {@code null} if there is no
     * container there.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>{@code ActorManager.applyContainers} - gets the container at
     *       the recorded position to update its contents during slot-change
     *       replay.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static Container containerAt(ServerLevel level, BlockPos pos)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the server level containing the container.</li>
     *   <li>{@code pos} - the block position of the container.</li>
     * </ul>
     *
     * <p><b>Returns:</b> The {@link Container} at the given position, or
     * {@code null} if there is no block entity at the position or if the
     * block entity does not implement {@code Container}.
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation looks up the block entity and checks whether it
     * implements {@code Container}:
     *
     * <p><b>All targets (1.21.1–26.3):</b>
     * <pre>{@code
     * public static Container containerAt(ServerLevel level, BlockPos pos) {
     *     if (level == null || pos == null) {
     *         return null;
     *     }
     *     BlockEntity be = level.getBlockEntity(pos);
     *     if (be instanceof Container container) {
     *         return container;
     *     }
     *     return null;
     * }
     * }</pre>
     *
     * <p>The {@code Container} interface is in {@code net.minecraft.world} and
     * is stable across all nine targets. The {@code instanceof} pattern
     * matching is stable. The {@code ServerLevel.getBlockEntity} method is
     * stable.
     *
     * <p><b>Alternative rejected:</b> Using
     * {@code level.getBlockEntity(pos) instanceof Container} directly would
     * work, but extracting the block entity first allows for a null check
     * and makes the code more readable.
     */
    public static Container containerAt(ServerLevel level, BlockPos pos) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    /**
     * Serialises the contents of the container at the given position to an
     * SNBT string.
     *
     * <p>This captures the full state of a container - every slot's item
     * stack - so the take can reproduce the exact contents the performer's
     * container had. This is distinct from {@link #blockEntityNbt}, which
     * captures the block entity's own NBT (including container contents as
     * stored by vanilla). This method captures the container's contents via
     * the {@code Container} interface, which is the canonical way to access
     * them.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>Future: {@code TakeRecorder.captureContainer} - would
     *       serialise the full container contents when a container is opened,
     *       so the take can restore the exact state. Not yet called from
     *       production code; the current recording path captures individual
     *       slot changes via {@code captureContainerSlot} instead.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static String containerNbt(ServerLevel level, BlockPos pos)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the server level containing the container.</li>
     *   <li>{@code pos} - the block position of the container.</li>
     * </ul>
     *
     * <p><b>Returns:</b> An SNBT string representing the container's contents,
     * or {@code null} if there is no container at the position.
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation gets the container via {@link #containerAt},
     * then serialises each slot's item stack:
     *
     * <p><b>1.21.1–1.21.10:</b>
     * <pre>{@code
     * public static String containerNbt(ServerLevel level, BlockPos pos) {
     *     Container container = containerAt(level, pos);
     *     if (container == null) {
     *         return null;
     *     }
     *     ListTag items = new ListTag();
     *     for (int i = 0; i < container.getContainerSize(); i++) {
     *         ItemStack stack = container.getItem(i);
     *         if (!stack.isEmpty()) {
     *             CompoundTag itemTag = stack.save(level.registryAccess());
     *             itemTag.putInt("Slot", i);
     *             items.add(itemTag);
     *         }
     *     }
     *     CompoundTag tag = new CompoundTag();
     *     tag.put("Items", items);
     *     return tag.toString();
     * }
     * }</pre>
     *
     * <p><b>1.21.11–26.3:</b>
     * <pre>{@code
     * public static String containerNbt(ServerLevel level, BlockPos pos) {
     *     Container container = containerAt(level, pos);
     *     if (container == null) {
     *         return null;
     *     }
     *     ListTag items = new ListTag();
     *     for (int i = 0; i < container.getContainerSize(); i++) {
     *         ItemStack stack = container.getItem(i);
     *         if (!stack.isEmpty()) {
     *             CompoundTag itemTag = stack.save(level.registryAccess());
     *             itemTag.putInt("Slot", i);
     *             items.add(itemTag);
     *         }
     *     }
     *     CompoundTag tag = new CompoundTag();
     *     tag.put("Items", items);
     *     return tag.toString();
     * }
     * }</pre>
     *
     * <p>The {@code Container.getContainerSize()} and {@code Container.getItem(int)}
     * methods are stable across all nine targets. The
     * {@code ItemStack.save(RegistryAccess)} method is stable. The
     * {@code CompoundTag.putInt(String, int)} method is stable. The
     * {@code ListTag.add(Tag)} method is stable.
     *
     * <p><b>Note:</b> The {@code "Slot"} key in the item tag is the same key
     * vanilla uses when serialising container contents to NBT. This ensures
     * compatibility with {@link #applyContainerNbt}, which uses the same
     * format.
     *
     * <p><b>Alternative rejected:</b> Using
     * {@code Container.saveAllItems(CompoundTag, RegistryAccess)} would be
     * simpler, but that method is not available on the {@code Container}
     * interface - it is only available on {@code BaseContainerBlockEntity}
     * and {@code RandomizableContainerBlockEntity}. Since
     * {@link #containerAt} returns a {@code Container}, not a
     * {@code BaseContainerBlockEntity}, the manual loop is necessary.
     */
    public static String containerNbt(ServerLevel level, BlockPos pos) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    /**
     * Applies a recorded NBT snapshot to the container at the given position.
     *
     * <p>This is the inverse of {@link #containerNbt}. During playback, the
     * take restores the container's contents - every slot's item stack - so
     * viewers see the container as the performer left it.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>Future: {@code ActorManager.applyContainer} - would apply
     *       the recorded NBT snapshot to the container during playback. Not
     *       yet called from production code; the current playback path applies
     *       individual slot changes via {@code Compat.updateContainerSlot}
     *       instead.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static void applyContainerNbt(ServerLevel level, BlockPos pos,
     *                                      String nbt)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the server level containing the container.</li>
     *   <li>{@code pos} - the block position of the container.</li>
     *   <li>{@code nbt} - the SNBT string from {@link #containerNbt}, or
     *       {@code null} if the container had no contents.</li>
     * </ul>
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation gets the container via {@link #containerAt},
     * parses the SNBT string, and applies each slot:
     *
     * <p><b>1.21.1–1.21.10:</b>
     * <pre>{@code
     * public static void applyContainerNbt(ServerLevel level, BlockPos pos,
     *                                      String nbt) {
     *     Container container = containerAt(level, pos);
     *     if (container == null || nbt == null || nbt.isEmpty()) {
     *         return;
     *     }
     *     CompoundTag tag = StringTagParser.parseTag(nbt);
     *     ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
     *     for (int i = 0; i < items.size(); i++) {
     *         CompoundTag itemTag = items.getCompound(i);
     *         int slot = itemTag.getInt("Slot");
     *         if (slot >= 0 && slot < container.getContainerSize()) {
     *             ItemStack stack = ItemStack.parse(level.registryAccess(), itemTag)
     *                 .orElse(ItemStack.EMPTY);
     *             container.setItem(slot, stack);
     *         }
     *     }
     * }
     * }</pre>
     *
     * <p><b>1.21.11–26.3:</b>
     * <pre>{@code
     * public static void applyContainerNbt(ServerLevel level, BlockPos pos,
     *                                      String nbt) {
     *     Container container = containerAt(level, pos);
     *     if (container == null || nbt == null || nbt.isEmpty()) {
     *         return;
     *     }
     *     CompoundTag tag = StringTagParser.parseTag(nbt);
     *     ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
     *     for (int i = 0; i < items.size(); i++) {
     *         CompoundTag itemTag = items.getCompound(i);
     *         int slot = itemTag.getInt("Slot");
     *         if (slot >= 0 && slot < container.getContainerSize()) {
     *             ItemStack stack = ItemStack.parse(level.registryAccess(), itemTag)
     *                 .orElse(ItemStack.EMPTY);
     *             container.setItem(slot, stack);
     *         }
     *     }
     * }
     * }</pre>
     *
     * <p>The {@code CompoundTag.getList(String, int)} method is stable across
     * all nine targets. The {@code ListTag.size()} and
     * {@code ListTag.getCompound(int)} methods are stable. The
     * {@code CompoundTag.getInt(String)} method is stable. The
     * {@code ItemStack.parse(RegistryAccess, CompoundTag)} method is stable.
     * The {@code Container.setItem(int, ItemStack)} method is stable.
     *
     * <p><b>Alternative rejected:</b> Using
     * {@code Container.loadAllItems(CompoundTag, RegistryAccess)} would be
     * simpler, but that method is not available on the {@code Container}
     * interface - it is only available on {@code BaseContainerBlockEntity}
     * and {@code RandomizableContainerBlockEntity}. Since
     * {@link #containerAt} returns a {@code Container}, not a
     * {@code BaseContainerBlockEntity}, the manual loop is necessary.
     */
    public static void applyContainerNbt(ServerLevel level, BlockPos pos,
                                         String nbt) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    /**
     * Sends a container-open packet to all viewers of the given position.
     *
     * <p>When a performer opens a chest, vanilla sends a
     * {@code ClientboundContainerOpenPacket} to nearby players so they see
     * the chest GUI. A clone has no client, so the replay server must send
     * this packet explicitly. Without it, viewers would not see the container
     * GUI open when the actor interacts with a chest.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>{@code ActorManager.applyContainers} - sends the open packet
     *       when replaying a recorded "open" container interaction.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static void sendContainerOpen(ServerLevel level, BlockPos pos,
     *                                      int containerId)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the server level containing the container.</li>
     *   <li>{@code pos} - the block position of the container. Used to
     *       determine which players are close enough to receive the packet.</li>
     *   <li>{@code containerId} - the container's ID. This is the same ID
     *       the performer's client used, so viewers' clients can match the
     *       packet to the correct GUI.</li>
     * </ul>
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation gets the container's menu type and sends the
     * open packet to nearby players:
     *
     * <p><b>1.21.1–1.21.10:</b>
     * <pre>{@code
     * public static void sendContainerOpen(ServerLevel level, BlockPos pos,
     *                                      int containerId) {
     *     Container container = containerAt(level, pos);
     *     if (container == null) {
     *         return;
     *     }
     *     BlockEntity be = level.getBlockEntity(pos);
     *     if (be instanceof BaseContainerBlockEntity base) {
     *         AbstractContainerMenuType<?> type = base.getMenuType();
     *         for (ServerPlayer player : level.players()) {
     *             if (player.blockPosition().distSqr(pos) < 64 * 64) {
     *                 player.connection.send(
     *                     new ClientboundContainerOpenPacket(
     *                         containerId, type, pos,
     *                         Component.empty()));
     *             }
     *         }
     *     }
     * }
     * }</pre>
     *
     * <p><b>1.21.11–26.3:</b>
     * <pre>{@code
     * public static void sendContainerOpen(ServerLevel level, BlockPos pos,
     *                                      int containerId) {
     *     Container container = containerAt(level, pos);
     *     if (container == null) {
     *         return;
     *     }
     *     BlockEntity be = level.getBlockEntity(pos);
     *     if (be instanceof BaseContainerBlockEntity base) {
     *         AbstractContainerMenuType<?> type = base.getMenuType();
     *         for (ServerPlayer player : level.players()) {
     *             if (player.blockPosition().distSqr(pos) < 64 * 64) {
     *                 player.connection.send(
     *                     new ClientboundContainerOpenPacket(
     *                         containerId, type, pos,
     *                         Component.empty()));
     *             }
     *         }
     *     }
     * }
     * }</pre>
     *
     * <p>The {@code BaseContainerBlockEntity.getMenuType()} method is stable
     * across all nine targets. The {@code ClientboundContainerOpenPacket}
     * constructor is stable. The {@code ServerLevel.players()} method is
     * stable. The {@code ServerPlayer.blockPosition()} method is stable.
     * The {@code BlockPos.distSqr(BlockPos)} method is stable.
     *
     * <p><b>Note:</b> The {@code 64 * 64} distance check is the same radius
     * vanilla uses for container open packets. Players outside this radius
     * do not need to see the container GUI.
     *
     * <p><b>Alternative rejected:</b> Using
     * {@code level.getServer().getPlayerList().broadcast()} would send the
     * packet to all players on the server, not just nearby ones. This is
     * wasteful and can cause issues with players who are too far away to
     * see the container. The distance check is necessary.
     */
    public static void sendContainerOpen(ServerLevel level, BlockPos pos,
                                         int containerId) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    /**
     * Sends a container-close packet to all viewers of the given position.
     *
     * <p>When a performer closes a chest, vanilla sends a
     * {@code ClientboundContainerClosePacket} to the player so their client
     * closes the GUI. A clone has no client, so the replay server must send
     * this packet explicitly. Without it, viewers would see the container GUI
     * remain open after the actor closes it.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>{@code ActorManager.applyContainers} - sends the close packet
     *       when replaying a recorded "close" container interaction.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static void sendContainerClose(ServerLevel level, BlockPos pos)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the server level containing the container.</li>
     *   <li>{@code pos} - the block position of the container. Used to
     *       determine which players are close enough to receive the packet.</li>
     * </ul>
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation sends the close packet to nearby players:
     *
     * <p><b>1.21.1–1.21.10:</b>
     * <pre>{@code
     * public static void sendContainerClose(ServerLevel level, BlockPos pos) {
     *     for (ServerPlayer player : level.players()) {
     *         if (player.blockPosition().distSqr(pos) < 64 * 64) {
     *             player.connection.send(
     *                 new ClientboundContainerClosePacket(
     *                     player.containerMenu.containerId));
     *         }
     *     }
     * }
     * }</pre>
     *
     * <p><b>1.21.11–26.3:</b>
     * <pre>{@code
     * public static void sendContainerClose(ServerLevel level, BlockPos pos) {
     *     for (ServerPlayer player : level.players()) {
     *         if (player.blockPosition().distSqr(pos) < 64 * 64) {
     *             player.connection.send(
     *                 new ClientboundContainerClosePacket(
     *                     player.containerMenu.containerId));
     *         }
     *     }
     * }
     * }</pre>
     *
     * <p>The {@code ClientboundContainerClosePacket} constructor is stable
     * across all nine targets. The {@code ServerLevel.players()} method is
     * stable. The {@code ServerPlayer.containerMenu} field is stable. The
     * {@code AbstractContainerMenu.containerId} field is stable.
     *
     * <p><b>Note:</b> The {@code player.containerMenu.containerId} is used
     * instead of a stored container ID because the close packet must match
     * the container ID that the viewer's client currently has open. If the
     * viewer has a different container open (e.g. their own inventory), the
     * close packet will close that container instead, which is the correct
     * behaviour - the viewer should not have a container open if the actor
     * is closing one.
     *
     * <p><b>Alternative rejected:</b> Using a stored container ID from the
     * take would be incorrect because the viewer's client may have a
     * different container open. The close packet must match the viewer's
     * current container, not the actor's.
     */
    public static void sendContainerClose(ServerLevel level, BlockPos pos) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }

    // ========================================================================
    // Mining
    // ========================================================================

    /**
     * Sends a block-break progress packet to all viewers of the given position.
     *
     * <p>When a performer mines a block, vanilla sends
     * {@code ClientboundBlockDestructionPacket} messages to nearby players to
     * show the crack overlay. A clone has no client, so the replay server must
     * send these packets explicitly. Without them, viewers would not see the
     * crack overlay when the actor mines a block.
     *
     * <p>The {@code breakerId} is the entity ID of the entity doing the
     * mining. Vanilla assigns a unique ID to each mining operation, and the
     * client uses this ID to track which cracks belong to which block. The
     * replay server must use a consistent ID for each mining operation so
     * the client can update the correct crack overlay.
     *
     * <p><b>Callers:</b>
     * <ul>
     *   <li>{@code ActorManager.applyMining} - sends the progress packet
     *       when replaying a recorded mining step. The method already
     *       constructs {@code ClientboundBlockDestructionPacket} directly;
     *       this Compat method would encapsulate the broadcast logic.</li>
     * </ul>
     *
     * <p><b>Signature:</b>
     * <pre>{@code
     * public static void sendMiningProgress(ServerLevel level, int breakerId,
     *                                      BlockPos pos, int progress)
     * }</pre>
     *
     * <p><b>Parameters:</b>
     * <ul>
     *   <li>{@code level} - the server level containing the block being
     *       mined.</li>
     *   <li>{@code breakerId} - the entity ID of the miner. This is the
     *       ID the actor's clone was assigned when it started mining, not
     *       the performer's entity ID.</li>
     *   <li>{@code pos} - the block position being mined.</li>
     *   <li>{@code progress} - the break progress stage (0–9), or a
     *       negative value to clear the overlay.</li>
     * </ul>
     *
     * <p><b>Implementation notes:</b>
     *
     * <p>The implementation sends the packet to nearby players:
     *
     * <p><b>1.21.1–1.21.10:</b>
     * <pre>{@code
     * public static void sendMiningProgress(ServerLevel level, int breakerId,
     *                                      BlockPos pos, int progress) {
     *     ClientboundBlockDestructionPacket packet =
     *         new ClientboundBlockDestructionPacket(breakerId, pos, progress);
     *     for (ServerPlayer player : level.players()) {
     *         if (player.blockPosition().distSqr(pos) < 64 * 64) {
     *             player.connection.send(packet);
     *         }
     *     }
     * }
     * }</pre>
     *
     * <p><b>1.21.11–26.3:</b>
     * <pre>{@code
     * public static void sendMiningProgress(ServerLevel level, int breakerId,
     *                                      BlockPos pos, int progress) {
     *     ClientboundBlockDestructionPacket packet =
     *         new ClientboundBlockDestructionPacket(breakerId, pos, progress);
     *     for (ServerPlayer player : level.players()) {
     *         if (player.blockPosition().distSqr(pos) < 64 * 64) {
     *             player.connection.send(packet);
     *         }
     *     }
     * }
     * }</pre>
     *
     * <p>The {@code ClientboundBlockDestructionPacket} constructor is stable
     * across all nine targets. The {@code ServerLevel.players()} method is
     * stable. The {@code ServerPlayer.blockPosition()} method is stable.
     * The {@code BlockPos.distSqr(BlockPos)} method is stable.
     *
     * <p><b>Note:</b> The {@code 64 * 64} distance check is the same radius
     * vanilla uses for block destruction packets (see
     * {@code ServerLevel.destroyBlockProgress} in the vanilla source). Players
     * outside this radius do not need to see the crack overlay.
     *
     * <p><b>Alternative rejected:</b> Using
     * {@code level.getChunkSource().broadcast()} or
     * {@code level.getServer().getPlayerList().broadcast()} would send the
     * packet to all players on the server, not just nearby ones. This is
     * wasteful and can cause issues with players who are too far away to
     * see the block. The distance check is necessary.
     *
     * <p><b>Current usage:</b> The {@code ActorManager.applyMining} method
     * already constructs and broadcasts {@code ClientboundBlockDestructionPacket}
     * directly. This Compat method would encapsulate that logic, but the
     * current implementation works correctly without it. The method is
     * documented here for completeness and as a potential refactoring target.
     */
    public static void sendMiningProgress(ServerLevel level, int breakerId,
                                         BlockPos pos, int progress) {
        throw new UnsupportedOperationException("Documentation only - implement in per-target Compat");
    }
}
