package pdx.dev.fbactors.filters;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShaderLibraryTest {
    static final String SOURCE="""
        #version 330 core
        uniform sampler2D InSampler;
        uniform vec2 InputSize;
        uniform float Time;
        uniform float Amount;
        in vec2 texCoord;
        out vec4 fragColor;
        void main(){vec4 c=texture(InSampler,texCoord);fragColor=vec4(mix(c.rgb,1.0-c.rgb,Amount),c.a);}
        """;
    @TempDir Path directory;
    @Test void importPersistsMetadataAndSourceAndDeleteRemovesBoth() throws Exception {
        var store=new ShaderLibrary(directory,"installation-a");
        var shader=store.add("My invert","A custom negative",SOURCE);
        var reload=new ShaderLibrary(directory,"installation-a");
        assertEquals(shader,reload.find(shader.id()));
        reload.delete(shader.id(),false);
        assertNull(new ShaderLibrary(directory,"installation-a").find(shader.id()));
        assertFalse(Files.readString(directory.resolve("library.json")).contains("My invert"));
    }
    @Test void reinstallRestoresBundledShadersWhileKeepingCustomOnes() throws Exception {
        var store=new ShaderLibrary(directory,"installation-a");
        var custom=store.add("Mine","Keep me",SOURCE);store.delete("aurora",true);
        assertTrue(new ShaderLibrary(directory,"installation-a").deleted("aurora"));
        var reinstalled=new ShaderLibrary(directory,"installation-b");
        assertFalse(reinstalled.deleted("aurora"));assertEquals(custom,reinstalled.find(custom.id()));
    }
    @Test void invalidNamesAndDescriptionsCannotCreatePartialEntries() throws Exception {
        var store=new ShaderLibrary(directory,"a");
        for(String name:new String[]{""," ","bad##id","x".repeat(65),"bad\nname"})
            assertThrows(IllegalArgumentException.class,()->store.add(name,"Description",SOURCE));
        assertThrows(IllegalArgumentException.class,()->store.add("Fine","",SOURCE));
        assertTrue(store.shaders().isEmpty());
    }
    @Test void failedSaveRollsBackImportAndDeletion() throws Exception {
        var store=new ShaderLibrary(directory,"a");var shader=store.add("Mine","Description",SOURCE);
        Path index=directory.resolve("library.json");Files.delete(index);Files.createDirectory(index);Files.writeString(index.resolve("block"),"block");
        assertThrows(java.io.IOException.class,()->store.add("Another","Description",SOURCE));
        assertEquals(1,store.shaders().size());
        assertThrows(java.io.IOException.class,()->store.delete(shader.id(),false));assertNotNull(store.find(shader.id()));
        assertThrows(java.io.IOException.class,()->store.delete("aurora",true));assertFalse(store.deleted("aurora"));
    }
    @Test void deletingBuiltinsDoesNotShiftThePreviewSheetAndCustomsAreScreenEffects() throws Exception {
        var store=new ShaderLibrary(directory,"a");store.delete("aurora",true);var shader=store.add("Mine","Description",SOURCE);
        try {
            ManagedShaders.use(store);
            assertNull(FilterCatalog.find("aurora"));assertNotNull(FilterCatalog.find(shader.id()));
            assertEquals(70,FilterCatalog.builtins().size());assertEquals("signal",FilterCatalog.builtins().getFirst().id());
            var look=new LookSettings();assertTrue(look.add(shader.id()));assertTrue(ActorEffectPlan.usesScreen(look));
            assertTrue(ActorEffectPlan.material(look).filters.isEmpty());
            store.delete(shader.id(),false);look.normalize();assertTrue(look.filters.isEmpty());
        }finally{ManagedShaders.use(null);}
    }
}
