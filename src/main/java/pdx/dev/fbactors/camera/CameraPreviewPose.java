package pdx.dev.fbactors.camera;

import java.util.ArrayList;
import java.util.Objects;

/** Interpolates captured resource-pack poses without replacing their animation curves. */
public final class CameraPreviewPose {
    private CameraPreviewCapture.Mesh previous,current;
    private double previousTime,currentTime;
    public void accept(CameraPreviewCapture.Mesh mesh,double seconds) {
        if(current==null || seconds<=currentTime || seconds-currentTime>.25) previous=null;
        else { previous=current;previousTime=currentTime; }
        current=mesh;currentTime=seconds;
    }
    public CameraPreviewCapture.Mesh at(double seconds) {
        if(current==null || previous==null || seconds>=currentTime || previous.vertices().size()!=current.vertices().size()) return current;
        float t=(float)Math.max(0,Math.min(1,(seconds-previousTime)/(currentTime-previousTime)));
        var vertices=new ArrayList<CameraPreviewCapture.Vertex>(current.vertices().size());
        for(int i=0;i<current.vertices().size();i++) {
            var a=previous.vertices().get(i);var b=current.vertices().get(i);
            if(a.u()!=b.u() || a.v()!=b.v() || !Objects.equals(a.texture(),b.texture())) return current;
            vertices.add(new CameraPreviewCapture.Vertex(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t,
                b.u(),b.v(),a.nx()+(b.nx()-a.nx())*t,a.ny()+(b.ny()-a.ny())*t,a.nz()+(b.nz()-a.nz())*t,b.color(),b.texture()));
        }
        return new CameraPreviewCapture.Mesh(vertices,current.renderer(),current.nativeTransform());
    }
}
