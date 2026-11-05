package pdx.dev.fbactors.events;

import java.util.UUID;

/** Visual-only scene cue. No entities, block changes, damage, or simulation state. */
public final class EventClip {
    public static final int MAX_TICK = 100_000_000;
    public UUID id = UUID.randomUUID();
    public String name = "ICBM";
    public EventKind kind=EventKind.ICBM;
    public String dimension = "";
    public boolean enabled = true;
    public boolean locked;
    public int start, end = MissileFlight.DEFAULT_TICKS;
    public Point launch = new Point(0, 64, 0), impact = new Point(80, 64, 0);
    public float scale = 1, blast = 1;
    public float dropHeight=180,heading,spread=72,volume=1;
    public int bombs=12;
    public boolean sound=true;
    public boolean eventCam;
    public int cameraStyle;
    public int ending,timingVersion;
    public boolean cinematicBars=true;

    public record Point(double x, double y, double z) {
        public Point add(double dx, double dy, double dz) { return new Point(x+dx,y+dy,z+dz); }
        public Point mix(Point other, double t) { return add((other.x-x)*t,(other.y-y)*t,(other.z-z)*t); }
        public double distance(Point other) { return Math.sqrt(Math.pow(x-other.x,2)+Math.pow(y-other.y,2)+Math.pow(z-other.z,2)); }
        public boolean valid() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Math.abs(x)<=30_000_000 && Math.abs(y)<=30_000_000 && Math.abs(z)<=30_000_000; }
    }

    public void normalize() {
        if(id==null) id=UUID.randomUUID();
        if(kind==null)kind=EventKind.ICBM;
        if(name==null || name.isBlank()) name="ICBM";
        if(dimension==null)dimension="";
        name=name.substring(0,Math.min(name.length(),64));
        start=Math.max(0,Math.min(MAX_TICK-20,start));
        end=Math.max(start+20,Math.min(MAX_TICK,end));
        scale=Float.isFinite(scale)?Math.max(.25f,Math.min(4,scale)):1;
        blast=Float.isFinite(blast)?Math.max(.25f,Math.min(4,blast)):1;
        dropHeight=Float.isFinite(dropHeight)?Math.max(40,Math.min(512,dropHeight)):180;
        heading=Float.isFinite(heading)?heading%360:0;
        spread=Float.isFinite(spread)?Math.max(12,Math.min(240,spread)):72;
        bombs=Math.max(4,Math.min(24,bombs));volume=Float.isFinite(volume)?Math.max(0,Math.min(2,volume)):1;
        cameraStyle=Math.max(0,Math.min(3,cameraStyle));
        ending=EventEnding.value(this);
    }
    public boolean valid() { return launch!=null && impact!=null && launch.valid() && impact.valid() && (kind!=null && !kind.twoLocations || launch.distance(impact)>=1); }
    public boolean active(double tick) { return kind!=null && kind.available() && enabled && Double.isFinite(tick) && tick>=start && tick<end; }
    public double progress(double tick) { return Math.max(0,Math.min(1,(tick-start)/(end-start))); }
    public EventClip copy() {
        var c=new EventClip(); c.id=id;c.name=name;c.enabled=enabled;c.locked=locked;c.start=start;c.end=end;
        c.launch=launch;c.impact=impact;c.scale=scale;c.blast=blast;c.dimension=dimension;c.kind=kind;
        c.dropHeight=dropHeight;c.heading=heading;c.spread=spread;c.bombs=bombs;c.volume=volume;c.sound=sound;c.eventCam=eventCam;c.cameraStyle=cameraStyle;
        c.ending=ending;c.cinematicBars=cinematicBars;c.timingVersion=timingVersion;return c;
    }
}
