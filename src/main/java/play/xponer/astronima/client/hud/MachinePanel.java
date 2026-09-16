package play.xponer.astronima.client.hud;

/**
 * Where everything on a machine screen is, in one Minecraft-free place.
 *
 * <h2>Why it moved</h2>
 * These numbers lived in {@code ProcessingMenu}, which extends a Minecraft class — so nothing could
 * load them in a unit test, and the only checkable part of a machine screen was the picture in the
 * middle of it. Reported as <em>"buttons overlap the inventory"</em>, and there was no way to find
 * out whether they did without opening the game and looking.
 *
 * <p>Here, a test can ask. {@link #plan(MachineKind)} reserves every rectangle a machine screen
 * puts on the panel — slots, control, readouts, the inventory label and the player's own grid —
 * and {@link Layout} refuses the overlap. The menu reads these constants rather than owning them,
 * so there is still exactly one source (rule 27).
 */
public final class MachinePanel {

    public static final int WIDTH = 176;

    /** The two or three item slots along the top. */
    public static final int SLOT = 18;
    public static final int SLOT_IN_X = 26;
    public static final int SLOT_OUT_X = 116;
    public static final int SLOT_ROW_Y = 26;

    /** Where the control sits, below the slot row. */
    public static final int DIAL_Y = 62;

    /** How much room the control itself takes, including the knob's overhang. */
    public static final int DIAL_HEIGHT = 14;

    /**
     * First readout row.
     *
     * <p>Used to sit below a strip of chrome that every machine kept its dial on. The dial now
     * lives inside each machine's own picture, on the thing it drives, so the picture starts where
     * that strip was and the panel is fourteen pixels shorter for it.
     */
    public static final int READOUT_Y = DIAL_Y;

    /**
     * Inventory label position, derived from the tallest machine's content rather than chosen.
     *
     * <p>Hand-picking this number is what put the separator's last gauge row through the label,
     * twice. It is still derived — what is new is that something checks the derivation.
     */
    public static final int INVENTORY_LABEL_Y = PanelLayout.tallestMachineContent(READOUT_Y) + 6;

    public static final int INVENTORY_Y = INVENTORY_LABEL_Y + 12;
    public static final int HOTBAR_Y = INVENTORY_Y + 58;
    public static final int HEIGHT = HOTBAR_Y + 22;

    /**
     * The same four numbers, for one machine rather than for the tallest of them.
     *
     * <p>One height for all seven meant the winnowing table's panel was mostly empty grey, with
     * the player's inventory two hundred pixels below the last thing worth reading. A panel should
     * be as tall as what is on it.
     */
    public static int inventoryLabelY(MachineKind kind) {
        return PanelLayout.contentBottom(kind, READOUT_Y) + 6;
    }

    public static int inventoryY(MachineKind kind) {
        return inventoryLabelY(kind) + 12;
    }

    public static int hotbarY(MachineKind kind) {
        return inventoryY(kind) + 58;
    }

    public static int height(MachineKind kind) {
        return hotbarY(kind) + 22;
    }

    /** How far above/below the slot row the two halves of a stacked pair sit — matches
     * {@code ProcessingUi.addMachineSlots}'s own {@code row - 10} / {@code row + 18} exactly. */
    private static final int STACK_UP = 10;
    private static final int STACK_DOWN = 18;

    /**
     * Every rectangle one machine's panel puts down, at that machine's own height —
     * including the <em>real</em> slot geometry {@code ProcessingUi.addMachineSlots} actually
     * draws for each kind, not a placeholder shape.
     *
     * <p>Three real shapes, not one guessed at for every kind (found by cross-referencing this
     * class against {@code ProcessingUi.java} directly rather than trusting the box the previous
     * version reserved): a one-slot machine has no product slot at all; a three-slot machine
     * stacks two product slots vertically at {@code SLOT_OUT_X}, not side by side; the one
     * two-feeds-two-products machine ({@code IRON_SMELTER}) stacks <em>both</em> columns. Reusing
     * exactly the vertical offsets {@code ProcessingUi} uses (rather than inventing a second pair
     * of numbers) is what makes this the geometry actually on screen, not a second, independently
     * maintained guess at it.
     */
    public static Layout plan(MachineKind kind) {
        Layout layout = new Layout("machine panel " + kind, WIDTH, height(kind));
        if (kind.isOneSlot()) {
            layout.reserve("feed slot", SLOT_IN_X, SLOT_ROW_Y, SLOT, SLOT);
        } else if (kind.isTwoFeedsTwoProducts()) {
            layout.reserve("first feed slot", SLOT_IN_X, SLOT_ROW_Y - STACK_UP, SLOT, SLOT);
            layout.reserve("second feed slot", SLOT_IN_X, SLOT_ROW_Y + STACK_DOWN, SLOT, SLOT);
            layout.reserve("first product slot", SLOT_OUT_X, SLOT_ROW_Y - STACK_UP, SLOT, SLOT);
            layout.reserve("second product slot", SLOT_OUT_X, SLOT_ROW_Y + STACK_DOWN, SLOT, SLOT);
        } else if (kind.isThreeSlot()) {
            layout.reserve("feed slot", SLOT_IN_X, SLOT_ROW_Y, SLOT, SLOT);
            layout.reserve("first product slot", SLOT_OUT_X, SLOT_ROW_Y - STACK_UP, SLOT, SLOT);
            layout.reserve("second product slot", SLOT_OUT_X, SLOT_ROW_Y + STACK_DOWN, SLOT, SLOT);
        } else {
            layout.reserve("feed slot", SLOT_IN_X, SLOT_ROW_Y, SLOT, SLOT);
            layout.reserve("product slot", SLOT_OUT_X, SLOT_ROW_Y, SLOT, SLOT);
        }
        layout.reserve("readouts", MARGIN, READOUT_Y, WIDTH - MARGIN * 2,
                inventoryLabelY(kind) - READOUT_Y - 2);
        layout.reserve("inventory label", MARGIN, inventoryLabelY(kind), WIDTH - MARGIN * 2, 10);
        layout.reserve("inventory", MARGIN, inventoryY(kind), SLOT * 9, SLOT * 3);
        layout.reserve("hotbar", MARGIN, hotbarY(kind), SLOT * 9, SLOT);
        return layout;
    }

    /** Left margin the panel's own furniture uses. */
    public static final int MARGIN = 8;

    private MachinePanel() {}
}
