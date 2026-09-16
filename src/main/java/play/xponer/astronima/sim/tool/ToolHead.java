package play.xponer.astronima.sim.tool;

/**
 * The state a tool carries between uses: how sharp it is, how hard it was forged, and
 * how much of it is left to grind away.
 *
 * <p>Immutable, so the wear rules stay expressions rather than mutations and can be
 * reasoned about in a test. The MC layer packs this into a data component.
 *
 * @param edge          0..1 edge condition; 1 is freshly ground
 * @param hardness      0..1 forged hardness, from {@code sim/metal/ColdWorking}
 * @param cracked       true when the head was worked past its ductility limit
 * @param resharpenings grinds left before the head is a stub
 */
public record ToolHead(double edge, double hardness, boolean cracked, int resharpenings) {

    /**
     * Grinds a head allows before there is not enough metal left to grind.
     *
     * <p>Three lives rather than one. Enough that looking after a tool means something,
     * few enough that it is still a resource.
     */
    public static final int MAX_RESHARPENINGS = 3;

    /**
     * Hardness of a green compact — grains pressed together and nothing more.
     *
     * <p>Fixed and low, because nothing about pressing loose powder admits of skill.
     * A green compact holds together by cold welding at the grain contacts and by
     * mechanical interlocking, with none of the continuous work-hardened structure a
     * forged billet has, and its strength is correspondingly a fraction of it.
     */
    public static final double GREEN_COMPACT_HARDNESS = 0.20;

    /** A green compact has one grind in it, not three: there is little body to lose. */
    public static final int GREEN_COMPACT_RESHARPENINGS = 1;

    /**
     * A head pressed straight from grains, with no forging.
     *
     * <p>The rung of the ladder you can reach within minutes of your first magnet pull.
     * Deliberately not a badly forged head but a differently made one — the way to
     * improve on it is to go and forge properly, not to press more carefully.
     */
    public static ToolHead pressed() {
        return new ToolHead(ToolWear.SHARP, GREEN_COMPACT_HARDNESS, false,
                GREEN_COMPACT_RESHARPENINGS);
    }

    /** A head straight off the forge. */
    public static ToolHead forged(double hardness, boolean cracked) {
        return new ToolHead(ToolWear.SHARP, Math.clamp(hardness, 0, 1), cracked,
                MAX_RESHARPENINGS);
    }

    public ToolHead {
        edge = Math.clamp(edge, 0.0, ToolWear.SHARP);
        hardness = Math.clamp(hardness, 0.0, 1.0);
        resharpenings = Math.max(0, resharpenings);
    }

    /**
     * One block's worth of work.
     *
     * <p>A cracked head chips instead of wearing when the edge crosses a band boundary:
     * brittle metal does not blunt gradually, it holds and then loses a piece. Modelled
     * as an extra loss on the transition rather than a random roll, so the same tool
     * always behaves the same way and the player can learn it.
     */
    public ToolHead mine(double load) {
        double worn = ToolWear.mine(edge, hardness, load);
        if (cracked && ToolWear.classify(worn) != ToolWear.classify(edge)) {
            worn = ToolWear.chip(worn);
        }
        return new ToolHead(worn, hardness, cracked, resharpenings);
    }

    /** True while grinding the edge back is still possible. */
    public boolean canResharpen() {
        return resharpenings > 0 && edge < ToolWear.SHARP;
    }

    /**
     * Grinds the edge back, spending one of the head's remaining lives.
     *
     * <p>A head with nothing left to grind is returned unchanged rather than refused:
     * the tool keeps working at whatever edge it has, which is the whole point.
     */
    public ToolHead resharpen() {
        if (!canResharpen()) {
            return this;
        }
        return new ToolHead(ToolWear.resharpen(), hardness, cracked, resharpenings - 1);
    }

    /** Mining speed this head currently gives, as a fraction of a sharp one. */
    public double speedMultiplier() {
        return ToolWear.speedMultiplier(edge);
    }

    public ToolWear.Edge condition() {
        return ToolWear.classify(edge);
    }

    /**
     * Total blocks this head can still cut, counting the grinds it has left.
     *
     * <p>The number that makes forging quality legible: a well-worked head is not
     * slightly better, it is several times longer-lived.
     */
    public double remainingLifeInBlocks() {
        double perLife = ToolWear.lifeInBlocks(hardness);
        double thisLife = Math.max(0, edge - ToolWear.SPENT_BELOW)
                / ToolWear.wearPerBlock(hardness, 1.0);
        return thisLife + perLife * resharpenings;
    }
}
