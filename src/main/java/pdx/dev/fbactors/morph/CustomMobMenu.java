package pdx.dev.fbactors.morph;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImString;
import pdx.dev.fbactors.ui.SkinPickerChrome;
import java.nio.file.Path;

public final class CustomMobMenu {
    private static final ImString NAME=new ImString(256),GEOMETRY=new ImString(4096),ANIMATION=new ImString(4096),TEXTURE=new ImString(4096);
    private static java.util.concurrent.CompletableFuture<String> dialog;
    private static ImString dialogField;
    private static boolean requested;
    private static String error="",imported;
    private CustomMobMenu() {}
    public static void open(){requested=true;error="";NAME.set("");GEOMETRY.set("");ANIMATION.set("");TEXTURE.set("");}
    public static String takeImported(){String id=imported;imported=null;return id;}
    public static void render() {
        if(requested){ImGui.openPopup("Import custom mob");requested=false;}
        ImGui.setNextWindowSize(500,0,imgui.moulberry90.flag.ImGuiCond.Appearing);
        if(ImGui.beginPopupModal("Import custom mob",imgui.moulberry90.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            try {
                ImGui.textWrapped("Blockbench / GeckoLib model with Box UV and numeric keyframe animations.");
                ImGui.setNextItemWidth(460);ImGui.inputTextWithHint("##CustomMobName","Model name",NAME);
                file("Geometry .geo.json",GEOMETRY,"json");
                file("Animation JSON (optional)",ANIMATION,"json");
                file("Texture PNG",TEXTURE,"png");
                if(dialog!=null&&dialog.isDone()) {
                    try{String path=dialog.getNow(null);if(path!=null)dialogField.set(path);}
                    catch(RuntimeException failure){error="File dialog failed. Paste the file path instead.";}
                    dialog=null;dialogField=null;
                }
                if(!error.isEmpty())ImGui.textWrapped(error);
                ImGui.beginDisabled(NAME.get().isBlank()||GEOMETRY.get().isBlank()||TEXTURE.get().isBlank());
                try {if(SkinPickerChrome.button("Import model",true,200)) {
                    try {
                        var asset=CustomMobs.importFiles(Path.of(GEOMETRY.get().strip()),ANIMATION.get().isBlank()?null:Path.of(ANIMATION.get().strip()),Path.of(TEXTURE.get().strip()),NAME.get());
                        imported=asset.id();ImGui.closeCurrentPopup();
                    }catch(Exception failure){error=String.valueOf(failure.getMessage());}
                }}finally{ImGui.endDisabled();}
                ImGui.sameLine();if(SkinPickerChrome.button("Cancel",false,100)){dialog=null;ImGui.closeCurrentPopup();}
            }finally{ImGui.endPopup();}
        }
    }
    private static void file(String label,ImString value,String extension) {
        ImGui.textDisabled(label);ImGui.setNextItemWidth(350);ImGui.inputText("##Path"+label,value);ImGui.sameLine();
        ImGui.beginDisabled(dialog!=null);
        try{if(SkinPickerChrome.button("Browse##"+label,false,100)) {
            try{dialog=pdx.dev.fbactors.compat.Compat.openModelFile(CustomMobs.directory().toString(),extension);dialogField=value;}
            catch(RuntimeException|LinkageError failure){error="File dialogs are unavailable. Paste the file paths above.";}
        }}finally{ImGui.endDisabled();}
    }
}
