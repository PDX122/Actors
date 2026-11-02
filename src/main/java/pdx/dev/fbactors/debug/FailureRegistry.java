package pdx.dev.fbactors.debug;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;

public final class FailureRegistry {
    public record Failure(long sequence,String feature,Instant time,String thread,String summary,String stack) {}
    private final Set<String> stopped=new HashSet<>();
    private final Deque<Failure> history=new ArrayDeque<>();
    private final BiConsumer<String,Throwable> logger;
    private long sequence;

    public FailureRegistry(BiConsumer<String,Throwable> logger) { this.logger=logger; }
    public boolean run(String feature,Runnable action) {
        synchronized(this) { if(stopped.contains(feature)) return false; }
        try { action.run(); return true; }
        catch(Exception | LinkageError | AssertionError error) { report(feature,error); return false; }
    }
    public void report(String feature,Throwable error) {
        synchronized(this) {
            if(!stopped.add(feature)) return;
            var text=new StringWriter(); error.printStackTrace(new PrintWriter(text));
            String summary=error.getClass().getSimpleName()+": "+Objects.toString(error.getMessage(),"No message provided");
            history.addFirst(new Failure(++sequence,feature,Instant.now(),Thread.currentThread().getName(),limit(summary,1000),limit(text.toString(),24_000)));
            while(history.size()>12) history.removeLast();
        }

        try { logger.accept(feature,error); } catch(Exception | LinkageError ignored) {}
    }
    private static String limit(String value,int length) { return value.length()>length?value.substring(0,length)+"\n[truncated]":value; }
    public synchronized boolean stopped(String feature) { return stopped.contains(feature); }
    public synchronized void retry(String feature) { stopped.remove(feature); }
    public synchronized List<Failure> history() { return List.copyOf(history); }
    public synchronized long sequence() { return sequence; }
    public synchronized String reportText() {
        var text=new StringBuilder();
        for(var f:history) text.append("\n--- ").append(f.feature()).append(" | ").append(f.time()).append(" | ").append(f.thread())
            .append(" | ").append(stopped.contains(f.feature())?"stopped":"retry enabled").append(" ---\n").append(f.stack());
        return text.toString();
    }
}
