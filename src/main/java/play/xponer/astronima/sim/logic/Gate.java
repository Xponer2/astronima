package play.xponer.astronima.sim.logic;

/**
 * The truth tables, on their own, where they can be checked.
 *
 * <p>Small enough to look trivial and worth separating anyway: a gate whose table is wrong is a
 * bug with <em>no symptom at the gate</em> — the player sees a circuit that misbehaves three
 * components downstream and has no way to tell which part lied. Every row of every table is
 * asserted, because there are only sixteen of them and no excuse.
 *
 * <p><strong>Why these six.</strong> AND, OR and NOT are functionally complete on their own; XOR
 * is here because it is the one a player reaches for constantly and building it from the other
 * three takes four components and a lesson nobody asked for. NAND and NOR are here because they
 * are what real logic is actually built out of — a single gate type that can make any other — and
 * a player who notices that has learned something true.
 *
 * <p>Minecraft-free (rule 1).
 */
public enum Gate {
    AND("AND", "both inputs live"),
    OR("OR", "either input live"),
    XOR("XOR", "exactly one input live"),
    NAND("NAND", "not both — the universal gate"),
    NOR("NOR", "neither input live"),
    NOT("NOT", "inverts its input");

    private final String label;
    private final String description;

    Gate(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /**
     * The output for a pair of inputs.
     *
     * <p>{@link #NOT} ignores its second input rather than refusing it: a one-input gate wired to
     * two lines is a player mistake that should behave predictably, and inverting the first is
     * the only reading that is not arbitrary.
     */
    public boolean apply(boolean a, boolean b) {
        return switch (this) {
            case AND -> a && b;
            case OR -> a || b;
            case XOR -> a ^ b;
            case NAND -> !(a && b);
            case NOR -> !(a || b);
            case NOT -> !a;
        };
    }

    /** True when the second input is meaningless for this gate. */
    public boolean isSingleInput() {
        return this == NOT;
    }
}
