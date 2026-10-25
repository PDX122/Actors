package pdx.dev.fbactors.menu;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayMenuModelTest {
    @Test void searchIgnoresCaseAndOuterWhitespace() {
        var a = new ReplayBrowser.Entry(ReplayBrowser.Source.FLASHBACK, Path.of("a"), "Forest Chase", 400);
        var b = new ReplayBrowser.Entry(ReplayBrowser.Source.FLASHBACK, Path.of("b"), "Studio", 800);
        assertEquals(List.of(a), ReplayMenuModel.filter(List.of(a, b), " CHASE "));
        assertEquals(List.of(a, b), ReplayMenuModel.filter(List.of(a, b), ""));
        assertTrue(ReplayMenuModel.filter(List.of(a, b), "missing").isEmpty());
    }

    @Test void smallerWindowsShowFewerRows() {
        assertEquals(2, ReplayMenuModel.rows(240));
        assertEquals(4, ReplayMenuModel.rows(300));
        assertEquals(8, ReplayMenuModel.rows(900));
        assertEquals(1, ReplayMenuModel.rows(120));
    }

    @Test void paginationHandlesEmptyAndPartialPages() {
        assertEquals(1, ReplayMenuModel.pages(0, 4));
        assertEquals(1, ReplayMenuModel.pages(4, 4));
        assertEquals(2, ReplayMenuModel.pages(5, 4));
        assertEquals(268435456, ReplayMenuModel.pages(Integer.MAX_VALUE, 8));
    }

    @Test void durationUsesMinutesAndSeconds() {
        assertEquals("0:00", ReplayMenuModel.duration(-1));
        assertEquals("0:09", ReplayMenuModel.duration(9));
        assertEquals("2:05", ReplayMenuModel.duration(125));
    }
}
