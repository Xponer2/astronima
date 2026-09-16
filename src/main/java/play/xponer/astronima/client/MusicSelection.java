package play.xponer.astronima.client;

import java.util.List;
import java.util.Map;

/**
 * Which ambient track plays, decided from the situation alone — Minecraft-free so the decision
 * is unit-testable (rule 25); {@code AstronimaMusicSelector} is the thin client glue that reads
 * real game state and a real {@code SoundEvent} onto whatever this picks.
 *
 * <p>Eight tracks, one job each — named for it, per rule 8, one mechanic per kind of work:
 *
 * <ul>
 * <li>Three menu tracks, because a player who leaves the game on the title screen for an hour
 *     should not hear one loop forever.
 * <li>Day and night ambience under open sky — the mod's existing day/night distinction, given a
 *     sound.
 * <li>A general "gameplay" track that keeps day from feeling like the only calm mood.
 * <li>{@link Track#COSMIC_VOID} for being genuinely underground with no sky above — the
 *     asteroid's own interior, not a dressed-up biome.
 * <li>{@link Track#CRYSTAL_CAVE} for standing near the site design/astra-incognita.md §7 and §8.8
 *     already name as this branch's actual deep content: the water-ice lenses. Nothing lives
 *     there yet (A1 has not reached that far) — the music is the first honest sign that the spot
 *     means something, before any mechanic does.
 * </ul>
 */
public final class MusicSelection {

    public enum Track {
        DEEP_SPACE_DRIFT, QUIET_SPACE_VOID, VAST_SILENCE,
        COSMIC_SURVIVAL, NIGHT_SPACE_AMBIENT, QUIET_COSMIC_PADS,
        COSMIC_VOID, CRYSTAL_CAVE
    }

    public enum Bucket { MENU, MAGIC_VEIN, DANGEROUS_DEPTH, NIGHT, DAY }

    private static final Map<Bucket, List<Track>> POOLS = Map.of(
            Bucket.MENU, List.of(Track.DEEP_SPACE_DRIFT, Track.QUIET_SPACE_VOID, Track.VAST_SILENCE),
            Bucket.MAGIC_VEIN, List.of(Track.CRYSTAL_CAVE),
            Bucket.DANGEROUS_DEPTH, List.of(Track.COSMIC_VOID),
            Bucket.DAY, List.of(Track.COSMIC_SURVIVAL, Track.QUIET_COSMIC_PADS),
            Bucket.NIGHT, List.of(Track.NIGHT_SPACE_AMBIENT, Track.QUIET_COSMIC_PADS));

    /** What the situation looks like, independent of how it was read off the real game. */
    public record Situation(boolean inMenu, boolean hasOpenSky, boolean isDaytime,
                            boolean nearMagicVein, boolean deepUnderground) {
    }

    /**
     * The bucket for a situation. Order matters and is deliberate: a magic vein is the most
     * specific fact about a place and wins over "it happens to be a cave", which in turn wins
     * over the ordinary day/night split.
     */
    public static Bucket bucketFor(Situation situation) {
        if (situation.inMenu()) {
            return Bucket.MENU;
        }
        if (situation.nearMagicVein()) {
            return Bucket.MAGIC_VEIN;
        }
        if (!situation.hasOpenSky() && situation.deepUnderground()) {
            return Bucket.DANGEROUS_DEPTH;
        }
        return situation.isDaytime() ? Bucket.DAY : Bucket.NIGHT;
    }

    /** Every track available for a bucket, in a fixed order — what {@code pick} draws from. */
    public static List<Track> tracksFor(Bucket bucket) {
        return POOLS.get(bucket);
    }

    /**
     * One track from a bucket's pool, given an index. The caller (not this class) is
     * responsible for keeping that index stable across repeated calls in one session — picking
     * fresh on every call would fight {@code Music}'s own minDelay/maxDelay spacing, restarting
     * selection before a track ever finished (see {@code AstronimaMusicSelector}).
     */
    public static Track pick(Bucket bucket, int index) {
        List<Track> pool = tracksFor(bucket);
        return pool.get(Math.floorMod(index, pool.size()));
    }

    private MusicSelection() {}
}
