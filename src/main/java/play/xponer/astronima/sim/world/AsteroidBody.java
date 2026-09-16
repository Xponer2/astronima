package play.xponer.astronima.sim.world;

/**
 * The asteroid's shape: a bounded oblate spheroid with a logical centre, not an infinite plane.
 *
 * <p>Minecraft-free (rule 1) — every column-fill loop, block placement and noise sample stays in
 * {@code worldgen/AsteroidChunkGenerator}; this class is the geometry underneath it, which is why
 * it can be tested without a world. See {@code design/asteroid-body.md} for why the body is
 * shaped this way and {@code design/asteroid-body-b1.md} for the two bugs a naive port of the
 * old flat-world generator would have shipped — both are exactly what {@link #shellCoordinate}
 * and the rim clamp in {@link #halfHeight} exist to prevent.
 */
public final class AsteroidBody {

    /**
     * The body's centre and its two radii. Not independently chosen: {@code CENTRE_Y +
     * POLAR_RADIUS = 96} and {@code CENTRE_Y - POLAR_RADIUS = -64} are exactly the old flat
     * generator's {@code SURFACE_BASE_Y} and {@code getMinY()} — those two constants already
     * described this spheroid's poles without a rim.
     */
    public static final int CENTRE_Y = 16;
    public static final int POLAR_RADIUS = 80;
    /** The one number in the shape that wants a playtest, not an argument — design doc §6.1. */
    public static final int EQUATORIAL_RADIUS = 240;

    private AsteroidBody() {}

    /**
     * Half the body's vertical extent at horizontal distance {@code r} from the spin axis — the
     * spheroid equation solved for height.
     *
     * <p><strong>Zero at and past the rim, not merely small.</strong> An unclamped formula would
     * still place terrain at {@code CENTRE_Y} everywhere outside the body, because the top and
     * bottom pole would coincide instead of vanishing — the same "the loop must simply not run"
     * requirement the old flat generator got for free from {@code minY}, made explicit here
     * because a curved body does not get it for free.
     */
    public static double halfHeight(double r) {
        double ratio = r / EQUATORIAL_RADIUS;
        return ratio >= 1.0 ? 0.0 : POLAR_RADIUS * Math.sqrt(1.0 - ratio * ratio);
    }

    /** True while {@code r} is still inside the body — false at and past the rim. */
    public static boolean isInside(double r) {
        return halfHeight(r) > 0.0;
    }

    /** The bare floor at horizontal distance {@code r}. No relief: see {@link #surfaceHeight}. */
    public static int floorHeight(double r) {
        return CENTRE_Y - (int) Math.round(halfHeight(r));
    }

    /**
     * The sky-facing surface at horizontal distance {@code r}, given a relief sample already
     * drawn from whatever noise field the caller uses.
     *
     * <p>{@code reliefAmplitude} is scaled by {@code half / POLAR_RADIUS} before being applied,
     * which is not cosmetic: unscaled relief where the body is only a few blocks thick would
     * punch holes clean through the rim rather than converging to a clean edge
     * (asteroid-body-b1.md §2.2).
     */
    public static int surfaceHeight(double r, double reliefSample, double reliefAmplitude) {
        double half = halfHeight(r);
        double reliefScale = half / POLAR_RADIUS;
        return CENTRE_Y + (int) Math.round(half + reliefSample * reliefAmplitude * reliefScale);
    }

    /**
     * Normalised ellipsoidal radius: {@code 0} at the centre, {@code 1} at the skin, in every
     * direction. The single number every resource band is gated on, replacing what an
     * absolute-Y depth check would have to be — a check that inverts at the rim, where a thin
     * column satisfies every "shallower than N" test at once and would generate as the richest
     * ground on the asteroid (asteroid-body-b1.md §1.2, the leaf's central finding).
     */
    public static double shellCoordinate(double r, double y) {
        double a = r / EQUATORIAL_RADIUS;
        double b = (y - CENTRE_Y) / (double) POLAR_RADIUS;
        return Math.sqrt(a * a + b * b);
    }

    /**
     * The whole body's volume, in blocks — an oblate spheroid of the two radii above.
     *
     * <p>Exact, not sampled: {@code (4/3)π·EQUATORIAL_RADIUS²·POLAR_RADIUS}.
     */
    public static double totalVolume() {
        return (4.0 / 3.0) * Math.PI * EQUATORIAL_RADIUS * EQUATORIAL_RADIUS * POLAR_RADIUS;
    }

    /**
     * How many blocks lie in the shell between {@code shellCoordinate} values {@code tLow} and
     * {@code tHigh} — the volume every ore band's expected total is computed from
     * ({@code design/asteroid-body-resources.md}).
     *
     * <p><strong>Exact, and it is exact for a real geometric reason, not an approximation
     * dressed up as one.</strong> {@code {t < T}} is itself an oblate spheroid — every point at
     * shell coordinate {@code T} sits on the surface of the body scaled down by exactly {@code
     * T} in all three axes, because {@code shellCoordinate} is linear in both {@code r} and
     * {@code y - CENTRE_Y}. A uniform linear scaling of a solid by factor {@code T} scales its
     * volume by {@code T³}, so {@code volumeWithin(T) = T³ · totalVolume()} follows directly from
     * the shape, not from sampling it.
     */
    public static double bandVolume(double tLow, double tHigh) {
        double clampedLow = Math.max(0.0, Math.min(1.0, tLow));
        double clampedHigh = Math.max(0.0, Math.min(1.0, tHigh));
        return (Math.pow(clampedHigh, 3) - Math.pow(clampedLow, 3)) * totalVolume();
    }
}
