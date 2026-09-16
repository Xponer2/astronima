package play.xponer.astronima.sim.ore;

/**
 * Turns the numbers into a sentence a person can act on.
 *
 * <p>"Liberation 26%" is precise and completely opaque unless you already know
 * mineral processing. The machines were reported as not understandable, and this is
 * why: the screens showed correct measurements of a process the player had no way to
 * form a mental model of. A gauge tells you <em>what</em>; it takes a sentence to say
 * <em>so what</em>.
 *
 * <p>Nothing here invents information. Every verdict is a band of the same numbers the
 * gauges show, so the words and the instruments can never disagree — the text is a
 * second reading of one truth, not a second truth.
 */
public final class SeparatorAdvice {
    /** What a crusher setting means for the magnet downstream. */
    public enum GrindVerdict {
        /** Barely broken: most grains are still trapped inside composite particles. */
        LOCKED("Most grains still locked in rock"),
        /** Usable: a fair share is free, some is not. */
        PARTIAL("Some grains freed, many still locked"),
        /** Well liberated: most grains stand alone and the magnet can sort them. */
        FREED("Grains mostly free - the magnet can sort them"),
        /** Over-ground: liberated, but now fine enough to travel as dust. */
        DUSTY("Fully freed, but dust will follow the magnet");

        private final String advice;

        GrindVerdict(String advice) {
            this.advice = advice;
        }

        public String advice() {
            return advice;
        }
    }

    /** What a field strength means for what the drum picks up. */
    public enum FieldVerdict {
        /** Too weak to hold even native metal reliably. */
        WEAK("Too weak - even metal slips past"),
        /** The clean cut: strong magnetics only. */
        SELECTIVE("Catching metal and magnetite only - clean"),
        /** Starting to lift part-metal particles: more metal, more waste. */
        GREEDY("Also lifting part-rock grains - more metal, dirtier"),
        /** Indiscriminate: everything with a trace of iron comes along. */
        INDISCRIMINATE("Lifting anything with iron in it - very dirty");

        private final String advice;

        FieldVerdict(String advice) {
            this.advice = advice;
        }

        public String advice() {
            return advice;
        }
    }

    /**
     * What a feed and a room mean for the winnowing table.
     *
     * <p>The table has no control, so its sentence has a different job from the others': it
     * does not describe a setting, it names <em>which of two upstream decisions</em> is
     * currently the limiting one. A player looking for a dial has to be told there is not
     * one and where the decision actually was.
     */
    public enum WinnowVerdict {
        /** No gas: nothing can be lifted, whatever the grind. */
        AIRLESS("No air to lift with - bring it inside"),
        /** Air is fine; the feed is too ragged for density to show through size. */
        RAGGED("Feed too coarse - size swamps density. Grind finer"),
        /** Working, but a finer grind would still sharpen the cut. */
        WORKABLE("Separating - a finer grind would sharpen it"),
        /** As good as this machine gets. */
        SHARP("Well sized - the cut is as sharp as this table gets");

        private final String advice;

        WinnowVerdict(String advice) {
            this.advice = advice;
        }

        public String advice() {
            return advice;
        }
    }

    /**
     * Classifies a winnowing run by whichever of its two inputs is holding it back.
     *
     * <p>Air first, and as a gate: a player whose table is in vacuum must not be told to go
     * and grind finer, because it would not help and they would do it. That is the same
     * ordering rule the pipe survey follows — name the thing that makes everything past it
     * moot, first.
     */
    public static WinnowVerdict winnow(double fineness, double pressureKPa) {
        if (pressureKPa < Elutriation.MINIMUM_PRESSURE_KPA) {
            return WinnowVerdict.AIRLESS;
        }
        if (fineness < 0.3) {
            return WinnowVerdict.RAGGED;
        }
        return fineness >= 0.8 ? WinnowVerdict.SHARP : WinnowVerdict.WORKABLE;
    }

    /**
     * Classifies a grind by what it does to the separator, not by the number itself.
     *
     * <p>The dusty band is the important one: liberation keeps rising all the way to
     * the finest setting, so a player reading only that number would grind to dust
     * every time. The thing that stops being true is the concentrate staying clean.
     */
    public static GrindVerdict grind(double liberation, double fineness) {
        if (fineness > 0.85) {
            return GrindVerdict.DUSTY;
        }
        if (liberation >= 0.6) {
            return GrindVerdict.FREED;
        }
        return liberation >= 0.25 ? GrindVerdict.PARTIAL : GrindVerdict.LOCKED;
    }

    /**
     * Classifies a field strength by which minerals it is capturing.
     *
     * <p>Bands come from the capture criterion itself: where native metal starts being
     * held, and where the middlings — particles that are part metal, part rock — start
     * coming with it.
     */
    public static FieldVerdict field(double fieldStrength) {
        double metal = MagneticSeparation.captureFraction(
                Mineral.KAMACITE.magneticSusceptibility(), fieldStrength);
        double middlings = MagneticSeparation.captureFraction(
                MagneticSeparation.MIDDLINGS_SUSCEPTIBILITY, fieldStrength);

        if (metal < 0.75) {
            return FieldVerdict.WEAK;
        }
        if (middlings < 0.15) {
            return FieldVerdict.SELECTIVE;
        }
        return middlings < 0.35 ? FieldVerdict.GREEDY : FieldVerdict.INDISCRIMINATE;
    }

    private SeparatorAdvice() {}
}
