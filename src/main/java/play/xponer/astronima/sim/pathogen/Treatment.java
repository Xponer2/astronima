package play.xponer.astronima.sim.pathogen;

import java.util.Set;

/**
 * A countermeasure, and what it is actually effective against.
 *
 * <h2>Why it names its targets rather than a strength</h2>
 * A treatment that simply "helps a bit" turns every illness into the same problem with a different
 * amount of waiting. Naming what it works on is what makes the assay bench a machine worth
 * building: you can <em>know</em> before you put it in your arm, and guessing has a price.
 *
 * @param strength      how far it moves the fight while it is being taken
 * @param courseSeconds how long a full course lasts; stopping before this breeds resistance
 * @param against       the sources whose organisms it answers
 */
public record Treatment(String name, double strength, double courseSeconds,
                        Set<Strain.Source> against) {

    public Treatment {
        against = Set.copyOf(against);
    }

    /** Whether this is the right thing for that organism. */
    public boolean worksOn(Strain strain) {
        return against.contains(strain.source());
    }
}
