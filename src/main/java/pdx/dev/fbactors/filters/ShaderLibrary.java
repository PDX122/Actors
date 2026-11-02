package pdx.dev.fbactors.filters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ShaderLibrary {
    public record Shader(String id,String name,String description,String source) {}
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final class State {
        String installation="";
        List<Shader> custom=new ArrayList<>();
        Set<String> deleted=new HashSet<>();
    }
    private final Path directory;
    private State state;
    public ShaderLibrary(Path directory,String installation) throws IOException {
        this.directory=directory;state=new State();
        Path index=directory.resolve("library.json");
        if(Files.isRegularFile(index)) {
            if(Files.size(index)>20_000_000) throw new IOException("Shader library index is too large");
            try { state=GSON.fromJson(Files.readString(index),State.class); }
            catch(RuntimeException error){throw new IOException("Could not read shader library",error);}
            if(state==null || state.custom==null || state.deleted==null || state.custom.size()>128) throw new IOException("Invalid shader library");
            var ids=new HashSet<String>();
            for(var item:state.custom) {
                if(item==null || !item.id().matches("custom_[a-f0-9]{32}") || !ids.add(item.id())) throw new IOException("Invalid custom shader ID");
                try { metadata(item.name(),item.description());CustomShaderSource.validate(item.source()); }
                catch(RuntimeException bad){throw new IOException("Invalid custom shader",bad);}
            }
        }
        if(!installation.equals(state.installation)) {
            state.installation=installation;state.deleted.clear();save();
        }
    }
    public List<Shader> shaders(){return List.copyOf(state.custom);}
    public Shader find(String id){return state.custom.stream().filter(item->item.id().equals(id)).findFirst().orElse(null);}
    public boolean deleted(String id){return state.deleted.contains(id);}
    public Shader add(String name,String description,String source) throws IOException {
        metadata(name,description);source=CustomShaderSource.validate(source);
        if(state.custom.size()>=128) throw new IllegalArgumentException("The custom library holds 128 shaders. Delete one first.");
        var shader=new Shader("custom_"+UUID.randomUUID().toString().replace("-",""),name.strip(),description.strip(),source);
        state.custom.add(shader);
        try {save();}catch(IOException error){state.custom.remove(shader);throw error;}
        return shader;
    }
    public void delete(String id,boolean bundled) throws IOException {
        var before=new ArrayList<>(state.custom);var deleted=new HashSet<>(state.deleted);
        if(bundled) state.deleted.add(id);else state.custom.removeIf(item->item.id().equals(id));
        try {save();}catch(IOException error){state.custom=before;state.deleted=deleted;throw error;}
    }
    private static void metadata(String name,String description) {
        if(name==null || name.strip().isEmpty() || name.length()>64 || name.contains("##") || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use a shader name of 1 to 64 characters.");
        if(description==null || description.strip().isEmpty() || description.length()>1000 || description.indexOf('\0')>=0)
            throw new IllegalArgumentException("Add a description of 1 to 1000 characters.");
    }
    private void save() throws IOException {
        if(!pdx.dev.fbactors.util.AtomicSave.writeJson(directory,"library.json",writer->GSON.toJson(state,writer)))
            throw new IOException("Could not save the shader library.");
    }
}
