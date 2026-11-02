package pdx.dev.fbactors.ui;

import java.util.ArrayList;
import java.util.List;

public final class ChangelogNotes {
    public record Entry(String text,boolean bullet,boolean heading) {}
    public record Release(String title,List<Entry> entries) {}
    private ChangelogNotes() {}
    public static List<Release> parse(String markdown) {
        var releases=new ArrayList<Release>();
        var entries=new ArrayList<Entry>();
        String title=null;
        var paragraph=new StringBuilder();
        boolean bullet=false;
        for(String raw:(markdown+"\n").split("\\R",-1)) {
            String line=raw.strip();
            boolean release=line.startsWith("## "),heading=line.startsWith("### "),item=line.startsWith("- ");
            if(line.isEmpty() || release || heading || item) {
                flush(entries,paragraph,bullet);
                bullet=false;
            }
            if(release) {
                if(title!=null) releases.add(new Release(title,List.copyOf(entries)));
                entries.clear(); title=plain(line.substring(3));
            } else if(title!=null && heading) entries.add(new Entry(plain(line.substring(4)),false,true));
            else if(title!=null && !line.isEmpty()) {
                if(paragraph.length()>0) paragraph.append(' ');
                paragraph.append(item?line.substring(2):line);
                if(item) bullet=true;
            }
        }
        if(title!=null) releases.add(new Release(title,List.copyOf(entries)));
        return List.copyOf(releases);
    }
    private static void flush(List<Entry> entries,StringBuilder text,boolean bullet) {
        if(text.length()>0) { entries.add(new Entry(plain(text.toString()),bullet,false)); text.setLength(0); }
    }
    private static String plain(String text) { return text.replace("**","").replace("`",""); }
}
