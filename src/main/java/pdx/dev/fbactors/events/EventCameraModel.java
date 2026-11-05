package pdx.dev.fbactors.events;

import pdx.dev.fbactors.camera.BoatCameraModel.Shot;
import pdx.dev.fbactors.events.EventClip.Point;

/** Deterministic cinematic art direction, sampled directly from replay time. */
public final class EventCameraModel {
    public static final String[] STYLES={"Director cuts", "Distant witness", "Overhead", "Continuous follow"};
    public static final double[] FLIGHT_CUTS={.12,.30,.42,.58};
    private EventCameraModel() {}
    public static double cloudUnit(EventClip c) {return c.blast*(c.kind==EventKind.NUCLEAR?6:2.5);}
    public static Shot shot(EventClip c,double tick,double aspect,Point witness) {
        double p=c.progress(tick),impact=c.kind.impact,age=Math.max(0,(p-impact)/(1-impact));
        if(c.kind.natural()) {
            double size=c.blast,angle=.68+p*.20;
            var focus=c.impact.add(0,c.kind==EventKind.VOLCANO?145*size:c.kind.lightning()?145*size:40*size,0);
            if(c.kind.meteor() && c.cameraStyle!=2) {
                var eye=c.impact.add(410*size,16*size,520*size);
                double travel=Math.pow(Math.max(0,1-p/impact),1.15)*1450*size,length=Math.sqrt(.8*.8+1+.35*.35);
                var head=EventNature.point(c,0).add(.8*travel/length,travel/length,.35*travel/length);
                var sky=head.add(0,75*size,0);
                var blast=c.impact.add(0,(c.kind==EventKind.AIRBURST?190:60)*size,0);
                // A ground witness shot preserves scale: the whole sky fills with the entry.
                focus=c.kind==EventKind.METEOR_SHOWER?c.impact.add(0,320*size,0):sky.mix(blast,ease(impact-.04,impact+.10,p));
                return look(eye,focus,0,c.kind==EventKind.METEOR_SHOWER?78:60);
            }
            double d=(c.kind==EventKind.VOLCANO?680:c.kind==EventKind.THUNDERSTORM || c.kind==EventKind.CRAWLER?640:c.kind.lightning()?420:700)*size/Math.min(1,Math.max(.35,aspect));
            double elevation=c.kind==EventKind.THUNDERSTORM || c.kind==EventKind.CRAWLER?-100*size:45*size;
            return look(focus.add(Math.sin(angle)*d,c.cameraStyle==2?d:elevation,Math.cos(angle)*d),focus,0,54);
        }
        if(p<impact) {
            var focus=BombFlight.position(c,0,p);
            double d=14*BombFlight.size(c),angle=.65+p*.8;
            if(c.kind==EventKind.ICBM) {
                var frame=MissileFlight.sample(c,tick);double offset=15-9*ease(MissileFlight.SEPARATION,MissileFlight.SEPARATION+.04,p)-4.5*ease(MissileFlight.SHROUD,MissileFlight.SHROUD+.04,p);
                focus=frame.position().add(-frame.direction().x()*offset*c.scale,-frame.direction().y()*offset*c.scale,-frame.direction().z()*offset*c.scale);
                d=(44-12*ease(MissileFlight.SEPARATION,MissileFlight.SEPARATION+.04,p)-8*ease(MissileFlight.SECOND_SEPARATION,MissileFlight.SECOND_SEPARATION+.04,p)-12*ease(MissileFlight.SHROUD,MissileFlight.SHROUD+.04,p))*c.scale;
                if(c.cameraStyle==0) {
                    if(p<.12) {
                        // Low launch-side view, gradually releasing into the climb.
                        double t=ease(0,.12,p);angle=.70+t*.42;d=(68-t*21)*c.scale;
                        return look(focus.add(Math.sin(angle)*d,(-9+t*6)*c.scale,Math.cos(angle)*d),focus,-t*1.5,40);
                    }
                    if(p<.30) {
                        double t=ease(.12,.30,p);angle=-.75+t*.20;d=(45-t*8)*c.scale;
                        return look(focus.add(Math.sin(angle)*d,6*c.scale,Math.cos(angle)*d),focus,-2+t*3,38);
                    }
                    if(p<.58) {
                        double t=ease(.30,.42,p);angle=1.60-t*.25;d=(28-t*7)*c.scale;
                        // Keep the covers in frame: a continuous orbital dolly instead of
                        // teleporting across the vehicle at the separation instant.
                        double coast=ease(.40,.56,p);
                        angle=angle+(-.96-angle)*coast;d=d+(17*c.scale-d)*coast;
                        return look(focus.add(Math.sin(angle)*d,(4-.5*coast)*c.scale,Math.cos(angle)*d),focus.add(0,-c.scale*coast,0),1-t*2, (float)(34+10*coast));
                    }
                    double t=ease(.58,impact,p);angle=.80-t*.12;d=(22-t*5)*c.scale;
                    return look(focus.add(Math.sin(angle)*d,4*c.scale,Math.cos(angle)*d),focus,1-t*2,35);
                }
            }
            if(c.cameraStyle==2)return look(focus.add(d*.5,d*.9,d*.5),focus,0,48);
            return look(focus.add(Math.sin(angle)*d,d*.30,Math.cos(angle)*d),focus,Math.sin(p*9)*3,48);
        }
        double unit=c.kind==EventKind.CARPET?c.blast*1.5:cloudUnit(c);
        double height=(8+Math.pow(age,.65)*86)*unit,radius=(4+(1-Math.exp(-age*7))*38)*unit;
        var focus=c.impact.add(0,height*.52,0);
        double half=Math.min(Math.toRadians(27),Math.atan(Math.tan(Math.toRadians(27))*Math.max(.2,aspect)));
        double distance=Math.max(height*.62,radius*1.9)/Math.sin(half)*1.16;
        int style=c.cameraStyle==0?(age<.17?1:age<.42?2:1):c.cameraStyle;
        if(c.cameraStyle==0 && age>.42 && age<.64 && witness!=null) {
            var away=witness.add(witness.x()-c.impact.x(),0,witness.z()-c.impact.z());
            double length=Math.max(1,witness.distance(away));
            var eye=witness.add((away.x()-witness.x())/length*18,16,(away.z()-witness.z())/length*18);
            var crowd=witness.mix(c.impact,.24).add(0,3,0);
            return look(eye,crowd,0,76);
        }
        double angle=.68+age*.16;
        var eye=style==2?focus.add(Math.sin(angle)*distance*.45,distance*.94,Math.cos(angle)*distance*.45)
            :focus.add(Math.sin(angle)*distance,height*.06+unit*3,Math.cos(angle)*distance);
        // Deterministic pressure kick, no frame-dependent random camera motion.
        double kick=Math.exp(-age*24)*unit*.7;
        eye=eye.add(Math.sin(age*430)*kick,Math.cos(age*337)*kick*.6,0);
        return look(eye,focus,Math.sin(age*123)*Math.exp(-age*22)*1.5,54);
    }
    private static double ease(double from,double to,double value) {
        double t=Math.max(0,Math.min(1,(value-from)/(to-from)));return t*t*(3-2*t);
    }
    private static Shot look(Point eye,Point focus,double roll,float fov) {
        double dx=focus.x()-eye.x(),dy=focus.y()-eye.y(),dz=focus.z()-eye.z();
        return new Shot(eye.x(),eye.y(),eye.z(),Math.toDegrees(Math.atan2(-dx,dz)),
            -Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))),roll,fov);
    }
}
