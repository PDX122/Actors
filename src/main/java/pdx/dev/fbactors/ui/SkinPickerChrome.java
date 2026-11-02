package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCol;
import imgui.moulberry90.flag.ImGuiStyleVar;

public final class SkinPickerChrome {
    private SkinPickerChrome() {}
    public static void push() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,16,16);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing,8,8);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding,10,7);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding,10);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding,14);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize,0);
        ImGui.pushStyleColor(ImGuiCol.WindowBg,0xFF1C1917);
        ImGui.pushStyleColor(ImGuiCol.ChildBg,0x00191715);
        ImGui.pushStyleColor(ImGuiCol.FrameBg,0xFF34302A);
        ImGui.pushStyleColor(ImGuiCol.Button,0xFF332F29);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered,0xFF51483D);
        ImGui.pushStyleColor(ImGuiCol.Header,0xFF514438);
        ImGui.pushStyleColor(ImGuiCol.Text,0xFFF0EBE7);
        ImGui.pushStyleColor(ImGuiCol.TextDisabled,0xFFA49C94);
    }
    public static void pop() { ImGui.popStyleColor(8); ImGui.popStyleVar(6); }

    public static boolean row(String id,String label,boolean selected,long head) {
        float width=Math.max(1,ImGui.getContentRegionAvailX()), height=44;
        boolean clicked=ImGui.invisibleButton(id,width,height);
        float x=ImGui.getItemRectMinX(), y=ImGui.getItemRectMinY();
        var draw=ImGui.getWindowDrawList();
        if (selected || ImGui.isItemHovered()) draw.addRectFilled(x,y,x+width,y+height,selected?0xFF4D4135:0xFF322D27,10);
        draw.addRectFilled(x+8,y+8,x+36,y+36,0xFF494037,8);
        if (head!=0) {
            draw.addImage(head,x+10,y+10,x+34,y+34,8f/64,8f/64,16f/64,16f/64);
            draw.addImage(head,x+10,y+10,x+34,y+34,40f/64,8f/64,48f/64,16f/64);
        } else if (!label.isBlank()) draw.addText(x+18,y+14,0xFFDED1C4,label.substring(0,1).toUpperCase(java.util.Locale.ROOT));
        String visible=label;
        while (visible.length()>1 && ImGui.calcTextSizeX(visible)>width-56) visible=visible.substring(0,visible.length()-1);
        if (!visible.equals(label) && visible.length()>2) visible=visible.substring(0,visible.length()-2)+"..";
        draw.addText(x+46,y+(height-ImGui.getFontSize())/2,0xFFF0EBE7,visible);
        return clicked;
    }

    public static float stage(long texture,float width,float height,String message) {
        width=Math.max(1,width);height=Math.max(1,height);
        ImGui.invisibleButton("##SkinStage",width,height);
        if (!ImGui.isItemVisible()) return 0;
        float x=ImGui.getItemRectMinX(), y=ImGui.getItemRectMinY();
        var draw=ImGui.getWindowDrawList();
        draw.addRectFilled(x,y,x+width,y+height,0xFF292521,14);
        if (texture!=0) {
            float size=Math.min(width,height);
            float left=x+(width-size)/2,top=y+(height-size)/2+Math.min(12,height*.04f);
            draw.pushClipRect(x,y,x+width,y+height,true);
            draw.addImage(texture,left,top,left+size,top+size,0,1,1,0);
            draw.popClipRect();
        } else if (message!=null) {
            float text=ImGui.calcTextSizeX(message);
            draw.addText(x+Math.max(10,(width-text)/2),y+(height-ImGui.getFontSize())/2,0xFFA49C94,message);
        }
        return ImGui.isItemActive() && ImGui.isMouseDragging(0) ? ImGui.getIO().getMouseDeltaX()*.8f : 0;
    }
    public static boolean button(String label,boolean selected,float width) {
        ImGui.pushStyleColor(ImGuiCol.Button,selected?0xFF514436:0xFF34302A);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered,0xFF594D40);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,0xFF665642);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding,10);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding,6,7);
        try { return iconButton(label,width); }
        finally { ImGui.popStyleVar(2); ImGui.popStyleColor(3); }
    }

    public static boolean apply(boolean disabled,float width) {
        ImGui.pushStyleColor(ImGuiCol.Button,0xFFB7D567);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered,0xFFCAE985);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,0xFF9EBB53);
        ImGui.pushStyleColor(ImGuiCol.Text,0xFF1D2419);
        ImGui.beginDisabled(disabled);
        try { return iconButton("Apply skin",width); }
        finally { ImGui.endDisabled(); ImGui.popStyleColor(4); }
    }

    private static boolean iconButton(String label,float width) {
        boolean clicked=ImGui.button("##SkinButton"+label,Math.max(1,width),32);
        String text=label.split("##",2)[0], icon=PhosphorIcons.forLabel(label);
        float textWidth=ImGui.calcTextSizeX(text), iconSize=14, gap=5;
        boolean showIcon=icon!=null && width>=textWidth+iconSize+gap+8;
        float total=textWidth+(showIcon?iconSize+gap:0);
        float x=ImGui.getItemRectMinX()+Math.max(2,(width-total)/2),y=ImGui.getItemRectMinY();
        int color=ImGui.getColorU32(ImGuiCol.Text);
        if(showIcon) { PhosphorIcons.draw(icon,x,y+9,iconSize,color);x+=iconSize+gap; }
        ImGui.getWindowDrawList().addText(x,y+(32-ImGui.getFontSize())/2,color,text);
        return clicked;
    }
}
