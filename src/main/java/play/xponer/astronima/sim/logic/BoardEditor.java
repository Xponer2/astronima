package play.xponer.astronima.sim.logic;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * What the plate editor <em>decides</em>, with no screen attached.
 *
 * <h2>Why this is a class and not part of the screen</h2>
 * The editor shipped broken twice, and both times for the same underlying reason: every decision
 * it made lived inside a render-and-dispatch method, where nothing could look at it. The second
 * time, the fault was one line — the field naming the pin a wire was being pulled from was cleared
 * <em>before</em> the legality check read it, so every connection anybody ever attempted was
 * refused with <em>"a plate cannot loop"</em>, including the first one on a blank plate. The
 * message was describing the editor's own state and calling it the circuit's.
 *
 * <p>Nothing in the build could have caught that, because there was nothing to call. Here the
 * whole interaction is a state machine over {@link Circuit}: press a pin, release on a pin, pick a
 * gate up, put it down. The screen is left with geometry and drawing, which is the part that
 * genuinely needs eyes on it (rule 4).
 *
 * <p>Minecraft-free (rule 1).
 */
public final class BoardEditor {

    /**
     * One connection point, named by what it belongs to rather than by where it is drawn.
     *
     * @param drives true for something that puts a value out — a gate's output, or one of the
     *               plate's input pins, which drives the circuit inside
     * @param node   the gate it belongs to, or -1 for one of the plate's own edge pins
     * @param index  which input of that gate, or which edge pin
     */
    public record Pin(boolean drives, int node, int index) {

        /**
         * What this pin would feed, when it is the source end of a wire.
         *
         * <p>For a driving pin on a node, {@code index} is which of that node's outputs this is —
         * always {@code 0} for a gate, {@code 0..pins-1} for a nested plate.
         */
        public Circuit.Source asSource() {
            return node >= 0 ? Circuit.Source.fromNodeOutput(node, index) : Circuit.Source.fromInput(index);
        }
    }

    private Circuit circuit;
    private @Nullable Gate armed;
    private @Nullable Circuit armedSubcircuit;
    /** How many real pins {@link #armedSubcircuit} carries — see {@link Circuit.Node#pins}. */
    private int armedSubcircuitPins;
    /** The chip's own name at the moment it was armed — see {@link Circuit.Node#name}. */
    private String armedSubcircuitName = "";
    private @Nullable Pin pulling;
    private int carryingX = -1;
    private int carryingY = -1;
    private String status = "";
    private boolean changed;

    public BoardEditor(Circuit circuit) {
        // Anything authored before the board existed, or arriving off a save with no positions,
        // is laid out once here rather than every frame — a layout that moved while you looked at
        // it would be worse than none.
        this.circuit = circuit.laidOut();
    }

    public Circuit circuit() {
        return circuit;
    }

    public @Nullable Gate armed() {
        return armed;
    }

    public @Nullable Circuit armedSubcircuit() {
        return armedSubcircuit;
    }

    public @Nullable Pin pulling() {
        return pulling;
    }

    public boolean isCarrying() {
        return carryingX >= 0;
    }

    /** The square the carried gate came from, or null. */
    public int @Nullable [] carriedFrom() {
        return carryingX < 0 ? null : new int[] {carryingX, carryingY};
    }

    /** Whatever the editor last had to say, or empty when it has nothing to complain about. */
    public String status() {
        return status;
    }

    /**
     * Whether the circuit changed since this was last asked, so the screen knows when to tell the
     * server.
     */
    public boolean takeChanged() {
        boolean was = changed;
        changed = false;
        return was;
    }

    // ---- the palette --------------------------------------------------------------

    /** Picking a gate to place, or putting it back by picking it again. */
    public void arm(Gate gate) {
        armed = armed == gate ? null : gate;
        armedSubcircuit = null;
        pulling = null;
        status = "";
    }

    /**
     * Picking a subcircuit template to place, or putting it back by picking it again.
     *
     * @param pins how many real pins the nested chip has — the inserted part's own
     *             {@code type().inputs().size()}, four or five (see {@link Circuit.Node#pins}).
     * @param name the chip's own name, read off the item stack it was armed from — see
     *             {@link Circuit.Node#name}.
     */
    public void armSubcircuit(Circuit subcircuit, int pins, String name) {
        boolean same = Objects.equals(armedSubcircuit, subcircuit) && armedSubcircuitPins == pins;
        armedSubcircuit = same ? null : subcircuit;
        armedSubcircuitPins = same ? 0 : pins;
        armedSubcircuitName = same ? "" : name;
        armed = null;
        pulling = null;
        status = "";
    }

