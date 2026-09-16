package play.xponer.astronima.sim.machine;

/**
 * Why a machine is, or is not, getting on with its batch.
 *
 * <p>Exists because of a real report: *"if you take the item out the progress is kept but
 * stops"*. That is the intended behaviour — half-crushed rock does not un-crush itself, and
 * a machine you have to babysit is a chore — but nothing said so, and a bar that holds still
 * at 40 % is indistinguishable from a machine that has broken. Keeping progress is a kindness
 * only if the player can tell it apart from a fault (rule 9).
 *
 * <p>Deliberately not a boolean. "Running / not running" is precisely the distinction that
 * was already visible and already useless; what the operator needs is <em>which</em> of the
 * three stalls this is, because each has a different remedy and two of them are their own
 * fault.
 */
public enum WorkState {
    /** Fed, room for the product, and someone is turning the handle. */
    CRANKING("Cranking", "Keep turning — this is the fast rate"),

    /** Fed and free to run, but nobody is at the handle, so it creeps. */
    CREEPING("Running slowly", "Hold right-click on the machine to crank it"),

    /** Nothing to work on. Whatever was done stays done. */
    STARVED("No feed", "Put something in the feed slot"),

    /** Fed, but the finished batch would have nowhere to go. */
    BLOCKED("Product full", "Take the product out, or pipe it away"),

    /**
     * The workpiece itself is ruined — fed, free to run, and never going to finish.
     *
     * <p>Only a machine that can destroy its own input reports this; the cold forge does,
     * because a billet worked past its ductility cracks. Without it the generic rule calls
     * a cracked billet "no feed" and sends the player for more metal.
     */
    SPOILED("Workpiece ruined", "This piece is cracked — it cannot be worked any further"),

    /**
     * A solar machine with no sun on it: buried, roofed over, or on the night side.
     *
     * <p>Its own stall rather than a flavour of "not running" because the remedy is a
     * <em>placement</em>, not anything you can do at the machine — and a player who thinks
     * they have fed it wrong will keep feeding it.
     */
    UNLIT("No sunlight", "The mirror needs a clear view of the sky, in daylight"),

    /** Lit and fed, but the focus is too loose for the reaction to start at all. */
    TOO_COLD("Below reaction temperature", "Tighten the focus until the bar reaches the window"),

    /**
     * A sealed vessel with nowhere to send its product gas.
     *
     * <p>Not a contrivance: a retort venting into vacuum loses everything it makes, and one
     * venting into nothing at all simply stops against its own back-pressure. Making it a
     * stall rather than a silent loss turns "where do I put this" into a question the
     * machine asks out loud.
     */
    BACKPRESSURE("Steam has nowhere to go", "Open it into a sealed room, or the water is lost"),

    /**
     * A gas-driven machine standing in air too thin to carry anything.
     *
     * <p>Its own stall because the remedy is neither the feed nor the output but
     * <em>where the machine is</em>. A winnowing table works by drag, drag scales with gas
     * density, and on the surface there is none — so a player who thinks they have fed it
     * wrong will keep feeding it.
     */
    UNPRESSURISED("Needs air to winnow", "Bring it inside: a gas stream needs gas"),

    /**
     * A fluidized bed with no hydrogen in the room to reduce its charge.
     *
     * <p>Held rather than run, because the hydrogen is <em>consumed</em>: without it the
     * reduction cannot proceed at all, and spinning the drum anyway would only blow feed out the
     * vent for nothing. Its own stall because the remedy is a supply, not the feed or the dial.
     */
    NO_REAGENT("No hydrogen", "Pipe hydrogen into the room — the bed reduces with it, not without"),

    /**
     * A fluidized bed spun so fast the fixed flow can no longer lift the bed: it packs to the
     * drum wall, the gas channels through it, and reduction stalls.
     *
     * <p>Held, and the feed is kept — a packed bed loses nothing, it simply does nothing. The
     * remedy is to <em>slow</em> the drum until the bed boils again, which is why this reads as
     * its own stall rather than "no feed": the slots are fine, the spin is wrong.
     */
    PACKED("Bed packed to the wall", "Slow the drum — spun this fast the flow cannot fluidize it"),

    /**
     * A fluidized bed spun so slow the fixed flow exceeds the terminal velocity and carries the
     * bed up and out the exhaust — feed and freshly-made iron alike, unrecovered.
     *
     * <p><strong>The bar still advances</strong> — the reactor's {@code canRun} steps a blowing-out
     * bed on purpose, because while it advances the bed is <em>leaving</em>, and that loss is the
     * expensive mistake the machine can make. But it reads as a <em>warning</em>, not a healthy
     * run ({@link #isWorking()} is false), so the gauge does not colour it green while the charge
     * pours out the vent. Spin faster to pin the bed.
     */
    BLOWING_OUT("Blowing out the exhaust", "Spin faster — the bed is being carried out the vent"),

