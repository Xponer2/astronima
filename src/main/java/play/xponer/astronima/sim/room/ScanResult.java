package play.xponer.astronima.sim.room;

import java.util.Set;

/**
 * Outcome of one room scan.
 *
 * @param cells       every open cell belonging to the room (empty if the seed wasn't open)
 * @param leaks       boundary cells of {@link BlockKind#LEAKY} blocks touching the room
 * @param openToSpace true when the fill reached {@link BlockKind#UNBOUNDED} — the volume
 *                    has a path to vacuum and will vent
 * @param overCap     true when the scan hit the volume cap: too large to pressurize,
 *                    but still an enclosed space that holds whatever gas is in it
 * @param shell       what the boundary is made of and what it faces, in faces - the
 *                    geometry the thermal balance runs on
 */
public record ScanResult(Set<CellPos> cells, Set<CellPos> leaks, boolean openToSpace,
                         boolean overCap, Shell shell) {
    public static ScanResult noRoom() {
        return new ScanResult(Set.of(), Set.of(), false, false, Shell.NONE);
    }

    /**
     * A room can be pressurized only if it exists and is neither open to space nor
     * oversized.
     *
     * <p>Note on the cap: a capped fill only reports what it saw, so a space larger
     * than the cap is reported enclosed even if some far corner reaches vacuum. That
     * is the intended trade — holding gas in an unmeasurably large cavern is a far
     * better failure than deleting the gas a player just breached into a tunnel.
     */
    public boolean sealed() {
        return !cells.isEmpty() && !openToSpace && !overCap;
    }

    public int volumeBlocks() {
        return cells.size();
    }
}
