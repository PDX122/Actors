package pdx.dev.fbactors.events;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pdx.dev.fbactors.compat.Compat;

public final class EventPlacement {
    private static boolean active, dragging,cancelledDrag;
    private static EventClip.Point launch;
    private static BlockPos hovered;
    private static int hoverFrame=-1, startTick;
    private static java.util.UUID owner, relocating;
    private static boolean relocateLaunch;
    private static String dimension;
    private static EventKind kind=EventKind.ICBM;
    private EventPlacement() {}
    public static boolean active() { return active; }
    public static void cancelByEscape() {cancel();cancelledDrag=true;}
    public static boolean choosingImpact() { return kind.twoLocations && launch!=null; }
    public static void cancel() { active=dragging=false;launch=null;hovered=null;relocating=null; }
    public static void begin(boolean drag) {
        begin(EventKind.ICBM,drag);
    }
    public static void begin(EventKind selected,boolean drag) {
        Events.timeline();cancel();
        kind=selected;
        var replay=Flashback.getReplayServer();if(replay==null)return;
        replay.replayPaused=true;owner=pdx.dev.fbactors.FlashbackActors.loadedReplayIdOrNull();
        active=true;dragging=drag;startTick=Math.max(0,replay.getReplayTick());
        var level=Minecraft.getInstance().level;dimension=level==null?"":level.dimension().toString();
    }
    public static void drag() { if(!active)begin(true); }
    public static void drag(EventKind selected) {if(cancelledDrag)return;if(!active || kind!=selected)begin(selected,true);}
    public static void relocate(EventClip clip,boolean from) {
        begin(clip.kind,false);
        if(!clip.dimension.isEmpty() && !clip.dimension.equals(dimension)) {Events.status="Return to the event's dimension to move its locations.";cancel();return;}
        relocating=clip.id;relocateLaunch=from;
    }
    public static void back() { if(relocating!=null || !kind.twoLocations)cancel();else {launch=null;dragging=false;} }
    public static String instruction() {
        if(relocating!=null)return "Click a block for the new "+(relocateLaunch?"launch":"impact")+" location";
        if(!kind.twoLocations)return dragging?"Release on the "+(kind==EventKind.CARPET?"bombing area":"impact block"):"Click the "+(kind==EventKind.CARPET?"bombing area":"impact block");
        return launch==null?(dragging?"1 / 2  Release on the launch block":"1 / 2  Click the launch block"):"2 / 2  Click the impact block";
    }
    /** Called inside Flashback's hovered viewport, before it can grab the mouse or edit blocks. */
    public static boolean viewportInput() {
        if(!active)return false;
        var replay=Flashback.getReplayServer();var mc=Minecraft.getInstance();
        if(replay==null || mc.level==null || !java.util.Objects.equals(owner,pdx.dev.fbactors.FlashbackActors.loadedReplayIdOrNull()) || Flashback.isExporting()) {cancel();return false;}
        if(!dimension.equals(mc.level.dimension().toString())) {cancel();return false;}
        var ray=ReplayUI.getMouseLookVector();hovered=null;hoverFrame=ImGui.getFrameCount();
        if(ray!=null) {
            var origin=Compat.eventCamera().position();
            var hit=mc.level.clip(new ClipContext(origin,origin.add(ray.normalize().scale(512)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()==HitResult.Type.BLOCK)hovered=hit.getBlockPos().immutable();
        }
        if(ImGui.isMouseClicked(1)) {back();return true;}
        boolean select=dragging?ImGui.isMouseReleased(0):ImGui.isMouseClicked(0);
        if(select) {
            dragging=false;
            if(hovered!=null) {
                var point=new EventClip.Point(hovered.getX()+.5,hovered.getY()+1.01,hovered.getZ()+.5);
                if(relocating!=null) {
                    var clip=Events.timeline().find(relocating);
                    if(clip!=null && !clip.locked && (!clip.kind.twoLocations || point.distance(relocateLaunch?clip.impact:clip.launch)>=1))
                        Events.edit(()->{if(!clip.kind.twoLocations) {clip.launch=point;clip.impact=point;}else if(relocateLaunch)clip.launch=point;else clip.impact=point;});
                    else { Events.status="Choose a different block for this endpoint.";return true; }
                    cancel();
                } else if(!kind.twoLocations) {Events.add(kind,point,point,startTick);cancel();}
                else if(launch==null)launch=point;
                else if(launch.distance(point)<1)Events.status="Choose a different block for impact.";
                else {Events.add(launch,point,startTick);cancel();}
            }
        }
        return true;
    }
    public static void render() {
        if(!ImGui.isMouseDown(0))cancelledDrag=false;
        if(!active)return;
        var server=Flashback.getReplayServer();
        if(server==null || !java.util.Objects.equals(owner,pdx.dev.fbactors.FlashbackActors.loadedReplayIdOrNull()) || Flashback.isExporting()) {cancel();return;}
        if(ImGui.isKeyPressed(ImGuiKey.Escape)) {cancel();return;}
        if(dragging && !ImGui.isMouseDown(0) && !ImGui.isMouseReleased(0)) {cancel();return;}
        var draw=ImGui.getForegroundDrawList();
        draw.pushClipRect(ReplayUI.frameX,ReplayUI.frameY,ReplayUI.frameX+ReplayUI.frameWidth,ReplayUI.frameY+ReplayUI.frameHeight,true);
        float bannerX=ReplayUI.frameX+16,bannerY=ReplayUI.frameY+18;
        draw.addRectFilled(bannerX-7,bannerY-6,bannerX+Math.min(470,ReplayUI.frameWidth-32),bannerY+39,0xe8202024,5);
        draw.addText(bannerX,bannerY,0xfff3d0a2,instruction());
        draw.addText(bannerX,bannerY+19,0xffa6b4c2,"Right click: back  /  Esc: cancel");
        if(hovered!=null && hoverFrame==ImGui.getFrameCount()) highlight(hovered,launch==null?0xff68e7ae:0xff718cff);
        if(launch!=null) {
            highlight(new BlockPos((int)Math.floor(launch.x()),(int)Math.floor(launch.y()-1.01),(int)Math.floor(launch.z())),0xff68e7ae);
            var a=Compat.projectActorPoint(new Vec3(launch.x(),launch.y()+.2,launch.z()));
            if(a!=null) {draw.addCircle(a[0],a[1],10,0xff68e7ae,24,2);draw.addText(a[0]+12,a[1]-8,0xffb9ffdd,"LAUNCH");}
        }
        draw.popClipRect();
        // Releasing over a panel abandons a drag, without placing a hidden endpoint.
        if(dragging && ImGui.isMouseReleased(0) && hoverFrame!=ImGui.getFrameCount())cancel();
    }
    private static void highlight(BlockPos block,int color) {
        var draw=ImGui.getForegroundDrawList();var p=new float[8][];
        for(int i=0;i<8;i++)p[i]=Compat.projectActorPoint(new Vec3(block.getX()+((i&1)==0?-.01:1.01),block.getY()+((i&2)==0?-.01:1.01),block.getZ()+((i&4)==0?-.01:1.01)));
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0 && p[i]!=null && p[i|bit]!=null)
            draw.addLine(p[i][0],p[i][1],p[i|bit][0],p[i|bit][1],color,2.5f);
        if(p[2]!=null && p[3]!=null && p[7]!=null && p[6]!=null)
            draw.addQuadFilled(p[2][0],p[2][1],p[3][0],p[3][1],p[7][0],p[7][1],p[6][0],p[6][1],(color&0x00ffffff)|0x33000000);
    }
}
