package pdx.dev.fbactors.ui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPass;
import pdx.dev.fbactors.mixin.MixinImGuiScissor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;

class ImGuiScissorHookTest {
    @Test void rendererHooksNeverSendEmptyScissorsOrUnclippedDraws() throws Exception {
        var hook=new MixinImGuiScissor();
        var scissor=MixinImGuiScissor.class.getDeclaredMethod("fbactors$scissor",RenderPass.class,int.class,int.class,int.class,int.class,Operation.class);
        var draw=MixinImGuiScissor.class.getDeclaredMethod("fbactors$draw",RenderPass.class,int.class,int.class,int.class,int.class,int.class,Operation.class);
        scissor.setAccessible(true); draw.setAccessible(true);
        var scissors=new AtomicInteger(); var draws=new AtomicInteger();
        Operation<Void> setClip=args -> { assertTrue((int)args[3]>0 && (int)args[4]>0); scissors.incrementAndGet(); return null; };
        Operation<Void> submit=args -> { draws.incrementAndGet(); return null; };
        scissor.invoke(hook,null,0,0,112,0,setClip); draw.invoke(hook,null,6,1,0,0,0,submit);
        assertEquals(0,scissors.get()); assertEquals(0,draws.get());
        scissor.invoke(hook,null,0,0,112,64,setClip); draw.invoke(hook,null,6,1,0,0,0,submit);
        assertEquals(1,scissors.get()); assertEquals(1,draws.get());
        scissor.invoke(hook,null,0,0,112,0,setClip); draw.invoke(hook,null,6,1,0,0,0,submit);
        assertEquals(1,scissors.get()); assertEquals(1,draws.get());
    }
}
