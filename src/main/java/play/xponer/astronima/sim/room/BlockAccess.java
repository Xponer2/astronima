package play.xponer.astronima.sim.room;

/**
 * The world as the room scanner sees it. The Minecraft layer adapts a {@code Level}
 * to this; tests supply a map. Keeping the interface this small is what lets the
 * entire sealing algorithm run under plain JUnit.
 */
public interface BlockAccess {
    BlockKind kindAt(CellPos pos);

    /**
     * Whether the block here is built to resist heat as well as hold pressure.
     *
     * <p>Deliberately a separate question from {@link BlockKind}, rather than a new kind.
     * Sealing and insulating are independent properties of a wall and the scanner's fill
     * logic must not have to care about the second one: folding them together would mean
     * every future combination (leaky-and-insulated, say) needing its own enum constant.
     *
     * <p>Defaulted to false so the map-backed {@code BlockAccess} the sealing tests use
     * keeps working unchanged - those tests are about enclosure, not about heat.
     */
    default boolean insulatedAt(CellPos pos) {
        return false;
    }

    /**
     * Whether the block here is painted to reflect the sun, 0..1's worth of face at a time.
     *
     * <p>Independent of {@link #insulatedAt(CellPos)} for the same reason that method is
     * independent of {@link BlockKind} — see its own doc. A face can be bare, insulated,
     * painted, or insulated-and-painted, and folding paint into insulation (or into a new
     * {@code BlockKind}) would mean every future combination needing its own case again.
     *
     * <p>Defaulted to false for the same reason {@link #insulatedAt(CellPos)} is: the
     * map-backed {@code BlockAccess} the sealing tests use is about enclosure, not heat.
     */
    default boolean paintedAt(CellPos pos) {
        return false;
    }
}
