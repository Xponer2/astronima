package play.xponer.astronima.client.hud;

import java.util.Map;

/**
 * What each floating readout is worth, and what each place is worth to it.
 *
 * <h2>Declared here rather than inside the readouts</h2>
 * They were private constants in two client classes, which meant nothing could load them — so a
 * test that asked <em>"do both readouts still fit on a busy screen?"</em> had to invent its own
 * stand-in panels and was quietly answering a question about those instead. It passed while the
 * real ones had two places between them.
 *
 * <p>Minecraft-free (rule 25), so the numbers a player actually gets are the numbers under test.
 */
public final class HudCosts {

    /**
     * The block readout: what the machine in front of you is doing.
     *
     * <p>Outranks the wire panel because a machine that is not running is the more urgent
     * question, and it wants the crosshair badly — a machine readout in a corner is one nobody
     * looks at while they are stood in front of the machine.
     */
    public static final int BLOCK_PRIORITY = 20;

    public static final Map<HudPlace, Integer> BLOCK = Map.of(
            HudPlace.UNDER_CROSSHAIR, 0,
            HudPlace.ABOVE_CROSSHAIR, 4,
            HudPlace.ABOVE_HOTBAR, 6,
            HudPlace.RIGHT_OF_CROSSHAIR, 9,
            HudPlace.LEFT_OF_CROSSHAIR, 10,
            HudPlace.MID_RIGHT, 14,
            HudPlace.LOWER_RIGHT, 16);

    /**
     * The goggles: what the wire under the crosshair is carrying.
     *
     * <p>Its second choice is nearly as cheap as its first, which is what lets it slide aside
     * rather than disappear when the block readout wants the same spot.
     */
    public static final int GOGGLES_PRIORITY = 10;

    public static final Map<HudPlace, Integer> GOGGLES = Map.of(
            HudPlace.UNDER_CROSSHAIR, 0,
            HudPlace.RIGHT_OF_CROSSHAIR, 2,
            HudPlace.LEFT_OF_CROSSHAIR, 3,
            HudPlace.ABOVE_HOTBAR, 5,
            HudPlace.MID_RIGHT, 7,
            HudPlace.LOWER_RIGHT, 8,
            HudPlace.TOP_RIGHT, 9);

    private HudCosts() {}
}
