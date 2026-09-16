package play.xponer.astronima.sim.pathogen;

/**
 * A thing that stands between a contaminated surface and a person.
 *
 * <p><strong>A barrier is intact or it is not.</strong> There is no "60 % effective glove" here,
 * and the omission is deliberate: a seal either holds or it has a hole in it, and a fractional
 * seal is a number a player could never act on. The judgement in this system lives in the
 * <em>load</em>, which is continuous because it is a quantity of material that really does divide
 * and accumulate. One dial to reason about, with yes/no gates around it.
 *
 * <p>See {@code design/transmission.md} §3.1.
 */
public enum Barrier {
    /** Sealed gloves. Stops what your hands pick up from reaching your skin. */
    GLOVES,
    /** A helmet that holds pressure. Stops room air reaching your face. */
    HELMET,
    /** A filter cartridge with capacity left. Stops what is <em>in</em> that air. */
    FILTER,
    /** The suit's outer layer. Stops a sharp edge carrying anything under the skin. */
    OUTER_LAYER,
    /** Water or food that has been sterilised rather than taken straight off a melt. */
    STERILE_STOCK
}
