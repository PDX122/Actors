package pdx.dev.fbactors.events;

import pdx.dev.fbactors.events.EventClip.Point;

public final class BombFlight {
    private BombFlight() {}
    public static double size(EventClip c) {return c.scale*(c.kind==EventKind.NUCLEAR?2.5:1);}
    public static double release(EventClip c,int bomb) { return c.kind==EventKind.CARPET?.04+bomb/(double)(c.bombs-1)*.32:0; }
    public static double impact(EventClip c,int bomb) { return release(c,bomb)+(c.kind==EventKind.CARPET?.24:c.kind.impact); }
    public static Point target(EventClip c,int bomb) {
        if(c.kind!=EventKind.CARPET)return c.impact;
        double along=(bomb/(double)(c.bombs-1)-.5)*c.spread;
        double across=(bomb%3-1)*c.spread*.12,angle=Math.toRadians(c.heading);
        return c.impact.add(Math.sin(angle)*along+Math.cos(angle)*across,0,Math.cos(angle)*along-Math.sin(angle)*across);
    }
    public static Point position(EventClip c,int bomb,double progress) {
        var hit=target(c,bomb);double t=Math.max(0,Math.min(1,(progress-release(c,bomb))/(impact(c,bomb)-release(c,bomb))));
        return hit.add(c.kind==EventKind.CARPET?Math.sin(Math.toRadians(c.heading))*(t-1)*18:0,
            c.dropHeight*(1-t*t)+2*size(c),c.kind==EventKind.CARPET?Math.cos(Math.toRadians(c.heading))*(t-1)*18:0);
    }
    public static String phase(EventClip c,double progress) {
        if(c.kind==EventKind.THUNDERSTORM)return "Storm front / rain curtains / repeated lightning";
        if(c.kind.meteor())return progress<c.kind.impact?"Atmospheric entry / fragmentation":c.kind==EventKind.AIRBURST?"Airburst / pressure shell / falling fragments":"Impact / ejecta / dust surge";
        if(c.kind==EventKind.VOLCANO)return progress<c.kind.impact?"Vent ignition":"Eruption column / ash surge / ejecta";
        if(c.kind.lightning())return progress<c.kind.impact?"Charge buildup":progress<c.kind.impact+.16?"Discharge / return strokes":"Delayed thunder / fading glare";
        if(c.kind==EventKind.CARPET)return progress<.28?"Bombing run / falling bombs":progress<.62?"Rolling impacts":"Smoke / embers / aftermath";
        return progress<c.kind.impact?"High-altitude freefall":progress<c.kind.impact+.08?"Flash / pressure wave":"Mushroom cloud / heat haze / aftermath";
    }
}
