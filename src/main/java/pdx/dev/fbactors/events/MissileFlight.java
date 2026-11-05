package pdx.dev.fbactors.events;

import pdx.dev.fbactors.events.EventClip.Point;

/** A cinematic, compressed ballistic flight, sampled directly from the replay clock. */
public final class MissileFlight {
    public static final int DEFAULT_TICKS=1200;
    public static final double IGNITION=.025, SEPARATION=.19, SECOND_SEPARATION=.30, BURNOUT=.39, SHROUD=.42, REENTRY=.58, IMPACT=.72;
    public static final double LENGTH=31.4;
    private static final double[] TIMES={IGNITION,.12,SEPARATION,SECOND_SEPARATION,BURNOUT,.48,REENTRY,IMPACT};
    private static final double[] FRACTIONS={0,.035,.12,.29,.41,.55,.72,1},SLOPES={0,.8,1.7,1.6,1.45,1.45,2,3};
    public record Frame(Point position, Point direction, double progress, double explosion, String phase) {}
    private MissileFlight() {}
    public static Frame sample(EventClip clip,double tick) {
        double p=clip.progress(tick),a=Math.max(IGNITION,p-.0001),b=Math.min(IMPACT,p+.0001);
        var position=nose(clip,p);var from=nose(clip,a);var to=nose(clip,b);
        double dx=to.x()-from.x(),dy=to.y()-from.y(),dz=to.z()-from.z(),length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        var direction=length<1e-8?new Point(0,p>=IMPACT?-1:1,0):new Point(dx/length,dy/length,dz/length);
        String phase=p<IGNITION?"Ignition":p<.12?"Accelerating boost":p<SEPARATION?"Pitch-over":p<SECOND_SEPARATION?"Second-stage burn":p<BURNOUT?"Third-stage burn / leaving atmosphere":p<SHROUD?"Engine cutoff":p<REENTRY?"Shroud release / space coast":p<IMPACT?"Hypersonic re-entry":"Impact / aftermath";
        return new Frame(position,direction,p,Math.max(0,(p-IMPACT)/(1-IMPACT)),phase);
    }
    public static Point position(EventClip clip,double p) {
        double flight=fraction(p);
        double height=height(clip);
        // Cubic Bezier: leave the pad vertically, pitch over, arrive nose-first.
        double u=1-flight;
        var a=clip.launch;var b=a.add(0,height,0);var d=clip.impact;var c=d.add(0,height*.9,0);
        double dx=d.x()-a.x(),dz=d.z()-a.z(),distance=Math.max(1,Math.hypot(dx,dz));
        // A broad cinematic cross-track arc keeps a short editor route from making
        // a needle-shaped turn at apogee. Endpoints and vertical tangents are exact.
        double lateral=height*2.4*flight*flight*u*u*(1-2*flight);
        // Smooth gravity turn: a restrained bank off the pad becomes a broad,
        // downrange lean instead of spending the whole boost on a vertical line.
        double turn=height*.12*flight*flight/(flight*flight+.0036)*Math.pow(u,5);
        lateral+=height*.018*flight*flight/(flight*flight+.0016)*Math.exp(-flight*12)*u*u;
        double sideX=-dz/distance,sideZ=Math.hypot(dx,dz)<.000001?1:dx/distance;
        return new Point(sideX*lateral+dx/distance*turn+u*u*u*a.x()+3*u*u*flight*b.x()+3*u*flight*flight*c.x()+flight*flight*flight*d.x(),
            u*u*u*a.y()+3*u*u*flight*b.y()+3*u*flight*flight*c.y()+flight*flight*flight*d.y(),
            sideZ*lateral+dz/distance*turn+u*u*u*a.z()+3*u*u*flight*b.z()+3*u*flight*flight*c.z()+flight*flight*flight*d.z());
    }
    public static double height(EventClip clip) {
        double distance=Math.hypot(clip.impact.x()-clip.launch.x(),clip.impact.z()-clip.launch.z());
        return Math.max(10000,Math.min(22000,distance*2.5+10000))*Math.sqrt(clip.scale);
    }
    /** Monotone Hermite timing: build speed off the pad, coast, then a sharp terminal dive. */
    public static double fraction(double p) {
        if(p<=IGNITION)return 0;if(p>=IMPACT)return 1;
        for(int i=1;i<TIMES.length;i++)if(p<=TIMES[i]) {
            double span=TIMES[i]-TIMES[i-1],t=(p-TIMES[i-1])/span,t2=t*t,t3=t2*t;
            return (2*t3-3*t2+1)*FRACTIONS[i-1]+(t3-2*t2+t)*span*SLOPES[i-1]
                +(-2*t3+3*t2)*FRACTIONS[i]+(t3-t2)*span*SLOPES[i];
        }
        return 1;
    }
    private static Point nose(EventClip clip,double p) {
        return position(clip,p).add(0,LENGTH*clip.scale*(1-fraction(p)),0);
    }
}
