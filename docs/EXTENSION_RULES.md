# Extension Rules - `fbactors` (pdx.dev.fbactors)

## 0. THE ONE RULE THAT MATTERS

`src/main/java` is **shared source compiled into all 9 Minecraft targets**
(1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2, 26.3).

Anything in `net.minecraft.*` / Fabric API that differs between targets **must** go through
`pdx.dev.fbactors.compat.Compat` (one implementation per target under `compat/<name>/java/`).

**Do NOT add code that references an API that does not exist in every target.** If you need a
capability `Compat` does not expose, implement the logic in terms of what it *does* expose, or
leave a clearly-marked hook and report it in your summary. Do not edit anything under `compat/`.

## 1. Safe-to-use vanilla surface (stable across all 9 targets)

- `net.minecraft.world.entity.Entity`, `LivingEntity`, `player.Player`
- `net.minecraft.world.entity.EquipmentSlot` (MAINHAND, OFFHAND, HEAD, CHEST, LEGS, FEET)
- `net.minecraft.world.entity.Pose` (STANDING, CROUCHING, FALL_FLYING, SLEEPING, SWIMMING, SPIN_ATTACK)
- `net.minecraft.core.BlockPos`, `net.minecraft.core.Vec3i`, `net.minecraft.util.Mth`
- `net.minecraft.world.phys.Vec3`, `net.minecraft.world.phys.AABB`
- `net.minecraft.world.item.ItemStack`, `net.minecraft.world.item.Items`
- `net.minecraft.network.chat.Component`

NOT safe without Compat: `KeyBindingHelper`, `ClientCommandManager`, `ResourceLocation`
(renamed `Identifier` at 1.21.11), `EntityType` (renamed `EntityTypes` at 26.2),
`PlayerSkin.texture()` (moved at 1.21.10), `moveTo` (renamed `snapTo` at 1.21.5).

## 2. The full public API of `pdx.dev.fbactors.compat.Compat`

Same signature on all 9 targets:

```java
long headTexture(UUID id)
void sampleNearest(long handle)
KeyMapping registerKey(String modId, String translationKey, String keyName)
LiteralArgumentBuilder<FabricClientCommandSource> literal(String name)
<T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, T type)
EntityType<?> playerEntityType()
String profileName(GameProfile profile)
UUID profileId(GameProfile profile)
Entity entityByUuid(ServerLevel level, UUID id)
void snapTo(Entity entity, double x, double y, double z, float yaw, float pitch)
void cancelInterpolation(Entity entity)
Packet<?> positionSyncPacket(Entity entity)
Connection silentConnection()
ResourceKey<Level> dimensionKey(String dimension)
String dimensionId(@Nullable ServerLevel level)
String itemId(ItemStack stack)
ItemStack itemStack(String itemId, int count)
Holder<MobEffect> effectHolder(String effectId)
String effectId(Holder<MobEffect> holder)
void spawnLightning(ServerLevel level, double x, double y, double z)
void spawnAnvil(ServerLevel level, double x, double y, double z)
void burst(ServerLevel level, FxKinds.Burst burst, ...)
void sound(ServerLevel level, FxKinds.Ambient ambient, ...)
int key(String name)
long currentWindowHandle()
boolean keyDown(long window, int key)
void swing(LivingEntity entity, InteractionHand hand)
boolean isSwinging(LivingEntity entity)
boolean jumpHeld(LivingEntity entity)
int selectedSlot(Player player)
void setFallDistance(Entity entity, float distance)
void setSelectedSlot(Player player, int slot)
void clearInvulnerability(Entity entity)
void critAnimation(Entity target, boolean magic)
void syncMotion(Entity target)
void setJumpHeld(LivingEntity entity, boolean jumping)
void tickSwing(LivingEntity entity)
void closeScreen()
Button spriteButton(int x, int y, int size, String tooltip, Runnable onPress)
void openScreen(net.minecraft.client.gui.screens.Screen screen)
```

## 3. Flashback API we depend on (`com.moulberry.flashback`, compile-only stub jar)

```java
Flashback.getDataDirectory()          // Path
Flashback.getReplayServer()           // @Nullable ReplayServer
EditorStateManager.getCurrent()       // @Nullable EditorState
EditorState.markDirty()
ReplayServer.getReplayTick(), .execute(Runnable), .goToReplayTick(int)
ReplayServer.getReplayViewers()      // List<ReplayPlayer>
KeyframeRegistry.register(KeyframeType)
FilePlayerSkin(String path)
EditorState fields: skinOverride (Map<UUID,GameProfile>), skinOverrideFromFile (Map<UUID,FilePlayerSkin>),
                  nameOverride (Map<UUID,String>)
```

