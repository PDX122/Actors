package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImGui;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class PhosphorIcons {
    private static final Map<String,int[]> ICONS = load();
    private PhosphorIcons() {}
    private static Map<String,int[]> load() {
        try(var stream=PhosphorIcons.class.getResourceAsStream("/assets/fbactors/ui/phosphor/icons.json")) {
            if(stream==null) throw new IllegalStateException("Missing Phosphor icons");
            var root=com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            var icons=new java.util.HashMap<String,int[]>();
            for(var entry:root.getAsJsonObject("icons").entrySet()) {
                var data=entry.getValue().getAsJsonArray();var spans=new int[data.size()];
                for(int i=0;i<spans.length;i++) spans[i]=data.get(i).getAsInt();
                icons.put(entry.getKey(),spans);
            }
            return Map.copyOf(icons);
        } catch(java.io.IOException error) { throw new IllegalStateException("Could not read Phosphor icons",error); }
    }
    public static String forLabel(String label) {
        return switch(label.split("##",2)[0]) {
            case "Players" -> "users";
            case "Saved" -> "bookmark-simple";
            case "Files" -> "folder";
            case "Find" -> "magnifying-glass";
            case "Front" -> "user-focus";
            case "Back" -> "arrow-u-up-left";
            case "Walk" -> "person-simple-walk";
            case "Save skin" -> "floppy-disk";
            case "More..." -> "dots-three";
            case "Apply skin" -> "check";
            case "Import PNG", "Import shader", "Choose file" -> "upload-simple";
            case "Delete shader" -> "trash";
            case "Add effect" -> "plus";
            case "Recording" -> "record";
            case "Playback" -> "play";
            case "Boats" -> "boat";
            case "World edits" -> "cube";
            case "Camera" -> "camera";
            case "Bake" -> "film-strip";
            case "Handling" -> "steering-wheel";
            case "Audio" -> "microphone";
            case "Captions" -> "subtitles";
            case "Interface" -> "sliders-horizontal";
            case "Looks gallery" -> "palette";
            default -> null;
        };
    }
    public static boolean contains(String name) { return ICONS.containsKey(name); }
    public static void draw(String name,float x,float y,float size,int tint) {
        var spans=ICONS.get(name);if(spans==null) return;
        float scale=size/24;
        var draw=ImGui.getWindowDrawList();
        for(int i=0;i<spans.length;i+=4) {
            int alpha=spans[i+3]*(tint>>>24)/255;
            draw.addRectFilled(x+spans[i]*scale,y+spans[i+1]*scale,x+(spans[i]+spans[i+2])*scale,
                y+(spans[i+1]+1)*scale,(tint&0xFFFFFF)|(alpha<<24));
        }
    }
}
