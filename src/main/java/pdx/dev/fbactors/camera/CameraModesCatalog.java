package pdx.dev.fbactors.camera;

import java.util.List;

public final class CameraModesCatalog {
    public record Entry(String id, String name, String description, int mode) {}
    public static final List<Entry> ENTRIES = List.of(
        new Entry("follow", "Follow", "Follow a subject from behind, on foot or in a vehicle.", 0),
        new Entry("side", "Side follow", "Track alongside the subject to show its movement.", 1),
        new Entry("orbit", "Orbit", "Circle the subject using replay time.", 2),
        new Entry("pov", "First person", "Look forward from the subject's eye level.", 3),
        new Entry("front", "Front follow", "Move ahead of the subject and look back at it.", 4),
        new Entry("overhead", "Overhead", "Look straight down at the subject's path.", 5),
        new Entry("drift", "Drift follow", "Follow the direction of travel through a slide or turn.", 6),
        new Entry("pair", "Two subjects", "Keep two subjects in frame as their distance changes.", 7),
        new Entry("shoulder", "Shoulder", "An intimate tracking shot from behind and beside the subject.", 8),
        new Entry("low_angle", "Low angle", "Track close to the ground for a dramatic upward view.", 9),
        new Entry("crane", "Crane reveal", "Rise and widen over the first eight seconds of the clip.", 10),
        new Entry("push_in", "Push in", "Ease toward the subject over the first eight seconds of the clip.", 11),
        new Entry("pull_back", "Pull back", "Ease away and reveal the surroundings over eight seconds.", 12),
        new Entry("dolly_zoom", "Dolly zoom", "Move back while narrowing the lens to hold the subject's apparent size.", 13),
        new Entry("ice_chase", "Ice chase", "Follow travel rather than boat yaw through high-speed ice drifts.", 14),
        new Entry("ice_lead", "Ice lead", "Lead the boat down the track and look back through the corner.", 15),
        new Entry("ice_trackside", "Ice trackside", "Low lateral tracking keeps the hull and slide visible.", 16),
        new Entry("horse_chase", "Mounted chase", "Frame horse and rider together from a raised rear quarter.", 17),
        new Entry("horse_profile", "Gallop profile", "Track alongside a horse with room for the full stride.", 18),
        new Entry("rider_shoulder", "Rider shoulder", "Look past the rider toward the trail at mounted eye level.", 19),
        new Entry("route_scout", "Route scout", "A high forward-looking chase shot for routes and racing lines.", 20),
        new Entry("arc_reveal", "Arc reveal", "Sweep from the rear quarter to the front over eight seconds.", 21),
        new Entry("rise_overhead", "Rise to overhead", "Lift from a rear view into a top-down composition over eight seconds.", 22),
        new Entry("hero_orbit", "Hero orbit", "Circle while rising to reveal the subject and its surroundings.", 23));
    public static final List<String> GROUPS = List.of("All shots", "Tracking", "Ice boating", "Horse riding", "Cinematic");
    public static String group(int mode) {
        if(mode>=14 && mode<=16) return "Ice boating";
        if(mode>=17 && mode<=19) return "Horse riding";
        return mode>=8 && mode!=20?"Cinematic":"Tracking";
    }
    public static boolean timed(int mode) { return mode==2 || mode>=10 && mode<=13 || mode>=21; }
    public static boolean usesTravel(int mode) { return mode==6 || mode>=14 && mode<=18 || mode==20; }
    private CameraModesCatalog() {}
    public static Entry find(String id) { return ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null); }
    public static String[] names() { return ENTRIES.stream().map(Entry::name).toArray(String[]::new); }
    public static BoatCam.Settings recommended(int mode) {
        return switch(mode) {
            case 1 -> new BoatCam.Settings(1,4.5,1.4,4.5,2,18,0,55,3,.18,true);
            case 2 -> new BoatCam.Settings(2,4,1.8,3,0,18,0,60,0,.16,true);
            case 3 -> new BoatCam.Settings(3,1,1.6,0,0,0,0,75,0,.06,true);
            case 4 -> new BoatCam.Settings(4,4.5,1.8,0,0,0,0,60,3,.18,true);
            case 5 -> new BoatCam.Settings(5,5,3,0,0,0,0,55,0,.12,true);
            case 6 -> new BoatCam.Settings(6,5.5,1.8,0,4,0,5,65,6,.18,true);
            case 7 -> new BoatCam.Settings(7,6,2.8,0,0,0,0,60,0,.2,true);
            case 8 -> new BoatCam.Settings(8,2.8,1.65,.85,0,0,0,52,0,.12,true);
            case 9 -> new BoatCam.Settings(9,4,.4,0,0,0,0,55,0,.14,true);
            case 10 -> new BoatCam.Settings(10,5,5,0,0,0,0,55,0,0,true);
            case 11 -> new BoatCam.Settings(11,4,1.65,0,0,0,0,50,0,0,true);
            case 12 -> new BoatCam.Settings(12,5,2.2,0,0,0,0,55,0,0,true);
            case 13 -> new BoatCam.Settings(13,5,1.65,0,0,0,0,55,0,0,true);
            case 14 -> new BoatCam.Settings(14,7,2.2,0,4,0,3,72,5,.1,true);
            case 15 -> new BoatCam.Settings(15,8,1.4,0,2,0,0,65,3,.1,true);
            case 16 -> new BoatCam.Settings(16,5,.65,0,2,0,0,65,2,.08,true);
            case 17 -> new BoatCam.Settings(17,6,3,1.8,1,0,1,58,2,.16,true);
            case 18 -> new BoatCam.Settings(18,6,2,0,1,0,0,55,1,.12,true);
            case 19 -> new BoatCam.Settings(19,2,2.6,.75,3,0,0,65,0,.1,true);
            case 20 -> new BoatCam.Settings(20,8,8,0,6,0,0,65,2,.15,true);
            case 21 -> new BoatCam.Settings(21,5,2.2,0,0,18,0,55,0,0,true);
            case 22 -> new BoatCam.Settings(22,6,7,0,0,0,0,60,0,0,true);
            case 23 -> new BoatCam.Settings(23,6,3.5,0,0,18,0,55,0,0,true);
            default -> new BoatCam.Settings(0,4,1.8,3,2,18,2,60,4,.16,true);
        };
    }
    public static BoatCam.Settings withMode(BoatCam.Settings s, int mode) {
        return new BoatCam.Settings(mode,s.distance(),s.height(),s.side(),s.ahead(),s.orbit(),s.bank(),s.fov(),s.speedFov(),s.lag(),s.collision());
    }
}
