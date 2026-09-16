package play.xponer.astronima.sim.lab;

/**
 * A drug, and the two numbers a disc-diffusion plate is read against.
 *
 * <h2>Breakpoints are not opinions</h2>
 * A zone of 21 mm and a zone of 19 mm sit on opposite sides of a published line, and that line is
 * set by what the drug can actually reach in a body. It is why the test gives an answer rather than
 * an impression.
 *
 * <p>Three bands, not two, because <strong>intermediate is a real result and a real decision</strong>:
 * it means the drug will work where it concentrates, or at a higher dose, and choosing to use it
 * anyway is a judgement the player gets to make.
 *
 * @param discContentUg how much drug is on the paper disc — the standard loading
 * @param resistantBelowMm at or under this, call it resistant
 * @param susceptibleAtMm at or over this, call it susceptible; between the two is intermediate
 */
public record Antibiotic(String name, double discContentUg,
                         double resistantBelowMm, double susceptibleAtMm) {

    /** What a plate says about one drug. */
    public enum Call { RESISTANT, INTERMEDIATE, SUSCEPTIBLE }

    /** Broad-spectrum, and the one a player will reach for first because it usually works. */
    public static final Antibiotic BROAD = new Antibiotic("broad-spectrum", 30, 14, 21);

    /** Narrow, and far better when it is right — the reward for having identified the organism. */
    public static final Antibiotic NARROW = new Antibiotic("narrow-spectrum", 10, 13, 20);

    /** Held back on purpose: it works on nearly everything, so using it teaches nothing. */
    public static final Antibiotic LAST_RESORT = new Antibiotic("last-resort", 5, 10, 16);

    public static java.util.List<Antibiotic> all() {
        return java.util.List.of(BROAD, NARROW, LAST_RESORT);
    }

    /** Which band a measured zone falls in. */
    public Call read(double zoneMm) {
        if (zoneMm >= susceptibleAtMm) {
            return Call.SUSCEPTIBLE;
        }
        return zoneMm <= resistantBelowMm ? Call.RESISTANT : Call.INTERMEDIATE;
    }
}
