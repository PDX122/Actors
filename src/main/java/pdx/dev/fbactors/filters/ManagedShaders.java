package pdx.dev.fbactors.filters;

import java.nio.file.*;
import java.util.List;
import pdx.dev.fbactors.FlashbackActors;

public final class ManagedShaders {
    private static ShaderLibrary library;
    public static String status="";
    private ManagedShaders() {}
    static void use(ShaderLibrary value){library=value;}
    public static void install() {
        try {
            var mod=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("fbactors").orElseThrow();
            String stamp=mod.getMetadata().getVersion().getFriendlyString();
            for(var path:mod.getOrigin().getPaths()) if(Files.isRegularFile(path)) {
                var attrs=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class);
                stamp+="|"+path.toAbsolutePath()+"|"+attrs.fileKey()+"|"+attrs.creationTime()+"|"+attrs.lastModifiedTime()+"|"+attrs.size();
            }
            library=new ShaderLibrary(com.moulberry.flashback.Flashback.getDataDirectory().resolve("fbactors/shaders"),stamp);
        }catch(Exception error){status="Could not load the shader library.";FlashbackActors.LOGGER.warn(status,error);}
    }
    public static boolean custom(String id){return library!=null && library.find(id)!=null;}
    public static boolean visible(String id){return library==null || !library.deleted(id);}
    public static List<FilterCatalog.Entry> entries(){return library==null?List.of():library.shaders().stream()
        .map(item->new FilterCatalog.Entry(item.id(),item.name(),"Custom","Imported GLSL",item.description())).toList();}
    public static String source(String id){var item=library==null?null:library.find(id);return item==null?null:item.source();}
    public static List<FilterCatalog.Param> params(String id){String source=source(id);return source==null?List.of():CustomShaderSource.params(source);}
    public static String importShader(Path path,String name,String description) throws java.io.IOException {
        if(library==null) throw new IllegalStateException("The shader library is unavailable.");
        String file=path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if(!(file.endsWith(".fsh")||file.endsWith(".frag")||file.endsWith(".glsl")) || Files.size(path)>CustomShaderSource.MAX_BYTES)
            throw new IllegalArgumentException("Choose a .fsh, .frag or .glsl fragment shader smaller than 64 KiB.");
        String source=CustomShaderSource.validate(Files.readString(path));
        FilterGpuPipeline.validateCustom(source);
        var shader=library.add(name,description,source);
        Looks.requestReload();status="Imported "+shader.name()+".";return shader.id();
    }
    public static void delete(String id) throws java.io.IOException {
        if(library==null) throw new IllegalStateException("The shader library is unavailable.");
        boolean bundled=FilterCatalog.builtins().stream().anyMatch(item->item.id().equals(id));
        if(!bundled && !custom(id)) throw new IllegalArgumentException("That shader is no longer available.");
        library.delete(id,bundled);
        Looks.settings().normalize();Effects.timeline().normalize();Looks.changed();Effects.changed();
        Looks.requestReload();status="Shader deleted.";
    }
}
