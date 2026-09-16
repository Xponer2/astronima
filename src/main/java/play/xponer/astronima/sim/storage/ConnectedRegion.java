package play.xponer.astronima.sim.storage;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * How big a connected group of matching things actually is — a plain breadth-first flood fill,
 * generic over whatever "position" and "neighbours of a position" mean to the caller. Used by
 * {@code StorageDriveBlockEntity} to count a player-built structure's own real size
 * (design/data-cells.md §9), the same shape {@code Atmosphere}'s own room scan already uses —
 * generalised to an arbitrary connected shape rather than a fixed cube or a straight run.
 *
 * <p>Minecraft-free (rule 1): takes plain functions rather than a {@code Level}/{@code BlockPos},
 * so the real counting algorithm itself stays testable against a fake neighbour lookup instead of
 * a real world.
 */
public final class ConnectedRegion {

    /** Real, generous safety bound — a structure this large is already absurd to build, and the
     *  same "bounded, not infinite" discipline {@code WireReadout}'s own trace-following keeps. */
    public static final int SEARCH_LIMIT = 10_000;

    /**
     * @param start        where the search begins (not itself counted, only its neighbours are)
     * @param neighboursOf every position adjacent to a given one, real or fake
     * @param matches      whether a given position is part of the region being counted
     * @return how many distinct positions, connected to {@code start} through positions that all
     *         themselves match, actually match — capped at {@link #SEARCH_LIMIT}
     */
    public static <T> int size(T start, Function<T, List<T>> neighboursOf, Predicate<T> matches) {
        Set<T> seen = new HashSet<>();
        ArrayDeque<T> frontier = new ArrayDeque<>();
        for (T neighbour : neighboursOf.apply(start)) {
            if (matches.test(neighbour)) {
                seen.add(neighbour);
                frontier.add(neighbour);
            }
        }
        while (!frontier.isEmpty() && seen.size() < SEARCH_LIMIT) {
            T current = frontier.poll();
            for (T neighbour : neighboursOf.apply(current)) {
                if (!seen.contains(neighbour) && matches.test(neighbour)) {
                    seen.add(neighbour);
                    frontier.add(neighbour);
                }
            }
        }
        return seen.size();
    }

    private ConnectedRegion() {}
}
