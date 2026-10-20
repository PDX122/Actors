package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.phys.Vec3;


class HostHitsTest {

    private static final UUID HOST = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static ActorHit hit(int tick, float damage) {
        return new ActorHit(tick, damage, 1.0, 0.0, 1.0f, HOST,
            new Vec3(0.5, 0.1, 0.0), ActorHit.CRIT_NONE);
    }

    @Test
    void recordHitKeepsTickOrder() {
        HostHits hits = new HostHits();
        hits.recordHit(HOST, hit(50, 4.0f));
        hits.recordHit(HOST, hit(10, 3.0f));
        hits.recordHit(HOST, hit(30, 2.0f));

        assertNotNull(hits.hitAt(HOST, 10));
        assertNotNull(hits.hitAt(HOST, 30));
        assertNotNull(hits.hitAt(HOST, 50));
        assertNull(hits.hitAt(HOST, 20));
        assertTrue(hits.hasHitAt(HOST, 30));
        assertFalse(hits.hasHitAt(HOST, 31));
        assertFalse(hits.hasHitAt(OTHER, 30));
    }

    @Test
    void recordHitReplacesSameTick() {
        HostHits hits = new HostHits();
        hits.recordHit(HOST, hit(10, 4.0f));
        hits.recordHit(HOST, hit(10, 6.0f));

        assertEquals(1, hits.hitsFor(HOST).size());
        assertEquals(6.0f, hits.hitAt(HOST, 10).damage());
        assertEquals(14.0f, hits.healthAt(HOST, 10, 20.0f), 0.001f);
    }

    @Test
    void recordHitKeepsSameTickDifferentTarget() {
        HostHits hits = new HostHits();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        hits.recordHit(HOST, new ActorHit(10, 4.0f, 1.0, 0.0, 1.0f, first, null,
            ActorHit.CRIT_NONE));
        hits.recordHit(HOST, new ActorHit(10, 6.0f, 1.0, 0.0, 1.0f, second, null,
            ActorHit.CRIT_NONE));

        assertEquals(2, hits.hitsFor(HOST).size());
        assertEquals(10.0f, hits.healthAt(HOST, 10, 20.0f), 0.001f);
    }

    @Test
    void sortHitsKeepsSameTickDifferentTarget() {
        HostHits hits = new HostHits();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        hits.recordHit(HOST, new ActorHit(10, 4.0f, 1.0, 0.0, 1.0f, first, null,
            ActorHit.CRIT_NONE));
        hits.recordHit(HOST, new ActorHit(10, 6.0f, 1.0, 0.0, 1.0f, second, null,
            ActorHit.CRIT_NONE));
        hits.sortHits();

        assertEquals(2, hits.hitsFor(HOST).size());
    }

    @Test
    void healthAtIsCumulativeAndClamped() {
        HostHits hits = new HostHits();
        hits.recordHit(HOST, hit(10, 4.0f));
        hits.recordHit(HOST, hit(20, 8.0f));
        hits.recordHit(HOST, hit(30, 12.0f));

        assertEquals(20.0f, hits.healthAt(HOST, 5, 20.0f), 0.001f);
        assertEquals(16.0f, hits.healthAt(HOST, 10, 20.0f), 0.001f);
        assertEquals(8.0f, hits.healthAt(HOST, 20, 20.0f), 0.001f);
        assertEquals(0.0f, hits.healthAt(HOST, 30, 20.0f), 0.001f);
        assertEquals(0.0f, hits.healthAt(HOST, 100, 20.0f), 0.001f);

        assertEquals(18.0f, hits.healthAt(HOST, 20, 30.0f), 0.001f);
    }

    @Test
    void deathTickIsFirstLethalTick() {
        HostHits hits = new HostHits();
        hits.recordHit(HOST, hit(10, 4.0f));
        hits.recordHit(HOST, hit(20, 8.0f));
        hits.recordHit(HOST, hit(30, 12.0f));

        assertEquals(30, hits.deathTick(HOST, 20.0f));
        assertEquals(-1, hits.deathTick(HOST, 30.0f));
        assertEquals(-1, hits.deathTick(OTHER, 20.0f));

        HostHits empty = new HostHits();
        assertTrue(empty.isEmpty());
        assertEquals(-1, empty.deathTick(HOST, 20.0f));
    }

    @Test
    void healthAtIsPureAcrossScrubs() {


        HostHits hits = new HostHits();
        hits.recordHit(HOST, hit(10, 15.0f));
        hits.recordHit(HOST, hit(20, 10.0f));

        assertEquals(hits.healthAt(HOST, 25, 20.0f), hits.healthAt(HOST, 25, 20.0f));
        assertEquals(20, hits.deathTick(HOST, 20.0f));
        assertEquals(0.0f, hits.healthAt(HOST, 20, 20.0f), 0.001f);
        assertEquals(5.0f, hits.healthAt(HOST, 15, 20.0f), 0.001f);
    }

    @Test
    void victimsTrackedPerReplay() {
        HostHits hits = new HostHits();
        hits.recordHit(HOST, hit(10, 4.0f));
        hits.recordHit(OTHER, hit(10, 4.0f));

        assertEquals(2, hits.victims().size());
        assertEquals(16.0f, hits.healthAt(OTHER, 10, 20.0f), 0.001f);
    }

    @Test
    void storeRoundTrips(@TempDir Path dir) throws Exception {
        HostHits hits = new HostHits();
        hits.recordHit(HOST, hit(10, 4.0f));
        hits.recordHit(HOST, new ActorHit(20, 6.0f, 0.0, 1.0, 0.8f, HOST,
            null, (byte) (ActorHit.CRIT_HIT | ActorHit.CRIT_MAGIC)));
        hits.recordHit(OTHER, hit(15, 2.0f));

        Path file = dir.resolve("host-hits");
        HostStore.write(file, hits);
        HostHits back = HostStore.load(file);

        assertEquals(2, back.victims().size());
        assertEquals(4.0f, back.hitAt(HOST, 10).damage());
        assertNotNull(back.hitAt(HOST, 10).impulse());
        assertEquals(HOST, back.hitAt(HOST, 10).target());
        ActorHit legacy = back.hitAt(HOST, 20);
        assertNull(legacy.impulse());
        assertTrue(legacy.isCritical());
        assertTrue(legacy.isMagicCritical());
        assertEquals(0.8f, legacy.attackStrength(), 0.001f);
        assertEquals(2.0f, back.hitAt(OTHER, 15).damage());
        assertEquals(10.0f, back.healthAt(HOST, 20, 20.0f), 0.001f);
        assertEquals(20, back.deathTick(HOST, 10.0f));
    }

    @Test
    void storeAbsentReadsEmpty(@TempDir Path dir) {


        HostHits hits = HostStore.load(dir.resolve("host-hits"));
        assertTrue(hits.isEmpty());
        assertTrue(hits.victims().isEmpty());
    }

    @Test
    void storeCorruptReadsEmpty(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("host-hits");
        Files.write(file, new byte[] {1, 2, 3, 4});
        assertTrue(HostStore.load(file).isEmpty());

        Files.write(file, "not a sidecar".getBytes());
        assertTrue(HostStore.load(file).isEmpty());
    }
}

