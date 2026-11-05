package pdx.dev.fbactors.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Captures the vertices actually emitted by Minecraft and resource-pack model renderers. */
public final class CameraPreviewCapture {
    public record Vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz,int color,String texture) {}
    public record Mesh(List<Vertex> vertices,String renderer,boolean nativeTransform) {
        public Mesh(List<Vertex> vertices) { this(vertices,"",false); }
        public Mesh(List<Vertex> vertices,String renderer) { this(vertices,renderer,false); }
    }
    private static final ThreadLocal<Session> ACTIVE=new ThreadLocal<>();
    private static final Map<Consumer<Mesh>,Float> CLOCKS=new WeakHashMap<>();
    private static final Class<?> ETF=etfInterface();
    private static Class<?> etfInterface() {
        try { return Class.forName("traben.entity_texture_features.utils.ETFVertexConsumer"); }
        catch(ClassNotFoundException | LinkageError ignored) { return null; }
    }
    private static final class Session {
        final List<Vertex> vertices=new ArrayList<>();
        final Map<Object,String> materials=new IdentityHashMap<>();
        String texture;
        String renderer="";
        Matrix4f inverse;
        Matrix3f inverseNormal;
        boolean nativeTransform;
        float frameTime=Float.NaN;
        float rowingTicks=Float.NaN;
        final Map<net.minecraft.client.model.geom.ModelPart,ArmPose> arms=new IdentityHashMap<>();
    }
    private record ArmPose(float x,float y,float z) {}
    private CameraPreviewCapture() {}
    public static void material(String id) { var session=ACTIVE.get();if(session!=null)session.texture=id; }
    public static void material(Object type,String id) { var session=ACTIVE.get();if(session!=null && type!=null)session.materials.put(type,id); }
    public static <T> T render(Consumer<Mesh> output,Supplier<T> work) {
        return render(output,Float.NaN,work);
    }
    public static float animationFrameTime() {
        var session=ACTIVE.get();return session==null?Float.NaN:session.frameTime;
    }
    public static <T> T render(Consumer<Mesh> output,float ticks,Supplier<T> work) {
        if(output==null) return work.get();
        var previous=ACTIVE.get();var session=new Session();ACTIVE.set(session);
        if(Float.isFinite(ticks)) {
            var before=CLOCKS.put(output,ticks);
            float delta=before==null?.05f:(ticks-before)/20;
            session.frameTime=delta>0 && delta<=.25f?delta:.05f;
        }
        try {
            T result=work.get();
            if(!session.vertices.isEmpty()) output.accept(new Mesh(List.copyOf(session.vertices),session.renderer,session.nativeTransform));
            return result;
        } finally { if(previous==null) ACTIVE.remove();else ACTIVE.set(previous); }
    }
    public static void model(Object model) {
        var session=ACTIVE.get();if(session==null) return;
        try {
            var root=model.getClass().getMethod("root").invoke(model);
            session.renderer=model.getClass().getName()+" ["+root.getClass().getName()+"]";
            if(root instanceof net.minecraft.client.model.geom.ModelPart part) rowingModel(part);
        }
        catch(ReflectiveOperationException ignored) { session.renderer=model.getClass().getName(); }
    }
    public static void rowing(float ticks) { var session=ACTIVE.get();if(session!=null) session.rowingTicks=ticks; }
    public static boolean rowingActive() { var session=ACTIVE.get();return session!=null && !session.arms.isEmpty(); }
    static void rowingModel(net.minecraft.client.model.geom.ModelPart root) {
        var session=ACTIVE.get();if(session==null || !Float.isFinite(session.rowingTicks)) return;
        double stroke=session.rowingTicks*Math.PI/8;
        float x=(float)(-1.05+.38*Math.sin(-stroke)),y=(float)(.25+.18*Math.sin(-stroke+1));
        for(String name:List.of("left_arm","right_arm","left_sleeve","right_sleeve")) if(root.hasChild(name)) {
            float side=name.startsWith("left")?1:-1;
            session.arms.put(root.getChild(name),new ArmPose(x,side*y,-side*.08f));
        }
    }
    public static void arm(net.minecraft.client.model.geom.ModelPart part,Runnable draw) {
        var session=ACTIVE.get();var arm=session==null?null:session.arms.get(part);
        if(arm==null) { draw.run();return; }
        float x=part.xRot,y=part.yRot,z=part.zRot;
        try {
            part.xRot=arm.x;part.yRot=arm.y;part.zRot=arm.z;
            CameraPreviewNative.rowingEvidence(arm.x);
            draw.run();
        } finally { part.xRot=x;part.yRot=y;part.zRot=z; }
    }
    public static void reference(PoseStack pose) {
        var session=ACTIVE.get();if(session==null || session.nativeTransform) return;
        session.inverse=new Matrix4f(pose.last().pose()).invert();
        session.inverseNormal=new Matrix3f(pose.last().normal()).invert();
        session.nativeTransform=true;
    }
    public static VertexConsumer wrap(PoseStack pose,VertexConsumer original) {
        var session=ACTIVE.get();
        if(session==null) return original;
        if(!session.nativeTransform) {
            session.inverse=new Matrix4f(pose.last().pose()).invert();
            session.inverseNormal=new Matrix3f(pose.last().normal()).invert();
        }
        return decorated(original,session)?original:decorate(original,session,pose,null);
    }
    public static VertexConsumer buffer(VertexConsumer original) {
        return buffer(original,null);
    }
    public static VertexConsumer buffer(VertexConsumer original,Object type) {
        var session=ACTIVE.get();
        return session==null || decorated(original,session)?original:decorate(original,session,null,session.materials.get(type));
    }
    private static boolean decorated(VertexConsumer value,Session session) {
        if(value instanceof Capture capture) return capture.session==session;
        return java.lang.reflect.Proxy.isProxyClass(value.getClass())
            && java.lang.reflect.Proxy.getInvocationHandler(value) instanceof Forward f && f.capture.session==session;
    }
    private static VertexConsumer decorate(VertexConsumer original,Session session,PoseStack pose,String texture) {
        var capture=new Capture(original,session,pose,texture);
        if(ETF==null || !ETF.isInstance(original)) return capture;
        // CEM texture overrides rely on this optional interface remaining visible on the consumer.
        return (VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(original.getClass().getClassLoader(),
            new Class<?>[]{VertexConsumer.class,ETF},new Forward(capture));
    }
    private record Forward(Capture capture) implements java.lang.reflect.InvocationHandler {
        @Override public Object invoke(Object proxy,java.lang.reflect.Method method,Object[] args) throws Throwable {
            try {
                boolean extra=method.getName().equals("setUv3") || method.getName().equals("setLineWidth");
                Object result=method.invoke(method.getDeclaringClass()==VertexConsumer.class && !extra?capture:capture.original,args);
                return result==capture || result==capture.original?proxy:result;
            } catch(java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
        }
    }
    private static final class Capture implements VertexConsumer {
        final VertexConsumer original;
        final Session session;
        final String texture;
        float x,y,z,u,v;
        int color=-1;
        final Matrix4f inverse;
        final Matrix3f inverseNormal;
        Capture(VertexConsumer original,Session session,PoseStack pose,String texture) {
            this.original=original;this.session=session;
            this.texture=texture;
            inverse=session.nativeTransform?new Matrix4f(session.inverse):pose==null?null:new Matrix4f(pose.last().pose()).invert();
            inverseNormal=session.nativeTransform?new Matrix3f(session.inverseNormal):pose==null?null:new Matrix3f(pose.last().normal()).invert();
        }
        @Override public VertexConsumer addVertex(float x,float y,float z) {
            this.x=x;this.y=y;this.z=z;original.addVertex(x,y,z);return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) {
            color=a<<24|b<<16|g<<8|r;original.setColor(r,g,b,a);return this;
        }
        public VertexConsumer setColor(int argb) {
            return setColor(argb>>16&255,argb>>8&255,argb&255,argb>>>24);
        }
        @Override public VertexConsumer setUv(float u,float v) { this.u=u;this.v=v;original.setUv(u,v);return this; }
        @Override public VertexConsumer setUv1(int u,int v) { original.setUv1(u,v);return this; }
        @Override public VertexConsumer setUv2(int u,int v) { original.setUv2(u,v);return this; }
        public VertexConsumer setUv3(float u,float v) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
        @Override public VertexConsumer setNormal(float nx,float ny,float nz) {
            original.setNormal(nx,ny,nz);
            var position=inverse==null?session.inverse:inverse;
            var normal=inverseNormal==null?session.inverseNormal:inverseNormal;
            if(position==null || normal==null) return this;
            var p=position.transformPosition(new Vector3f(x,y,z));
            var n=normal.transform(new Vector3f(nx,ny,nz)).normalize();
            if(session.vertices.size()<100_000) session.vertices.add(session.nativeTransform
                ?new Vertex(-p.x,p.y,-p.z,u,v,-n.x,n.y,-n.z,color,texture==null?session.texture:texture)
                :new Vertex(p.x,1.5f-p.y,-p.z,u,v,n.x,-n.y,-n.z,color,texture==null?session.texture:texture));
            return this;
        }
    }
}
