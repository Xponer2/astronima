package play.xponer.astronima.sim.lab;

import java.util.ArrayList;
import java.util.List;

/**
 * What is on a Petri dish, and everything anybody has found out about it.
 *
 * <h2>The dish is the notebook</h2>
 * This record is why the laboratory is a chain of machines rather than one box with a progress bar:
 * <strong>the knowledge travels with the plate.</strong> A dish streaked in one machine, grown in
 * the next and stained in a third arrives at the disc reader already knowing it is a Gram-positive
 * coccus in clusters — because each machine wrote what it found onto the thing it handed on.
 *
 * <p>Nothing here is a flag saying "identified". The dish carries <em>observations</em>, and the
 * identification is whatever {@link IdentificationKey} makes of them. A plate on which somebody
 * botched the stain carries the botched result, faithfully, and takes it to the next bench.
 *
 * <p>Minecraft-free (rule 1).
 *
 * @param organism    what is actually on it — the answer the player does not have
 * @param grownHours  how long it has been incubated for, and at what it grew
 * @param streak      how well it was streaked, 0..1; a poor streak gives confluent growth
 * @param gram        what the stain said, which is not always what the organism is
 * @param shape       what the microscope showed, or null
 * @param arrangement what the microscope showed, or null
 * @param catalase    the peroxide result, or null before it was run
 * @param oxidase     the reagent result, or null before it was run
 */
public record Culture(String organism, double grownHours, double streak,
                      GramStain.Result gram, Organism.Shape shape,
                      Organism.Arrangement arrangement, Boolean catalase, Boolean oxidase) {


    /** How long at a good temperature before there are colonies to work with. */
    public static final double READY_HOURS = 18.0;

    /** Below this nothing grows; above it the plate is cooked. Both in kelvin. */
    public static final double COLD_K = 293.15;
    public static final double IDEAL_K = 310.15;
    public static final double LETHAL_K = 320.15;

    /** A streak worse than this runs the colonies together and none can be picked. */
    public static final double CONFLUENT_BELOW = 0.35;

    /** A fresh plate: something on it, nothing known, nothing grown. */
    public static Culture freshly(String organism, double streak) {
        return new Culture(organism, 0, streak, null, null, null, null, null);
    }

    /** Nothing on it at all. */
    public static final Culture BLANK = new Culture("", 0, 0, null, null, null, null, null);

    public boolean isBlank() {
        return organism.isBlank();
    }

    /** True once there are separate colonies a loop can pick from. */
    public boolean hasColonies() {
        return !isBlank() && grownHours >= READY_HOURS && streak >= CONFLUENT_BELOW;
    }

    /**
     * True when it grew, but into a lawn nobody can pick a single colony out of.
     *
     * <p>Its own state rather than "not ready", because the fix is different: a plate that needs
     * more time needs waiting, and a confluent one needs streaking again from the beginning.
     */
    public boolean isConfluent() {
        return !isBlank() && grownHours >= READY_HOURS && streak < CONFLUENT_BELOW;
    }

    /**
     * A spell in the incubator.
     *
     * <p><strong>Too hot kills it, and the plate then looks exactly like one that was sterile.</strong>
     * That ambiguity is real and it is the reason a laboratory owns a thermometer rather than a
     * dial with "hot" written on it.
     */
    public Culture incubated(double hours, double kelvin) {
        if (isBlank() || hours <= 0) {
            return this;
        }
        if (kelvin >= LETHAL_K) {
            return BLANK;                       // dead, and indistinguishable from empty
        }
        if (kelvin < COLD_K) {
            return this;                        // nothing happens at all
        }
        // Fastest at blood heat and slower either side of it, which is what a growth curve does.
        double from = Math.abs(kelvin - IDEAL_K) / (LETHAL_K - IDEAL_K);
        double rate = Math.clamp(1 - from * from, 0.05, 1.0);
        return new Culture(organism, grownHours + hours * rate, streak,
                gram, shape, arrangement, catalase, oxidase);
    }

    public Culture stained(GramStain.Result result) {
        return new Culture(organism, grownHours, streak, result, shape, arrangement,
                catalase, oxidase);
    }

    public Culture seen(Organism.Shape underMicroscope, Organism.Arrangement howTheySit) {
        return new Culture(organism, grownHours, streak, gram, underMicroscope, howTheySit,
                catalase, oxidase);
    }

    public Culture tested(boolean isCatalase, boolean positive) {
        return new Culture(organism, grownHours, streak, gram, shape, arrangement,
                isCatalase ? positive : catalase, isCatalase ? oxidase : positive);
    }

    /** The key, built from what this plate has been through. */
    public IdentificationKey key() {
        IdentificationKey key = new IdentificationKey();
        if (gram != null) {
            key.stained(gram);
        }
        if (shape != null && arrangement != null) {
            key.seen(shape, arrangement);
        }
        if (catalase != null) {
            key.catalase(catalase);
        }
        if (oxidase != null) {
            key.oxidase(oxidase);
        }
        return key;
    }

    /** What is actually growing, or null for a blank plate. */
    public Organism growing() {
        return Isolates.all().stream()
                .filter(known -> known.name().equalsIgnoreCase(organism))
                .findFirst().orElse(null);
    }

    /** The label a player reads off the side of the dish. */
    public List<String> label() {
        List<String> lines = new ArrayList<>();
        if (isBlank()) {
            lines.add("sterile - nothing growing");
            return lines;
        }
        if (isConfluent()) {
            lines.add("confluent growth - streak it again");
        } else if (!hasColonies()) {
            lines.add(String.format(java.util.Locale.ROOT, "incubating - %.0f of %.0f h",
                    grownHours, READY_HOURS));
        } else {
            lines.add("colonies, countable");
        }
        if (gram != null) {
            lines.add("gram: " + gram.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (shape != null && arrangement != null) {
            lines.add(shape.name().toLowerCase(java.util.Locale.ROOT) + ", "
                    + arrangement.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (catalase != null) {
            lines.add("catalase " + (catalase ? "+" : "-"));
        }
        if (oxidase != null) {
            lines.add("oxidase " + (oxidase ? "+" : "-"));
        }
        return List.copyOf(lines);
    }
}
