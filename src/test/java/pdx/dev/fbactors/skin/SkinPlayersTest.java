package pdx.dev.fbactors.skin;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinPlayersTest {
    @Test void exclusionSurvivesTheServerChangingItsPlayerList() {
        var ids=java.util.List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var live=new java.util.ArrayList<>(ids);
        var excluded=new java.util.HashSet<UUID>();
        assertDoesNotThrow(() -> SkinPlayers.exclude(excluded,live,id -> { live.clear(); return id; }));
        assertEquals(Set.copyOf(ids),excluded);
    }
    @Test void excludesReplayCameraAndActorClonesByIdentity() {
        UUID viewer=UUID.randomUUID(), clone=UUID.randomUUID(), player=UUID.randomUUID();
        var excluded=Set.of(viewer,clone);
        assertFalse(SkinPlayers.visible(viewer,"NormalName",excluded,""));
        assertFalse(SkinPlayers.visible(clone,"AnotherActor",excluded,""));
        assertTrue(SkinPlayers.visible(player,"PDXJS",excluded,""));
    }
    @Test void hidesSyntheticViewerLabelEvenWhenNoViewerEntityIsAvailable() {
        UUID id=UUID.randomUUID();
        assertFalse(SkinPlayers.visible(id," Replay Viewer ",Set.of(),""));
        assertFalse(SkinPlayers.visible(id,"replay viewer",Set.of(),"replay"));
        assertFalse(SkinPlayers.visible(id,"",Set.of(),""));
        assertFalse(SkinPlayers.visible(null,"PDXJS",Set.of(),""));
    }
    @Test void filtersRealAccountsWithoutCaseSensitivity() {
        UUID id=UUID.randomUUID();
        assertTrue(SkinPlayers.visible(id,"PDXJS",Set.of()," pdx "));
        assertFalse(SkinPlayers.visible(id,"PDXJS",Set.of(),"Notch"));
    }
}
