package pdx.dev.fbactors.filters;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImString;
import pdx.dev.fbactors.ui.SkinPickerChrome;

public final class ShaderLibraryMenu {
    private static final ImString NAME=new ImString(256),DESCRIPTION=new ImString(4096),PATH=new ImString(4096);
    private static java.util.concurrent.CompletableFuture<String> dialog;
    private static String error="",deleting;
    private static String imported;
    private static boolean requestImport,requestDelete;
    private ShaderLibraryMenu() {}
    public static void openImport(){error="";NAME.set("");DESCRIPTION.set("");PATH.set("");dialog=null;requestImport=true;}
    public static void confirmDelete(String id){deleting=id;error="";requestDelete=true;}
    public static String takeImported(){String id=imported;imported=null;return id;}
    public static void render() {
        if(requestImport){ImGui.openPopup("Import GLSL shader");requestImport=false;}
        if(requestDelete){ImGui.openPopup("Delete shader?");requestDelete=false;}
        ImGui.setNextWindowSize(460,0,imgui.moulberry90.flag.ImGuiCond.Appearing);
        if(ImGui.beginPopupModal("Import GLSL shader",imgui.moulberry90.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            try {
                ImGui.textWrapped("Import a GLSL 330 fragment effect for the scene or an actor.");
                ImGui.setNextItemWidth(420);ImGui.inputTextWithHint("##CustomShaderPath","Choose a .fsh, .frag or .glsl file",PATH);
                if(SkinPickerChrome.button("Choose file",false,140) && dialog==null) {
                    try {dialog=pdx.dev.fbactors.compat.Compat.openShaderFile(com.moulberry.flashback.Flashback.getDataDirectory().toString());}
                    catch(RuntimeException|LinkageError failure){error="File dialog unavailable. Paste the shader path above.";}
                }
                if(dialog!=null && dialog.isDone()) {
                    try {String path=dialog.getNow(null);if(path!=null) PATH.set(path);}
                    catch(RuntimeException failure){error="Could not choose that file. Paste its path above.";}
                    dialog=null;
                }
                ImGui.setNextItemWidth(420);ImGui.inputTextWithHint("##CustomShaderName","Shader name",NAME);
                ImGui.textDisabled("Description");ImGui.inputTextMultiline("##CustomShaderDescription",DESCRIPTION,420,84);
                if(!error.isEmpty()) ImGui.textWrapped(error);
                ImGui.beginDisabled(PATH.get().isBlank()||NAME.get().isBlank()||DESCRIPTION.get().isBlank());
                try {
                    if(SkinPickerChrome.button("Import shader",true,180)) {
                        try {imported=ManagedShaders.importShader(java.nio.file.Path.of(PATH.get().strip()),NAME.get(),DESCRIPTION.get());ImGui.closeCurrentPopup();}
                        catch(Exception|LinkageError failure){error=message(failure);}
                    }
                } finally {ImGui.endDisabled();}
                ImGui.sameLine();if(SkinPickerChrome.button("Cancel",false,100)){dialog=null;ImGui.closeCurrentPopup();}
            }finally{ImGui.endPopup();}
        }
        ImGui.setNextWindowSize(420,0,imgui.moulberry90.flag.ImGuiCond.Appearing);
        if(ImGui.beginPopupModal("Delete shader?",imgui.moulberry90.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            try {
                var item=FilterCatalog.find(deleting);
                ImGui.textUnformatted(item==null?"Shader no longer available":item.name());
                ImGui.textWrapped(ManagedShaders.custom(deleting)?"Delete this imported shader and its saved code?"
                    :"This shader comes with the mod. You will have to reinstall the mod to get it back.");
                ImGui.textWrapped("Effects that used this shader will stop using it.");
                if(!error.isEmpty()) ImGui.textWrapped(error);
                if(SkinPickerChrome.button("Delete shader",true,160)) {
                    try {ManagedShaders.delete(deleting);ImGui.closeCurrentPopup();}
                    catch(Exception failure){error=message(failure);}
                }
                ImGui.sameLine();if(SkinPickerChrome.button("Cancel",false,100))ImGui.closeCurrentPopup();
            }finally{ImGui.endPopup();}
        }
    }
    private static String message(Throwable failure){String text=failure.getMessage();return text==null?"The shader operation failed.":text.substring(0,Math.min(700,text.length()));}
}
