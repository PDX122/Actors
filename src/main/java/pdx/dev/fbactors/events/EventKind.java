package pdx.dev.fbactors.events;

public enum EventKind {
    ICBM("ICBM",MissileFlight.DEFAULT_TICKS,MissileFlight.IMPACT,true), NUCLEAR("Nuclear bomb",560,.22,false),
    /** Retained only to read older replays; no longer offered or played. */
    CARPET("Carpet bombing (retired)",400,.30,false),
    METEOR("Meteor impact",560,.42,false), VOLCANO("Volcanic eruption",800,.10,false), LIGHTNING("Lightning strike",320,.15,false),
    SUPERBOLT("Superbolt",360,.16,false), CRAWLER("Cloud crawler",400,.14,false), THUNDERSTORM("Thunderstorm",800,.12,false),
    METEOR_SHOWER("Meteor shower",720,.25,false), AIRBURST("Meteor airburst",560,.42,false);
    public static final EventKind[] LIBRARY={ICBM,NUCLEAR,METEOR,AIRBURST};
    public boolean available() {return this==ICBM || this==NUCLEAR || this==METEOR || this==AIRBURST;}
    public boolean natural() {return ordinal()>=3;}
    public boolean lightning() {return this==LIGHTNING || this==SUPERBOLT || this==CRAWLER || this==THUNDERSTORM;}
    public boolean meteor() {return this==METEOR || this==METEOR_SHOWER || this==AIRBURST;}
    public String category() {return lightning()?"Lightning & storms":meteor()?"Meteors":natural()?"Natural forces":"Explosions";}
    public String description() {
        return switch(this) {
            case ICBM -> "Three-stage launch, orbital coast, cover separation and re-entry. Choose launch and impact blocks.";
            case NUCLEAR -> "High-altitude bomb drop, blinding flash, rolling mushroom cloud and lingering pressure haze.";
            case METEOR -> "An incandescent bolide tears across the sky, fragments and strikes in an expanding cloud of dust and fire.";
            case VOLCANO -> "A violent vent erupts into a towering ash column, glowing ejecta and a rolling airborne ash surge.";
            case LIGHTNING -> "A branching cloud-to-ground discharge with rapid return strokes, blue-white glare and delayed thunder.";
            case SUPERBOLT -> "A massive, heavily branched discharge, lingering ionized channel and a deep rolling thunderclap.";
            case CRAWLER -> "Sheet lightning crawls sideways through a broad cloud deck, illuminating its billows from within.";
            case THUNDERSTORM -> "A moving shelf of dark volumetric clouds, rain curtains and repeated lightning with rolling thunder.";
            case METEOR_SHOWER -> "A procession of enormous burning fragments crosses the sky before staggered impacts and overlapping shockwaves.";
            case AIRBURST -> "A tumbling fireball breaks apart overhead in a blinding atmospheric burst, expanding vapour shell and falling fragments.";
            default -> "Retired event. Remove this clip from the timeline.";
        };
    }
    public final String label;
    public final int duration;
    public final double impact;
    public final boolean twoLocations;
    EventKind(String label,int duration,double impact,boolean twoLocations) {
        this.label=label;this.duration=duration;this.impact=impact;this.twoLocations=twoLocations;
    }
}
