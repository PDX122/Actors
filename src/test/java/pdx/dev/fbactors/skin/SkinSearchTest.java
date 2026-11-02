package pdx.dev.fbactors.skin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinSearchTest {
    @Test void unavailableSkinLoaderShowsAnErrorWithoutBreakingTheUi() {
        var search = new SkinSearch<String>(id -> { throw new NoClassDefFoundError("loader unavailable"); });
        assertDoesNotThrow(() -> search.search("Notch"));
        assertFalse(search.loading()); assertNull(search.result()); assertNotNull(search.error());
    }
    @Test void duplicateClicksDoNotQueueRepeatedRequests() {
        var calls = new AtomicInteger(); var pending = new CompletableFuture<String>();
        var search = new SkinSearch<String>(id -> { calls.incrementAndGet(); return pending; });
        search.search("Notch"); search.search(" notch ");
        assertEquals(1, calls.get()); assertTrue(search.loading());
        assertNull(search.result()); pending.complete("skin"); search.poll();
        assertEquals("skin", search.result()); assertFalse(search.loading());
    }
    @Test void switchingAccountOrClosingCancelsOldResults() {
        var old = new CompletableFuture<String>(); var fresh = new CompletableFuture<String>();
        var search = new SkinSearch<String>(id -> id.value().equals("alpha") ? old : fresh);
        search.search("alpha"); search.search("beta");
        assertTrue(old.isCancelled()); assertFalse(old.complete("wrong skin"));
        fresh.complete("beta skin"); search.poll();
        assertEquals("beta skin", search.result()); assertEquals("beta", search.source().value());
        search.clear(); assertNull(search.result()); assertNull(search.source());
    }
    @Test void validationAndNetworkFailuresRemainVisibleErrors() {
        var requests = new AtomicInteger();
        var search = new SkinSearch<String>(id -> { requests.incrementAndGet(); return CompletableFuture.failedFuture(new TimeoutException()); });
        search.search("broken-uuid"); assertEquals(0, requests.get()); assertNotNull(search.error());
        search.search("validname"); assertDoesNotThrow(search::poll);
        assertNull(search.result()); assertTrue(search.error().contains("timed out"));
        var missing = new SkinSearch<String>(id -> CompletableFuture.completedFuture(null));
        missing.search("unknown"); missing.poll(); assertNotNull(missing.error()); assertNull(missing.result());
    }
    @Test void changingInputClearsAnAlreadyLoadedSkin() {
        var search = new SkinSearch<String>(id -> CompletableFuture.completedFuture("old skin"));
        search.search("alpha"); search.poll(); assertNotNull(search.result());
        search.search("bad input"); assertNull(search.result()); assertNotNull(search.error());
    }
}
