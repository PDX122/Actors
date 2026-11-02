package pdx.dev.fbactors.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;

class ChangelogNotesTest {
    @Test void groupsReleasesAndJoinsWrappedBulletText() {
        var notes=ChangelogNotes.parse("# Changelog\nIntro\n\n## 2.0\n\n- Added **camera**\n  previews with `skins`.\n- Fixed 100% scaling.\n\n### Fixes\n\nA paragraph\ncontinued.\n\n## 1.0\n\n- Original release.");
        assertEquals(2,notes.size());
        assertEquals("2.0",notes.getFirst().title());
        var entries=notes.getFirst().entries();
        assertEquals(4,entries.size());
        assertEquals("Added camera previews with skins.",entries.getFirst().text());
        assertTrue(entries.getFirst().bullet());
        assertEquals("Fixed 100% scaling.",entries.get(1).text());
        assertTrue(entries.get(2).heading());
        assertEquals("A paragraph continued.",entries.get(3).text());
        assertFalse(entries.get(3).bullet());
        assertEquals("Original release.",notes.get(1).entries().getFirst().text());
    }
    @Test void handlesEmptyNotesCrLfAndMissingFinalNewline() {
        assertTrue(ChangelogNotes.parse("").isEmpty());
        var notes=ChangelogNotes.parse("## Release\r\n- One\r\n  two");
        assertEquals("One two",notes.getFirst().entries().getFirst().text());
    }
    @Test void actualBundledNotesIncludeCurrentFeaturesAndNoEmDashes() throws Exception {
        try(var input=getClass().getResourceAsStream("/assets/fbactors/changelog.md")) {
            assertNotNull(input,"The canonical changelog must be bundled in the mod");
            String text=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            assertFalse(text.contains("\u2014"));
            var notes=ChangelogNotes.parse(text);
            assertEquals("0.11.1 (in development)",notes.getFirst().title());
            assertTrue(text.contains("Random"));
            assertTrue(text.contains("Copy log"));
            assertTrue(text.contains("UI themes"));
            assertTrue(text.contains("16-bit vertex limit"));
            assertTrue(notes.stream().anyMatch(release -> release.title().equals("0.10.11")));
        }
    }
}
