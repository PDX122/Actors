package pdx.dev.fbactors.take;

public record ActorDeath(int tick, Kind kind) {

    public enum Kind {
        LIGHTNING("Struck by lightning"),
        ANVIL("Anvil"),
        TNT("Blown up"),
        LAVA("Doused in lava"),
        WITHER("Withered"),
        ARROW("Shot"),
        DROWN("Drowned"),
        POISON("Poisoned"),
        VOID("Swallowed by the void"),
        METEOR("Meteor");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }
}

