package play.xponer.astronima.sim.room;

/** How a block behaves for atmosphere purposes. */
public enum BlockKind {
    /** Gas-passable interior space (air, torches, open hatches): part of a room. */
    OPEN,
    /** Airtight (full solid blocks, hull plates, closed bulkhead doors): a room wall. */
    SEALED,
    /**
     * Blocks passage but not gas (ordinary doors, fences, cracked hull): a slow leak
     * path. The scanner records these so rooms on both sides can exchange gas.
     */
    LEAKY,
    /**
     * Space the room system cannot account for: unloaded chunks, world border, or the
     * exposed asteroid exterior. Reaching one of these means the volume is not sealed.
     */
    UNBOUNDED
}
