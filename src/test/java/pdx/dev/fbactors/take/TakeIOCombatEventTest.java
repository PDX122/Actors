package pdx.dev.fbactors.take;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TakeIOCombatEventTest {
    @Test void projectilePayloadSurvivesSavingAndRetiming(@TempDir Path dir) throws Exception {
        ActorTake take = new ActorTake(UUID.randomUUID(), "Arrows", "minecraft:overworld", 100);
        for (int i = 0; i < 6; i++) take.frames.add(new ActorFrame(0, 64, 0, 0, 0, 0, (byte)0, (byte)0));
        String payload = "{damage:5.0d,crit:1b,PierceLevel:3b,weapon:{id:\"minecraft:crossbow\"}}";
        take.shots.add(new ActorShot(4, 0, 0, 1, 3.15f, "minecraft:crossbow", null,
            "minecraft:spectral_arrow", payload));
        Path file = dir.resolve("projectile.take");
        TakeIO.write(file, take);
        ActorTake loaded = TakeIO.read(file);
        assertEquals(take.shots, loaded.shots);
        ActorShot clipped = ClipEditor.splitAt(loaded, 3)[1].shots.getFirst();
        assertEquals(1, clipped.tick());
        assertEquals("minecraft:spectral_arrow", clipped.entityId());
        assertEquals(payload, clipped.entityNbt());
    }

    @Test void attackConditionsSurviveSavingAndClipEdits(@TempDir Path dir) throws Exception {
        ActorTake take = new ActorTake(UUID.randomUUID(), "PvP", "minecraft:overworld", 100);
        for (int i = 0; i < 6; i++) take.frames.add(new ActorFrame(0, 70-i, 0,
            0, 0, 0, (byte) 0, (byte) 0));
        ActorEvent event = new ActorEvent(104, ActorEvent.Type.ATTACK, UUID.randomUUID(),
            0.925f, 12.75, true);
        take.events.add(event);
        ActorEvent swing = new ActorEvent(105, ActorEvent.Type.SWING_OFF, null,
            -1.0f, -1.0, false, "STAB", 12);
        take.events.add(swing);
        Path file = dir.resolve("combat.take");
        TakeIO.write(file, take);
        ActorTake loaded = TakeIO.read(file);
        assertEquals(event, loaded.events.getFirst());
        assertEquals(swing, loaded.events.get(1));
        ActorTake right = ClipEditor.splitAt(loaded, 3)[1];
        assertEquals(event.attackStrength(), right.events.getFirst().attackStrength());
        assertEquals(event.fallDistance(), right.events.getFirst().fallDistance());
        assertTrue(right.events.getFirst().sprinting());
        assertEquals("STAB", right.events.get(1).swingAnimation());
        assertEquals(12, right.events.get(1).swingDuration());
    }

    @Test void legacyEventsUseTheRecordedInputFallback() {
        ActorEvent legacy = ActorEvent.attack(10, UUID.randomUUID());
        assertEquals(-1.0f, legacy.attackStrength());
        assertEquals(-1.0, legacy.fallDistance());
    }
}