    // ---- pins ---------------------------------------------------------------------

    /**
     * Whether a wire from {@code from} may land on {@code to}.
     *
     * <p><strong>The source is a parameter, not a field</strong>, and that is the whole of the bug
     * this class was extracted for. The screen asks this twice — once to decide whether to
     * highlight a pin as a landing place, and once when the drop happens — so a pin can never be
     * drawn as legal and then refuse.
     */
    public boolean canLand(@Nullable Pin from, Pin to) {
        if (from == null || to.drives() || !from.drives()) {
            return false;
        }
        return to.node() < 0 || circuit.canLink(to.node(), from.asSource());
    }

    /** Whether that pin would accept the wire currently being pulled — what the screen lights. */
    public boolean isLegalTarget(Pin to) {
        return canLand(pulling, to);
    }

    /** What is wired into a listening pin, or null. */
    public Circuit.@Nullable Source feeding(Pin pin) {
        if (pin.drives()) {
            return null;
        }
        Circuit.Source source = pin.node() >= 0
                ? circuit.nodes().get(pin.node()).input(pin.index())
                : circuit.outputs().get(pin.index());
        return source.isOff() ? null : source;
    }

    /**
     * Pressing a pin.
     *
     * <p>A driving pin starts a wire — or replaces the one already being pulled, because changing
     * your mind about which output to take from is commoner than cancelling. A listening pin
     * either <em>completes</em> a wire or, when nothing is being pulled and it is already wired,
     * disconnects itself. That last one has nowhere else to live: a wire inside a plate is drawn
     * rather than stored, so there is nothing to click on to remove it.
     */
    public void pressPin(Pin pin) {
        status = "";
        if (pin.drives()) {
            pulling = pin;
            return;
        }
        if (pulling != null) {
            land(pin);
            return;
        }
        if (feeding(pin) != null) {
            apply(unwire(pin));
        }
    }

    /**
     * Releasing over a pin.
     *
     * <p>Released somewhere else, the wire stays in hand — so <strong>click-then-click works
     * exactly as well as press-and-drag</strong>, which matters because a drag across a screen is
     * a different gesture from a drag across a wall and a player will try both.
     */
    public void releaseOnPin(@Nullable Pin pin) {
        if (pulling == null || pin == null || pin.equals(pulling) || pin.drives()) {
            return;
        }
        land(pin);
    }

    private void land(Pin to) {
        Pin from = pulling;
        pulling = null;
        if (!canLand(from, to)) {
            status = "a plate cannot loop - that gate already reads this one";
            return;
        }
        apply(to.node() >= 0
                ? circuit.linked(to.node(), to.index(), from.asSource())
                : circuit.withOutput(to.index(), from.asSource()));
    }

    private Circuit unwire(Pin pin) {
        return pin.node() >= 0
                ? circuit.linked(pin.node(), pin.index(), Circuit.Source.off())
                : circuit.withOutput(pin.index(), Circuit.Source.off());
    }

    // ---- the board ----------------------------------------------------------------

    /**
     * Pressing a board cell: pick up whatever is there, or drop the armed gate on it.
     *
     * @return true when the press was used
     */
    public boolean pressCell(int x, int y) {
        return pressCell(x, y, false);
    }

    /**
     * Pressing a board cell: pick up whatever is there, drop the armed part on it, or — with
     * {@code replace} — swap the armed part in for whatever is already there, in place.
     *
     * @param replace whether this press asks for a swap rather than a pick-up, when the cell is
     *                occupied and something is armed — the screen's shift-click, kept as a
     *                parameter rather than read from the keyboard here so this class stays
     *                Minecraft-free and this decision stays testable. A plain click always drags,
     *                unchanged: {@code armed} stays set after every drop (so a player can place
     *                several of the same part in a row), which means it is almost never null —
     *                if replace fired on a bare click, picking up an existing part to move it
     *                would swap it instead the moment anything was armed at all.
     * @return true when the press was used
     */
    public boolean pressCell(int x, int y, boolean replace) {
        status = "";
        if (!onBoard(x, y)) {
            return false;
        }
        int gate = circuit.nodeAt(x, y);
        if (gate >= 0) {
            if (replace && (armed != null || armedSubcircuit != null)) {
                return replaceAt(gate);
            }
            // Held by its *square*, not by its index. An edit can renumber the gates
            // (`Circuit.linked` sorts when a link points backwards), and an index kept across a
            // click would then name a different gate — rule 19's shape exactly.
            carryingX = x;
            carryingY = y;
            return true;
        }
        return drop(x, y);
    }

