package play.xponer.astronima.atmosphere;

import play.xponer.astronima.sim.room.Shell;
import it.unimi.dsi.fastutil.longs.LongSet;
import play.xponer.astronima.sim.RoomState;

/**
 * Runtime record of one detected room: its physics state plus the block positions
 * (packed longs) it occupies. {@code dirty} marks rooms whose geometry may be stale
 * after a nearby block change; they are re-scanned lazily on next access, inheriting
 * their gas so mass is conserved across splits and merges.
 */
final class Room {
    final RoomState state;
    LongSet cells;
    LongSet leaks;
    boolean sealed;

    /**
     * What this room's enclosure is made of and what it faces - the thermal geometry.
     *
     * <p>Taken from the scan rather than recomputed, because it is the same walk: the
     * scanner already visits every boundary face to decide whether the room is sealed, and
     * asking a second time would be a second chance for the two answers to disagree.
     */
    Shell shell = Shell.NONE;

    /**
     * Heat put into this room since the last thermal step, in <strong>joules</strong>.
     *
     * <p>Joules rather than watts, and that is the whole reason this field exists instead of
     * the thermal step going looking for heat sources itself. A player is ticked on one
     * cadence, a machine on another, and the atmosphere on a third; a contributor that
     * announced its wattage would be counted once per <em>its</em> tick and scaled by
     * whichever cadence happened to read it last. Energy added up is cadence-proof: each
     * source contributes what it actually produced over its own step, and the thermal tick
     * divides the total by its own elapsed time to get back to watts.
     */
    double pendingHeatJ;

    /**
     * What the last thermal step actually had to work with, in watts.
     *
     * <p>Kept because {@link #pendingHeatJ} is zeroed the moment it is used, so anything
     * asking "how much heat is going into this room" between steps would read a half-filled
     * accumulator and report a number that flickered with the tick phase. The instrument
     * needs the figure the balance used, not the one still being collected.
     */
    double lastSupplyWatts;
    boolean dirty;
    /** True when the scan hit the volume cap (space too large to pressurize). */
    boolean overCap;
    /** True when the volume has a path to vacuum: it vents, and cannot hold gas. */
    boolean openToSpace;
    /** Game time of the last geometry scan; drives periodic revalidation. */
    long lastScanTime;

    Room(RoomState state, LongSet cells, LongSet leaks, boolean sealed) {
        this.state = state;
        this.cells = cells;
        this.leaks = leaks;
        this.sealed = sealed;
    }

    long id() {
        return state.id();
    }
}
