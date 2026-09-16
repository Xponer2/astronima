package play.xponer.astronima.sim.circuit;

import java.util.Locale;

/**
 * How thick the conductor inside the jacket is — <strong>the second axis of the ladder</strong>.
 *
 * <p>{@code design/electrical.md} §3.2 names it and then the build did not have it: <em>"the sheath
 * is what you see, the conductor inside it is a stated gauge… cross-section is a property of the
 * wire type, and it is the second axis of the ladder: thicker wire costs more metal and loses
 * less."</em> Every trace in the game was 4 mm² because one line of code said so.
 *
 * <p>It is worth having because it is a <strong>decision with a computable right answer</strong>,
 * which is this tier's whole philosophy. Resistance goes as 1/A and so does the loss; the current a
 * wire may carry goes up with its surface, which goes as √A; and the metal it costs goes straight
 * up with A. So doubling the copper does not double anything else, and the player can work out
 * which way that trade falls for the run they are about to lay — before they lay it.
 *
 * <h2>Four rungs, each with a real job</h2>
 * <table>
 *   <tr><td><strong>Signal</strong> 0.5 mm²</td><td>control wiring. Carries a contact's worth of
 *       current and nothing else — put a machine on one and it is the first thing in this tier that
 *       can be overloaded</td></tr>
 *   <tr><td><strong>Standard</strong> 4 mm²</td><td>the machine bus, and what every number in the
 *       design document is quoted against</td></tr>
 *   <tr><td><strong>Heavy</strong> 16 mm²</td><td>a trunk feeding several machines, or a long haul
 *       where the loss would otherwise eat the delivery</td></tr>
 *   <tr><td><strong>Busbar</strong> 50 mm²</td><td>the main tie between generation and storage.
 *       Absurd for anything else, and priced like it</td></tr>
 * </table>
 *
 * <p>Minecraft-free (rule 1).
 */
public enum WireGauge {

    SIGNAL("signal", Conductor.SIGNAL_MM2),
    STANDARD("standard", Conductor.STANDARD_MM2),
    HEAVY("heavy", Conductor.HEAVY_MM2),
    BUSBAR("busbar", Conductor.BUSBAR_MM2);

    /** What the rest of the tier is costed against, and what an unstated gauge means. */
    public static final WireGauge DEFAULT = STANDARD;

    /**
     * How many pixels of {@link #STANDARD} one item of stock draws into.
     *
     * <p>Sixty-four — four metres — which is the figure the coil has always spent metal at.
     */
    public static final int STANDARD_PIXELS_PER_ITEM = 64;

    private final String id;
    private final double squareMillimetres;

    WireGauge(String id, double squareMillimetres) {
        this.id = id;
        this.squareMillimetres = squareMillimetres;
    }

    public String id() {
        return id;
    }

    public double squareMillimetres() {
        return squareMillimetres;
    }

    /** The area in SI, which is what {@link Conductor} wants. */
    public double crossSectionM2() {
        return Conductor.mm2(squareMillimetres);
    }

    /** A metre of this gauge in a given metal — the unit everything else is computed from. */
    public Conductor metre(ConductorMaterial material) {
        return new Conductor(material, crossSectionM2(), 1.0);
    }

    public Conductor over(ConductorMaterial material, double metres) {
        return new Conductor(material, crossSectionM2(), metres);
    }

    /**
     * How far one item of stock goes at this gauge, in pixels.
     *
     * <p><strong>Inversely with area, because it is the same metal either way.</strong> An item is
     * a fixed lump; drawing it thicker makes it shorter. That is what turns "thicker wire costs
     * more metal" from a sentence in a design document into something the player pays at the
     * moment they choose — and it is the honest half of the trade, since the other half is that
     * the thick run loses far less.
     *
     * <p>At least one pixel per item however absurd the gauge, so a busbar is expensive rather
     * than impossible.
     */
    public int pixelsPerItem() {
        return Math.max(1, (int) Math.round(
                STANDARD_PIXELS_PER_ITEM * STANDARD.squareMillimetres / squareMillimetres));
    }

    /** Whichever of two gauges is thinner — a run is limited by its narrowest part. */
    public WireGauge thinnerOf(WireGauge other) {
        return squareMillimetres <= other.squareMillimetres ? this : other;
    }

    public static WireGauge byId(String id) {
        for (WireGauge gauge : values()) {
            if (gauge.id.equalsIgnoreCase(id)) {
                return gauge;
            }
        }
        return DEFAULT;
    }

    @Override
    public String toString() {
        return id.toLowerCase(Locale.ROOT);
    }
}