## 4. Existing data model you will build on

`pdx.dev.fbactors.take` - a take is a lookup table indexed by take-tick, **never** a simulation.

```java
ActorTake:  UUID id; String name; String skinName; String dimension; int startTick;
  List<ActorFrame> frames; List<ActorEvent> events; List<ActorEquip> equipment;
  List<ActorHit> hits; List<ActorDeath> deaths; List<ActorBlockEdit> blocks;
  List<ActorVoiceLine> voices; List<ActorVitals> vitals; List<ActorMining> mining;
  List<ActorEffect> effects; List<ActorInput> inputs;
  boolean voiceEnabled; String voiceDevice; boolean voiceActivation; int voiceThreshold;
  int offsetTicks; int trimIn; int trimOut; boolean enabled; int colour;
  float MAX_HEALTH = 20f;  int DEATH_LINGER_TICKS = 30;
  int firstTick(); int lastTick(); int length(); boolean covers(int replayTick);
  void resetTrim(); ActorTake copyAs(UUID newId, String newName);
  List<ActorEvent> eventsAt(int replayTick); @Nullable ActorVitals vitalsAt(int takeTick);
  List<ActorMining> miningAt(int takeTick); byte selectedSlotAt(int replayTick);
  Map<String,ActorEffect> effectSetAt(int takeTick); int recorded(int replayTick);
```

```java
record ActorFrame(double x,double y,double z, float yRot,float xRot,float yHeadRot,
                  byte pose, byte flags)  // FLAG_SNEAKING/SPRINTING/ON_GROUND/SWINGING/FALL_FLYING
record ActorInput(int tick, float forwardAxis, float strafeAxis, boolean jump, boolean sneak,
                  boolean sprint, float yawDelta, float pitchDelta, byte selectedSlot,
                  boolean useHeld, boolean attackHeld, float attackStrength, boolean flying)
record ActorEquip(int tick, byte slot, String itemId, int count)   // itemId null/empty = empty
```

## 5. Code conventions

- Package root `pdx.dev.fbactors`, one flat subsystem package per concern.
- Records for immutable data, small `final class` + private ctor for stateless helpers
  (see `ActorItems`, `ActorSkins`).
- **Documentation style is a hard requirement.** Existing files carry long explanatory
  javadoc that argues *why* the design is what it is, including the dead ends that were
  rejected. Match it. Do not write "// getter". Explain the constraint, the alternative you
  rejected, and what breaks without this.
- `LOGGER` is `FlashbackActors.LOGGER` (`org.slf4j.Logger`), `MOD_ID` is `fbactors`.
- Prefer `Mth.clamp` / `Mth.lerp` over hand-rolled math.
- No `var` in public signatures. Explicit `@Nullable` from `org.jetbrains.annotations`.

## 6. How to register your subsystem (IMPORTANT - do not edit shared files)

**Do NOT edit `FlashbackActors.java`, `fabric.mod.json`, `fbactors.mixins.json`,
`assets/fbactors/lang/en_us.json`, or anything under `compat/`.** The lead integrates those.

Each subsystem exposes exactly one entry point:

```java
public final class YourSubsystem {
    private YourSubsystem() {}
    /** Idempotent. Called from FlashbackActors#onInitializeClient. */
    public static void install() { ... }
}
```

Your final summary must list:
1. The `install()` call the lead must add to `onInitializeClient`.
2. Any new mixin classes + `@Mixin` targets and injection points (if any).
3. Every `en_us.json` key you used, as `key` = `value` lines.
4. Any command / keybinding you want registered.

## 7. Build verification

```powershell
cd C:\Users\wired\Desktop\Project\flashback-actors
.\gradlew.bat :versions:26.3:compileJava      # fast deobf target
.\gradlew.bat :versions:1.21.1:compileJava    # mojang-mapped target
```

**Do not run `buildAll`. Do not run gradle concurrently with other agents** - another agent
may hold the Gradle lock. Write all your code first, then attempt at most one compile; if the
lock is contended, report the code as ready and note verification was skipped.

## 8. Persistence

Per-replay data lives beside Flashback's own, never inside the `.flashback` file:

```
Flashback.getDataDirectory()/fbactors/<replay-uuid>/<take-uuid>.take
```

Global libraries (emotes, gear presets, saved skins) go under `flashback/fbactors/`.

## 9. Data-file convention

`TakeIO` is the existing serializer - read `src/main/java/pdx/dev/fbactors/take/TakeIO.java` and
follow its magic/version-header + section approach for any new on-disk format you add.