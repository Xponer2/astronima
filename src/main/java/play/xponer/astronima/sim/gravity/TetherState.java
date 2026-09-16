package play.xponer.astronima.sim.gravity;

/**
 * design/eva-mobility.md §3.2: whether a player currently has an active tether, and if so, where
 * it is anchored and how much slack it was fired with. {@link #NONE} is the untethered state — the
 * same sentinel-record shape {@code CarriedContamination}/{@code CarriedInfection} already use in
 * this codebase, rather than a nullable reference every consumer would need to null-check
 * separately.
 */
public record TetherState(boolean active, double anchorX, double anchorY, double anchorZ,
                           double restLength) {

    public static final TetherState NONE = new TetherState(false, 0, 0, 0, 0);
}
