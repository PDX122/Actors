package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class ChangelogWindow {
    private static final ImBoolean OPEN=new ImBoolean(false);
    private static final ImString SEARCH=new ImString(256);
    private static List<ChangelogNotes.Release> notes;
    private static int selected;
    private static String lastSearch="";
    private ChangelogWindow() {}
    public static void open() { OPEN.set(true); }
    private static List<ChangelogNotes.Release> load() {
        if(notes!=null) return notes;
        try(var input=ChangelogWindow.class.getResourceAsStream("/assets/fbactors/changelog.md")) {
            if(input==null) throw new IOException("Bundled changelog is missing");
            byte[] bytes=input.readNBytes(262_145);
            if(bytes.length>262_144) throw new IOException("Bundled changelog exceeds 256 KB");
            notes=ChangelogNotes.parse(new String(bytes,StandardCharsets.UTF_8));
            return notes;
        } catch(IOException e) { throw new IllegalStateException("Could not load the release notes",e); }
    }
    public static void render() {
        if(!OPEN.get()) return;
        var releases=load();
        var viewport=ImGui.getMainViewport();
        float width=Math.max(1,viewport.getWorkSizeX()-24),height=Math.max(1,viewport.getWorkSizeY()-24);
        ImGui.setNextWindowSize(Math.min(680,width),Math.min(590,height),ImGuiCond.Appearing);
        ImGui.setNextWindowSizeConstraints(Math.min(360,width),Math.min(340,height),width,height);
        SkinPickerChrome.push();
        boolean visible=ImGui.begin("Changelog###FbActorsChangelog",OPEN);
        try {
            if(!visible) return;
            ImGui.textUnformatted("Flashback Actors");
            if(releases.isEmpty()) ImGui.textWrapped("No release notes are bundled with this build.");
            else {
                selected=Math.max(0,Math.min(selected,releases.size()-1));
                ImGui.setNextItemWidth(-1);
                if(ImGui.beginCombo("##ChangelogRelease",releases.get(selected).title())) {
                    try {
                        for(int i=0;i<releases.size();i++) if(ImGui.selectable(releases.get(i).title()+"##"+i,selected==i)) selected=i;
                    } finally { ImGui.endCombo(); }
                }
                ImGui.setNextItemWidth(-1);
                ImGui.inputTextWithHint("##ChangelogSearch","Search this release",SEARCH);
                ImGui.separator();
                boolean contents=ImGui.beginChild("##ChangelogNotes"+selected,0,Math.max(1,ImGui.getContentRegionAvailY()-48),false);
                try {
                    if(!lastSearch.equals(SEARCH.get())) { ImGui.setScrollY(0); lastSearch=SEARCH.get(); }
                    if(contents) {
                        String query=SEARCH.get().strip().toLowerCase(Locale.ROOT);
                        boolean matched=false;
                        for(var entry:releases.get(selected).entries()) {
                            if(!query.isEmpty() && !entry.text().toLowerCase(Locale.ROOT).contains(query)) continue;
                            matched=true;
                            ImGui.pushTextWrapPos(0);
                            try {
                                if(entry.heading()) { ImGui.spacing(); ImGui.separator(); ImGui.textUnformatted(entry.text()); }
                                else if(entry.bullet()) {
                                    ImGui.bullet(); ImGui.sameLine(); ImGui.beginGroup();
                                    try { ImGui.textUnformatted(entry.text()); } finally { ImGui.endGroup(); }
                                } else ImGui.textUnformatted(entry.text());
                            } finally { ImGui.popTextWrapPos(); }
                            ImGui.spacing();
                        }
                        if(!matched) ImGui.textWrapped("No matching changes in this release.");
                    }
                } finally { ImGui.endChild(); }
            }
            ImGui.separator();
            if(SkinPickerChrome.button("Done",true,100)) OPEN.set(false);
        } finally { ImGui.end(); SkinPickerChrome.pop(); }
    }
}
