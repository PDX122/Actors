package pdx.dev.fbactors.util;

import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RetryingJsonSaves<T> {
    private static final Logger LOGGER=LoggerFactory.getLogger("fbactors");
    private static final Gson GSON=new Gson();
    private final String fileName;
    private final Map<UUID,T> pending=new LinkedHashMap<>();
    private final Set<UUID> reported=new HashSet<>();
    public RetryingJsonSaves(String fileName) { this.fileName=fileName; }
    public void put(UUID owner,T value) { if(owner!=null && value!=null) pending.put(owner,value); }
    public T pending(UUID owner) { return pending.get(owner); }
    public boolean isEmpty() { return pending.isEmpty(); }
    public void flush(Function<UUID,Path> directory) {
        var iterator=pending.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            boolean saved;
            try { saved=AtomicSave.writeJson(directory.apply(entry.getKey()),fileName,w -> GSON.toJson(entry.getValue(),w)); }
            catch(RuntimeException error) { saved=false; }
            if(saved) { reported.remove(entry.getKey()); iterator.remove(); }
            else if(reported.add(entry.getKey())) LOGGER.warn("Could not save {} for replay {}; keeping pending changes for retry",fileName,entry.getKey());
        }
    }
}
