package play.xponer.astronima.sim.pathogen;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * What is on the surfaces of one region, keyed by whatever a caller uses to name a place.
 *
 * <p>Minecraft-free (rule 1), and it was not at first — the first version keyed on {@code BlockPos}
 * and therefore could not be unit-tested at all. None of the behaviour worth guarding has anything
 * to do with block positions: it is <em>sparse storage that forgets</em>, and forgetting correctly
 * is the whole job.
 *
 * <p><strong>Sparse on purpose.</strong> A habitat is thousands of blocks and a player brushes
 * against them constantly. Keeping an entry for every position anybody ever touched would grow the
 * save forever with information nobody can act on, so an entry disappears the moment it weathers
 * below {@link Contamination#NEGLIGIBLE} — and writing a zero counts as removing it, because a
 * sparse map becomes a dense one one zero at a time.
 */
public final class SurfaceLoads {

    /** Share of a dirty glove that comes off onto a surface it works on. */
    public static final double LEFT_BEHIND = 0.25;

    private final Map<Long, Contamination> loads = new HashMap<>();

    public SurfaceLoads() {
    }

    public SurfaceLoads(Map<Long, Contamination> initial) {
        initial.forEach(this::set);
    }

    /** What is on a place, or clean if nobody has ever dirtied it. An absence is an answer. */
    public Contamination at(long place) {
        Contamination here = loads.get(place);
        return here == null ? Contamination.clean(Strain.Source.COMMENSAL) : here;
    }

    public void set(long place, Contamination load) {
        if (load.isClean()) {
            loads.remove(place);
        } else {
            loads.put(place, load);
        }
    }

    public boolean isEmpty() {
        return loads.isEmpty();
    }

    public int size() {
        return loads.size();
    }

    public Set<Long> dirtyPlaces() {
        return Set.copyOf(loads.keySet());
    }

    public Map<Long, Contamination> all() {
        return Map.copyOf(loads);
    }

    /**
     * Weathers everything here.
     *
     * @param halfLife {@link Contamination#VACUUM_HALF_LIFE} outside,
     *                 {@link Contamination#HABITAT_HALF_LIFE} in a pressurised room
     * @return whether anything moved, so a caller can skip syncing a region that did not change
     */
    public boolean decay(double seconds, double halfLife) {
        boolean moved = false;
        var iterator = loads.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Contamination after = entry.getValue().decay(seconds, halfLife);
            if (after.isClean()) {
                iterator.remove();
                moved = true;
            } else if (after.load() != entry.getValue().load()) {
                entry.setValue(after);
                moved = true;
            }
        }
        return moved;
    }

    /**
     * Working on a surface with a pair of hands: both directions, in one call.
     *
     * <p>Touching a dirty bench dirties your gloves, which is the obvious half. The half people
     * forget is that <strong>it goes the other way too</strong> — work at a clean bench with a
     * dirty glove and the bench is the problem now, and it will still be the problem tomorrow when
     * you come back without gloves. Splitting this into "touch" and "soil" would let a caller do
     * half of a thing that has no halves.
     *
     * @param glovesIntact whether there is anything between the surface and the skin
     */
    public Exposure.Handled work(long place, Contamination glove, Contamination skin,
                                 boolean glovesIntact) {
        Exposure.Handled picked = Exposure.handle(at(place), glove, skin, glovesIntact);
        if (!glovesIntact) {
            // Bare hands do not wipe a quarter of themselves back onto every surface they touch.
            // A glove is smooth and impermeable and genuinely carries material around; skin holds
            // what lands on it. Making both shed alike gave a bench and a hand that traded the
            // same load back and forth forever and settled below an infectious dose - so working
            // bare-handed at a filthy bench was *safe*, which is the opposite of true.
            set(place, picked.surface());
            return picked;
        }
        Contamination.Handover wiped = picked.glove().transferTo(picked.surface(), LEFT_BEHIND);
        set(place, wiped.to());
        return new Exposure.Handled(wiped.to(), wiped.from(), picked.skin());
    }
}
