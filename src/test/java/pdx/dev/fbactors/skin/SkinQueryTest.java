package pdx.dev.fbactors.skin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinQueryTest {
    private static final String ID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    @Test void acceptsNamesUuidsAndBrowserProfileLinks() {
        assertEquals("notch", SkinQuery.parse("  Notch  ").value());
        assertEquals(ID, SkinQuery.parse(ID.replace("-", "")).value());
        assertEquals("notch", SkinQuery.parse("https://namemc.com/profile/Notch.1").value());
        assertEquals(ID, SkinQuery.parse("https://en.namemc.com/profile/"+ID+".2?tab=skins").value());
        assertEquals("notch", SkinQuery.parse("namemc.com/profile/Notch").value());
    }
    @Test void rejectsBadInputBeforeAnyRequestCanBeStarted() {
        for (String input : new String[]{"", " ", "bad uuid", "12-not-a-uuid", "/steve", "a".repeat(17),
            "https://evil.test/profile/Notch", "https://namemc.com.evil.test/profile/Notch",
            "https://namemc.com@evil.test/profile/Notch", "https://namemc.com/skin/abcdef",
            "https://namemc.com/profile/Notch/extra", "https://namemc.com/profile/%2Fadmin", "a".repeat(3000)})
            assertThrows(IllegalArgumentException.class, () -> SkinQuery.parse(input), input);
        assertThrows(IllegalArgumentException.class, () -> SkinQuery.parse(null));
    }
}