    /**
     * Swapping whatever is armed in for the part already on that square, in its place — same
     * index, same position, so every wire already landed on it or leaving it stays exactly where
     * it was pointed. Only the part's own identity changes.
     *
     * <p>Inputs the old part had and the new one does not are dropped, the same way a removed
     * gate leaves its readers dangling rather than silently re-pointed (see {@link
     * Circuit#removed}); pins the new part has and the old one lacked arrive unconnected, same as
     * on a freshly placed one. When the two are pin-for-pin the same shape — which is the case a
     * same-family swap always is — nothing is dropped at all, and the whole board stays wired.
     */
    private boolean replaceAt(int index) {
        Circuit.Node old = circuit.nodes().get(index);
        Circuit.Node next = armedSubcircuit != null
                ? new Circuit.Node(armedSubcircuit, old.inputs(), old.x(), old.y(),
                        armedSubcircuitPins, armedSubcircuitName)
                : new Circuit.Node(armed, old.a(), old.b(), old.x(), old.y());
        if (next.equals(old)) {
            return true;
        }
        apply(circuit.withNode(index, next));
        status = "replaced - any wire with nowhere left to land is unconnected";
        return true;
    }

    private boolean drop(int x, int y) {
        if (armed == null && armedSubcircuit == null) {
            return false;
        }
        if (circuit.nodes().size() >= Circuit.MAX_NODES) {
            status = "this plate holds " + Circuit.MAX_NODES + " gates and it is full";
            return true;
        }
        if (armedSubcircuit != null) {
            apply(circuit.addedSubcircuit(armedSubcircuit, x, y, armedSubcircuitPins, armedSubcircuitName));
        } else {
            apply(circuit.added(armed, x, y));
        }
        return true;
    }

    /** Releasing over a board cell, having picked a gate up. */
    public void releaseOnCell(int x, int y) {
        if (carryingX < 0) {
            return;
        }
        int gate = circuit.nodeAt(carryingX, carryingY);
        carryingX = -1;
        carryingY = -1;
        if (gate < 0 || !onBoard(x, y)) {
            return; // dropped off the board: it stays where it was, which is the safe answer
        }
        if (circuit.occupied(x, y)) {
            status = "there is already a gate there";
            return;
        }
        apply(circuit.moved(gate, x, y));
    }

    /** Right-clicking a cell: whatever is there comes out. */
    public void removeAt(int x, int y) {
        pulling = null;
        armed = null;
        carryingX = -1;
        carryingY = -1;
        status = "";
        int gate = circuit.nodeAt(x, y);
        if (gate < 0) {
            return;
        }
        apply(circuit.removed(gate));
        status = "removed - anything that read it is unconnected now";
    }

    private static boolean onBoard(int x, int y) {
        return x >= 0 && y >= 0 && x < Circuit.BOARD_COLUMNS && y < Circuit.BOARD_ROWS;
    }

    /**
     * Replaces the whole board with a circuit pasted in from elsewhere — never a merge, the
     * same way loading a save replaces what was open rather than adding to it. {@code laidOut}
     * (the constructor's own call) keeps any position the pasted circuit already carries and
     * only fills in what arrives unplaced, so a board copied from another plate keeps reading
     * exactly as it did there.
     */
    public void paste(Circuit pasted) {
        armed = null;
        armedSubcircuit = null;
        pulling = null;
        carryingX = -1;
        carryingY = -1;
        Circuit laid = pasted.laidOut();
        if (laid.equals(circuit)) {
            status = "pasted - identical to what was already here";
            return;
        }
        circuit = laid;
        changed = true;
        status = "pasted from the clipboard";
    }

    /**
     * The clipboard held something, but not a real, intact circuit — the board stays exactly as
     * it was. Refusing loudly here is the entire point of {@code CircuitClipboard}'s own
     * checksum: a garbled paste reading back as a quietly blank board would look exactly like
     * the player's own work had been erased.
     */
    public void pasteFailed() {
        status = "clipboard does not hold a real circuit";
    }

    /**
     * Takes an edit, and notices when it was refused.
     *
     * <p>{@code Circuit} returns itself unchanged when it will not do something, so "did anything
     * happen" is one comparison — and a refusal cannot leave the editor half-edited, because there
     * was never a partial state to leave behind.
     */
    private void apply(Circuit next) {
        if (next.equals(circuit)) {
            return;
        }
        circuit = next;
        changed = true;
    }
}
