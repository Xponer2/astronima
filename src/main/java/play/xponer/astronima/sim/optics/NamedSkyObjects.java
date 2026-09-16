package play.xponer.astronima.sim.optics;

import play.xponer.astronima.sim.magic.ObservationTarget;

import java.util.List;

/**
 * Fixed sky-space placements for {@link ObservationTarget}'s named deep-sky entries
 * (design/sky.md L4): one direction each, in the same unrotated frame the starfield and galactic
 * band are built in, so {@link SkyRotation} carries all three together — the renderer's marker
 * and the spectrograph's targeting read the identical direction and cannot silently disagree.
 */
public final class NamedSkyObjects {

    /** Which of the sky's shaders a placement draws with — the actual mechanism behind "every
     * object is its own kind of thing, not a reskin of another one" (the user's own standing
     * request): an emission nebula, a reflection nebula, a planetary nebula and a whole galaxy
     * are four real, physically distinct classes of object, so each gets its own shader rather
     * than one shared shape with a colour swapped in. */
    public enum Kind { EMISSION, REFLECTION, PLANETARY, GALAXY }

    /**
     * Which telescope lens can even resolve this object, design/astra-research.md §2b: a real
     * optical fact (surface brightness), not a gameplay-convenience number. {@link #BASE_LENS_TIER}
     * is what a freshly-built telescope always has.
     */
    public record Placement(ObservationTarget target, SkyRotation.Vec3 fixedDirection,
                             double angularToleranceDegrees, Kind kind, int requiredLensTier) {}

    /** Every telescope starts able to resolve tier-1 objects — no crafting required for this much. */
    public static final int BASE_LENS_TIER = 1;

    /** Whether a lens of the given tier can resolve {@code placement} at all — the honest gate
     * design/astra-research.md §2b asks for: search/closeness math is unaffected by lens tier
     * (finding the *direction* is real regardless of aperture), only whether a capture on it can
     * ever complete. */
    public static boolean reachableWithLensTier(Placement placement, int lensTier) {
        return lensTier >= placement.requiredLensTier();
    }

    /**
     * Every direction below is invented, not a real RA/Dec — this dimension's own galactic band
     * is already a procedural, not real, orientation (design/sky.md §0), so real sky coordinates
     * would not actually land inside our band either. What is real is each target's own physics;
     * only where each one sits in this fictional sky is chosen, spread apart so they read as
     * separate objects rather than overlapping.
     */
    public static final Placement ORION_NEBULA = new Placement(
            ObservationTarget.ORION_NEBULA,
            new SkyRotation.Vec3(0.35, 0.55, -0.75).normalize(),
            4.0, Kind.EMISSION, BASE_LENS_TIER);

    /** A reflection nebula, not an emission one — {@link Kind#REFLECTION}'s own shader, blue
     * and star-studded rather than red/teal, is the actual point of giving it a distinct kind.
     * Real reflection nebulae only show scattered starlight — dim, diffuse — so this is the tier's
     * first object that needs a lens better than the base one to resolve at all. */
    public static final Placement PLEIADES = new Placement(
            ObservationTarget.PLEIADES,
            new SkyRotation.Vec3(-0.62, 0.28, 0.68).normalize(),
            4.0, Kind.REFLECTION, BASE_LENS_TIER + 1);

    /** A planetary nebula — {@link Kind#PLANETARY}'s own shader draws the real ring shape this
     * object is famous for, not another cloud. Compact and self-luminous, the same reason Orion is
     * base-tier: real, high surface brightness. */
    public static final Placement HELIX_NEBULA = new Placement(
            ObservationTarget.HELIX_NEBULA,
            new SkyRotation.Vec3(0.15, -0.45, 0.88).normalize(),
            4.0, Kind.PLANETARY, BASE_LENS_TIER);

    /** A whole galaxy, not a nebula — {@link Kind#GALAXY}'s own shader draws the real elongated
     * disc shape this object is famous for, the furthest in kind from the other three L4 objects.
     * The faintest, most extended target in the catalogue — real aperture-limited astronomy puts
     * this well beyond a base lens. */
    public static final Placement ANDROMEDA_GALAXY = new Placement(
            ObservationTarget.ANDROMEDA_GALAXY,
            new SkyRotation.Vec3(-0.80, -0.30, -0.52).normalize(),
            4.0, Kind.GALAXY, BASE_LENS_TIER + 1);

    /** Placed near {@link #ANDROMEDA_GALAXY}'s own direction — real M32 sits right beside M31 in
     * the sky, close enough that both fit the same telescope field — but well outside either
     * one's own angular tolerance, so the two read as two separate, individually aimable targets
     * rather than one the reticle cannot tell apart. Reuses {@link Kind#GALAXY}'s own shader: a
     * compact elliptical is still a whole galaxy, not a new physical class this catalogue needs a
     * fifth shader for. */
    public static final Placement M32 = new Placement(
            ObservationTarget.M32,
            new SkyRotation.Vec3(-0.68, -0.09, -0.58).normalize(),
            4.0, Kind.GALAXY, BASE_LENS_TIER + 1);

    public static final List<Placement> ALL =
            List.of(ORION_NEBULA, PLEIADES, HELIX_NEBULA, ANDROMEDA_GALAXY, M32);

    /** Which placement, if any, {@code lookDirection} is currently close enough to count as
     * "aimed at" — null if none. */
    public static Placement lookedAt(SkyRotation.Vec3 lookDirection, long gameTimeTicks) {
        for (Placement placement : ALL) {
            SkyRotation.Vec3 current =
                    SkyRotation.currentDirection(placement.fixedDirection(), gameTimeTicks);
            if (SkyRotation.angleBetweenDegrees(lookDirection, current)
                    <= placement.angularToleranceDegrees()) {
                return placement;
            }
        }
        return null;
    }

    /**
     * The placement whose current direction is closest to {@code lookDirection} right now,
     * regardless of whether it is within tolerance — the "which one am I closest to" a search
     * reticle needs (design/astra-telescope.md §4), distinct from {@link #lookedAt}'s plain
     * yes/no. Never null: {@link #ALL} always holds at least one placement.
     */
    public static Placement nearest(SkyRotation.Vec3 lookDirection, long gameTimeTicks) {
        Placement best = ALL.get(0);
        double bestAngle = Double.MAX_VALUE;
        for (Placement placement : ALL) {
            SkyRotation.Vec3 current =
                    SkyRotation.currentDirection(placement.fixedDirection(), gameTimeTicks);
            double angle = SkyRotation.angleBetweenDegrees(lookDirection, current);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = placement;
            }
        }
        return best;
    }

    private NamedSkyObjects() {}
}
