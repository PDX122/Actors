package pdx.dev.fbactors.events;

/** Only user-supplied ICBM audio is scheduled. Phase times stretch with the event. */
public final class EventAudioPlan {
    public record Phase(String name,double start,double end,float gain,float pitch,boolean loop) {
        public boolean active(EventClip c,double p) {return c.kind==EventKind.ICBM && c.sound && p>=start && p<end;}
        public float level(double p) {
            double fade=Math.min(.008,(end-start)*.15);
            return gain*(float)Math.max(0,Math.min(1,Math.min((p-start)/fade,(end-p)/fade)));
        }
    }
    public static final Phase[] PHASES={
        new Phase("alert_klaxon",0,.055,.24f,1,false),
        new Phase("prepare_windup",0,.045,.35f,1.1f,false),new Phase("ignition",.020,.060,.40f,1,false),
        new Phase("prepare_stopped",.075,.10,.20f,1,false),
        new Phase("launch",MissileFlight.IGNITION,MissileFlight.BURNOUT,.80f,.7f,false),
        new Phase("engine_sustain",.15,MissileFlight.BURNOUT,.24f,.7f,true),
        new Phase("stage_deploy",MissileFlight.SEPARATION,MissileFlight.SEPARATION+.025,.40f,1,false),
        new Phase("stage_clank",MissileFlight.SECOND_SEPARATION,MissileFlight.SECOND_SEPARATION+.035,.35f,.8f,false),
        new Phase("stage_cap",MissileFlight.SHROUD,MissileFlight.SHROUD+.06,.50f,.7f,false),
        new Phase("reentry_scream",MissileFlight.REENTRY,MissileFlight.IMPACT,.75f,1,false),
        new Phase("impact_crackle",MissileFlight.IMPACT+.025,.94,.25f,.8f,true),
        new Phase("impact_wind",MissileFlight.IMPACT+.06,.98,.16f,1,true)
    };
    public static final String[] FILES={"alert_klaxon","silo_door_open","launch","reentry_scream","detonation_close","detonation_distant","engine_sustain","prepare_windup","prepare_stopped","ignition","stage_deploy","stage_clank","stage_cap","impact_bass","impact_crackle","impact_wind"};
    private EventAudioPlan() {}
    public static String impact(double distance) {return distance>600?"detonation_distant":"detonation_close";}
}
