package play.xponer.astronima.sim.tox;

import play.xponer.astronima.sim.Co2Status;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.Humidity;
import play.xponer.astronima.sim.RoomState;

import java.util.function.ToDoubleFunction;

/**
 * What is in this air that should not be, and what to do about it.
 *
 * <p>The analyzer has listed partial pressures since the first tier, and a list of eleven
 * numbers is a table rather than an instrument: it is true, it is complete, and it leaves the
 * player to work out which row is the one killing them. This answers the two questions they
 * actually have — <em>is anything in here going to hurt me</em>, and <em>what is the thing to
 * do</em>.
 *
 * <h2>The one judgement it makes</h2>
 * <strong>It must not reach for the biggest hammer every time.</strong> Carbon dioxide and
 * humidity have machines that take them out at the rate they arrive; the remedy there is a
 * scrubber, not dumping the atmosphere. Purging is the answer for the gases with no other
 * answer — and an instrument that told you to vent the module over ordinary CO₂ would be one
 * the player stopped reading long before the day it mattered.
 *
 * <p>Minecraft-free (rule 1): a room's composition in, a sentence out.
 */
public final class ContaminationVerdict {

    /** What the player should reach for. */
    public enum Remedy {
        /** Nothing here needs acting on. */
        NONE,
        /** A machine already handles this one at the rate it arrives. */
        FILTER,
        /**
         * Nothing filters this. Isolate the room, dump the atmosphere, refill from stores.
         *
         * <p>Which is what a real crew does about a toxic release — see
         * {@code design/contamination.md} §1.
         */
        PURGE
    }

    /**
     * @param gas      the species that is the problem, or null when nothing is
     * @param severity how bad it is right now
     * @param remedy   what to do
     * @param advice   the sentence, ready to draw
     */
    public record Reading(Gas gas, GasToxicity.Severity severity, Remedy remedy, String advice) {
        public boolean isClean() {
            return remedy == Remedy.NONE;
        }

        /** True when the room needs emptying rather than filtering. */
        public boolean needsPurge() {
            return remedy == Remedy.PURGE;
        }
    }

    private static final Reading CLEAN =
            new Reading(null, GasToxicity.Severity.NONE, Remedy.NONE, "");

    /**
     * The species a machine in this mod can already take back out.
     *
     * <p>Kept as an explicit question rather than a set literal so that adding a machine and
     * forgetting this list cannot happen quietly: {@code ContaminationVerdictTest} asserts
     * that every gas the mod can remove is answered {@link Remedy#FILTER} here.
     */
    public static boolean isFilterable(Gas gas) {
        return gas == Gas.CARBON_DIOXIDE || gas == Gas.WATER_VAPOR;
    }

    /**
     * Reads the room.
     *
     * <p><strong>The worst species wins, not the first or the largest.</strong> Partial
     * pressure alone would nominate carbon dioxide in almost every room that has ever held a
     * person, while chlorine — dangerous a thousandfold lower — sat unmentioned underneath it.
     * The ranking is by what the gas is doing to you, which is what {@code GasToxicity} is for.
     */
    public static Reading read(RoomState room) {
        return read(gas -> room.partialPressureKPa(gas), Humidity.moldFavourable(room));
    }

    /**
     * The same reading, for a caller that has partial pressures but not a room.
     *
     * <p>Which is the client: the analyzer HUD is sent a table of partial pressures and a
     * humidity, never a {@code RoomState}. Splitting the entry point rather than duplicating
     * the ranking is what stops the panel and the server ever disagreeing about which gas is
     * the problem — one authority, two callers (rule 13).
     *
     * @param partialKPa partial pressure of a species, kPa
     * @param moldRisk   whether the room is damp enough to grow mould
     */
    public static Reading read(ToDoubleFunction<Gas> partialKPa, boolean moldRisk) {
        Gas worst = null;
        GasToxicity.Severity worstSeverity = GasToxicity.Severity.NONE;

        for (Gas gas : Gas.values()) {
            GasToxicity.Severity severity = severityOf(partialKPa, moldRisk, gas);
            if (severity.ordinal() > worstSeverity.ordinal()) {
                worst = gas;
                worstSeverity = severity;
            }
        }
        if (worst == null || worstSeverity == GasToxicity.Severity.NONE) {
            return CLEAN;
        }
        Remedy remedy = isFilterable(worst) ? Remedy.FILTER : Remedy.PURGE;
        return new Reading(worst, worstSeverity, remedy, advice(worst, worstSeverity, remedy));
    }

    /**
     * How bad this species is in this room, whichever model owns it.
     *
     * <p><strong>Two gases do not live in {@link GasToxicity}</strong>, and finding that out
     * is what the first draft of this class got wrong: it asked {@code acute} about everything
     * and therefore reported a room at four kPa of carbon dioxide as perfectly clean. Carbon
     * dioxide's hazard is {@link Co2Status} and humidity's is mould, and those are the right
     * homes for them — one gas, one authority. What was missing is the thing that gathers the
     * answers up for a single readout, which is this method.
     */
    private static GasToxicity.Severity severityOf(ToDoubleFunction<Gas> partialKPa,
                                                   boolean moldRisk, Gas gas) {
        if (gas == Gas.CARBON_DIOXIDE) {
            return switch (Co2Status.classify(partialKPa.applyAsDouble(gas))) {
                case NORMAL -> GasToxicity.Severity.NONE;
                case ELEVATED -> GasToxicity.Severity.IRRITATION;
                case HIGH -> GasToxicity.Severity.DAMAGE;
                case TOXIC -> GasToxicity.Severity.SEVERE;
            };
        }
        if (gas == Gas.WATER_VAPOR) {
            // Water vapour is not poisonous; what it does is grow mould, and that is a
            // property of the room rather than of a partial pressure. Reported at the
            // gentlest level there is, because it is a housekeeping problem and not an
            // emergency - and putting it on the same scale is what lets one line rank it
            // honestly against a gas that is trying to kill you.
            return moldRisk
                    ? GasToxicity.Severity.TRACE_WARNING : GasToxicity.Severity.NONE;
        }
        return GasToxicity.acute(gas, partialKPa.applyAsDouble(gas));
    }

    private static String advice(Gas gas, GasToxicity.Severity severity, Remedy remedy) {
        String what = gas.symbol() + (severity == GasToxicity.Severity.TRACE_WARNING
                ? " in the air" : " at harmful levels");
        if (remedy != Remedy.FILTER) {
            return what + " — nothing filters this. Seal the room and purge it";
        }
        // Name the right machine. "Run a scrubber" at a damp room sends the player to fit a
        // lithium hydroxide cartridge, which will do nothing whatever about the humidity.
        return gas == Gas.WATER_VAPOR
                ? "Damp enough for mould — run a dehumidifier"
                : what + " — run a scrubber";
    }

    private ContaminationVerdict() {}
}
