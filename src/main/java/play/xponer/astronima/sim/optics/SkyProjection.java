package play.xponer.astronima.sim.optics;

/**
 * Equirectangular projection of a fixed sky-space direction onto a flat chart (design/astra-
 * research.md §6.1, design/astra-research-m2a.md). Static — this is a fixed direction's own
 * position, not {@link SkyRotation#currentDirection}'s rotated one; drift is M3 (§6.2).
 *
 * <p>Chosen over a two-circle planisphere (§14 open question 1) because it is the simplest
 * correct mapping and every consumer downstream reads {@code (u, v)}, not this method — a later
 * switch to a planisphere changes this file's body only.
 */
public final class SkyProjection {

    /** Normalised chart position, both axes in {@code [0, 1]}. */
    public record Point(double u, double v) {}

    /**
     * @param direction need not be normalised — this method normalises it, so a caller cannot
     *                  accidentally get a different answer for the same direction at a different
     *                  length.
     */
    public static Point project(SkyRotation.Vec3 direction) {
        SkyRotation.Vec3 d = direction.normalize();
        double longitude = Math.atan2(d.z(), d.x()); // (-PI, PI]
        double latitude = Math.asin(Math.clamp(d.y(), -1.0, 1.0)); // [-PI/2, PI/2]
        double u = (longitude + Math.PI) / (2.0 * Math.PI);
        // y growing downward on screen: straight up (latitude = +PI/2) must land at v = 0.
        double v = 1.0 - (latitude + Math.PI / 2.0) / Math.PI;
        return new Point(u, v);
    }

    private SkyProjection() {}
}