    /**
     * The polymerizer, catalyst loaded, with no ethylene in the room to string into polyethylene.
     *
     * <p>Its own value rather than reusing {@link #NO_REAGENT} because that one's own label is
     * hydrogen-specific text baked in for the fluidized bed — reusing it here would tell a player
     * standing at a polymerizer to go find hydrogen, which is not what it needs.
     */
    NO_ETHYLENE("No ethylene", "Pipe ethylene into the room — the polymerizer needs it to run"),

    /**
     * The Sabatier reactor, catalyst loaded, with too little carbon dioxide or hydrogen (or
     * both) in the room to react.
     *
     * <p>Its own value because this machine needs two room reagents at once — {@link #NO_REAGENT}
     * and {@link #NO_ETHYLENE} each name one gas by name in their own remedy text, and a reactor
     * missing either one (or both) needs a remedy that does not pretend to know which.
     */
    NO_FEEDSTOCK_GAS("Not enough CO2 or H2",
            "Needs carbon dioxide and hydrogen in the room together — check both"),

    /**
     * The troilite roaster, fed, with too little oxygen in the room to roast the charge.
     *
     * <p>Its own value because the remedy is a supply, like {@link #NO_REAGENT}, but naming
     * oxygen specifically — reusing hydrogen's own hard-coded text here would send a player
     * roasting ore to go pipe in the wrong gas.
     */
    NO_OXYGEN("No oxygen", "Needs oxygen in the room — roasting a sulfide genuinely consumes it"),

    /**
     * The sulfuric acid plant, catalyst loaded, with too little SO2, oxygen or water vapor
     * (or several) in the room to react.
     *
     * <p>Its own value because this machine needs three room reagents at once — the most any
     * machine in this mod checks — and {@link #NO_FEEDSTOCK_GAS}'s own remedy text is hard-coded
     * to name CO2 and H2, which would send a player standing at the acid plant to go check the
     * wrong two gases.
     */
    NO_ACID_FEEDSTOCK("Not enough SO2, O2 or H2O vapor",
            "Needs sulfur dioxide, oxygen and water vapor in the room together — check all three"),

    /**
     * The freeze dryer, fed, without both of its own two preconditions: an adjacent dewar
     * holding LN2, and real exterior vacuum.
     *
     * <p>One combined value rather than two, the same shape {@link #NO_ACID_FEEDSTOCK} already
     * uses for several missing things at once — the remedy names both, and a player fixing
     * either one and rechecking is a normal loop, not a hidden second stall behind the first.
     */
    NOT_READY_TO_DRY("Needs vacuum and LN2",
            "Move it outside a sealed room, next to a dewar holding liquid nitrogen");

    private final String label;
    private final String remedy;

    WorkState(String label, String remedy) {
        this.label = label;
        this.remedy = remedy;
    }

    /**
     * What the machine is doing, from the three facts that decide it.
     *
     * @param hasFeed      the feed slot holds something this machine can process
     * @param productFits  the finished batch would fit in the product slots
     * @param beingCranked a player has turned the handle recently
     */
    public static WorkState of(boolean hasFeed, boolean productFits, boolean beingCranked) {
        // Starvation is reported ahead of a full product slot on purpose: with nothing in
        // the feed there is no batch to be blocked, and "product full" on an empty machine
        // sends the player to tidy an output that is not the problem.
        if (!hasFeed) {
            return STARVED;
        }
        if (!productFits) {
            return BLOCKED;
        }
        return beingCranked ? CRANKING : CREEPING;
    }

    /**
     * True while the batch is advancing <em>well</em> — the states that read green on the panel.
     *
     * <p>Deliberately excludes {@link #BLOWING_OUT}, which is a special case: the batch is still
     * stepping (the reactor's {@code canRun} advances it regardless), but it is stepping
     * <em>badly</em>, losing the bed out the exhaust. So it must read as a warning, not as a
     * healthy run — this method is what the panel and the gauge colour off, and a blowing-out bed
     * coloured green would be a lie. That the bar keeps moving is the reactor's own doing, not
     * this flag's.
     */
    public boolean isWorking() {
        return this == CRANKING || this == CREEPING;
    }

    /**
     * True when the machine is stopped through no fault of its own and is holding what it
     * has done. The panel says so, because a held bar and a broken bar look identical.
     */
    public boolean isHolding() {
        return !isWorking();
    }

    /** Short name for the panel, e.g. "No feed". */
    public String label() {
        return label;
    }

    /** What to actually do about it — the same grammar the biomonitor uses for ailments. */
    public String remedy() {
        return remedy;
    }
}
